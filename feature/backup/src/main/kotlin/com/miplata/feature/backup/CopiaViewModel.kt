package com.miplata.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.backup.ArchivoDeBackup
import com.miplata.core.backup.ContenidoDelBackup
import com.miplata.core.backup.FraseDeRespaldo
import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.common.DespachadorBloqueante
import com.miplata.core.domain.repository.AjustesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.io.IOException
import javax.inject.Inject

/**
 * Exporta una copia de seguridad cifrada al documento que elija el usuario.
 *
 * El selector del sistema lo abre la pantalla -es quien tiene la Activity- y
 * aqui solo llega la direccion del documento elegido. Desde ahi todo pasa fuera
 * del hilo principal: reunir los datos, cifrarlos -un par de segundos de CPU en
 * un movil- y escribirlos.
 *
 * Sobre el `@Suppress`: son siete dependencias y detekt avisa, con razon, para
 * una funcion normal. Aqui es un constructor que rellena Hilt, y cada una es una
 * pieza distinta del proceso; agruparlas solo moveria la lista de sitio.
 */
@Suppress("LongParameterList")
@HiltViewModel
class CopiaViewModel
    @Inject
    constructor(
        private val recolector: RecolectorDeDatos,
        private val archivo: ArchivoDeBackup,
        private val abridor: AbridorDeDestino,
        private val informacion: InformacionDeLaApp,
        private val ajustes: AjustesRepository,
        private val reloj: Clock,
        @param:DespachadorBloqueante private val trabajo: CoroutineDispatcher,
    ) : ViewModel() {
        private val exportacion = MutableStateFlow<Exportacion>(Exportacion.Inactiva)

        val uiState: StateFlow<CopiaUiState> =
            combine(ajustes.observar(), exportacion) { configuracion, enCurso ->
                CopiaUiState(
                    ultimaCopiaEnMillis = configuracion.ultimoBackupEnMillis,
                    exportacion = enCurso,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(CINCO_SEGUNDOS),
                initialValue = CopiaUiState(),
            )

        /** El nombre que se propone en el selector. */
        fun nombreSugerido(): String = nombreDelArchivo(reloj.now(), TimeZone.currentSystemDefault())

        /**
         * Escribe la copia en [uri], cifrada con [frase].
         *
         * [frase] se **borra** en cuanto se ha copiado dentro de la
         * [FraseDeRespaldo], y esa a su vez se olvida al terminar, salga bien o
         * mal. La pantalla no tiene que acordarse de limpiar nada.
         *
         * @param nombre el que aparecera en el mensaje de exito. El selector no
         *   devuelve el nombre definitivo, asi que se usa el sugerido.
         */
        fun exportar(
            uri: String,
            frase: CharArray,
            nombre: String,
        ) {
            if (exportacion.value == Exportacion.EnCurso) {
                frase.fill(BORRADO)
                return
            }
            exportacion.value = Exportacion.EnCurso

            val clave =
                try {
                    FraseDeRespaldo(frase)
                } catch (e: IllegalArgumentException) {
                    exportacion.value = Exportacion.Fallida(e.message.orEmpty())
                    return
                } finally {
                    frase.fill(BORRADO)
                }

            viewModelScope.launch {
                exportacion.value =
                    try {
                        escribir(uri, clave)
                        apuntarLaCopia()
                        Exportacion.Terminada(nombre)
                    } catch (e: IOException) {
                        // Lo normal: el proveedor (Drive, OneDrive, una tarjeta)
                        // no deja escribir, o se quedo sin espacio.
                        Exportacion.Fallida(e.message ?: ERROR_AL_ESCRIBIR)
                    } catch (e: SecurityException) {
                        // El permiso sobre el documento se perdio por el camino.
                        Exportacion.Fallida(e.message ?: ERROR_AL_ESCRIBIR)
                    } finally {
                        clave.olvidar()
                    }
            }
        }

        /** Vuelve al estado de reposo tras enseñar un exito o un error. */
        fun descartarAviso() {
            if (exportacion.value != Exportacion.EnCurso) exportacion.value = Exportacion.Inactiva
        }

        private suspend fun escribir(
            uri: String,
            clave: FraseDeRespaldo,
        ) = withContext(trabajo) {
            val contenido =
                ContenidoDelBackup(
                    datos = recolector.recolectar(),
                    versionDelEsquema = informacion.versionDelEsquema,
                    versionDeLaApp = informacion.versionDeLaApp,
                    creadoEnMillis = reloj.now().toEpochMilliseconds(),
                    dispositivo = informacion.dispositivo,
                )
            abridor.abrir(uri).use { destino -> archivo.escribir(contenido, clave, destino) }
        }

        /**
         * Anota cuando se hizo la copia, **solo si salio bien**.
         *
         * Es lo que usara el recordatorio (4.5) para avisar de que hace mucho
         * que no se hace una. Apuntarla antes de escribir haria que un fallo
         * dejara al usuario creyendo que tiene una copia reciente que no existe.
         */
        private suspend fun apuntarLaCopia() {
            val actuales = ajustes.obtener()
            ajustes.guardar(actuales.copy(ultimoBackupEnMillis = reloj.now().toEpochMilliseconds()))
        }

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
            const val BORRADO = '\u0000'
            const val ERROR_AL_ESCRIBIR = "No se pudo escribir el archivo"
        }
    }
