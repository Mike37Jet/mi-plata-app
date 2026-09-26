package com.miplata.feature.backup

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.backup.ArchivoDeBackup
import com.miplata.core.backup.BackupInvalido
import com.miplata.core.backup.FraseDeRespaldo
import com.miplata.core.backup.FraseIncorrecta
import com.miplata.core.backup.Procedencia
import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.backup.Recuento
import com.miplata.core.backup.RestauracionIncompleta
import com.miplata.core.backup.RestauradorDeCopias
import com.miplata.core.backup.VERSION_DEL_FORMATO
import com.miplata.core.common.DespachadorBloqueante
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.IOException
import javax.inject.Inject

/**
 * Restaura una copia, paso a paso, y permite deshacerlo.
 *
 * El orden de los pasos es lo que hace segura la operacion mas peligrosa de la
 * app (docs/05): se lee el archivo **sin la frase** para enseñar que trae y que
 * se va a sustituir; solo despues se pide la frase; y solo tras una
 * confirmacion explicita se toca algo. Lo delicado de verdad -la copia previa,
 * la transaccion unica- vive en [RestauradorDeCopias].
 *
 * Sobre el `@Suppress`: igual que en [CopiaViewModel], son dependencias de un
 * constructor que rellena Hilt, y cada una es una pieza distinta del proceso.
 */
@Suppress("LongParameterList")
@HiltViewModel
class RestaurarViewModel
    @Inject
    constructor(
        private val archivo: ArchivoDeBackup,
        private val restaurador: RestauradorDeCopias,
        private val recolector: RecolectorDeDatos,
        private val abridor: AbridorDeOrigen,
        private val informacion: InformacionDeLaApp,
        private val reloj: Clock,
        @param:DespachadorBloqueante private val trabajo: CoroutineDispatcher,
    ) : ViewModel() {
        private val estado = MutableStateFlow(RestaurarUiState())
        val uiState: StateFlow<RestaurarUiState> = estado.asStateFlow()

        /** El archivo elegido. Solo existe mientras se esta en el resumen. */
        private var uriElegida: String? = null

        init {
            refrescarCopiaPrevia()
        }

        /** Lee que trae el archivo elegido, sin pedir todavia la frase. */
        fun elegirArchivo(uri: String) {
            uriElegida = uri
            cambiarPaso(PasoDeRestauracion.LeyendoArchivo)
            viewModelScope.launch {
                cambiarPaso(
                    try {
                        val manifiesto = withContext(trabajo) { abridor.abrir(uri).use(archivo::leerManifiesto) }
                        if (manifiesto.versionDelFormato > VERSION_DEL_FORMATO) {
                            // Mejor decirlo aqui que despues de hacerle teclear la
                            // frase: con este archivo no hay nada que hacer.
                            PasoDeRestauracion.Fallida(ACTUALIZA_LA_APP)
                        } else {
                            PasoDeRestauracion.Resumen(copia = manifiesto, actual = recuentoActual())
                        }
                    } catch (e: BackupInvalido) {
                        registrar(e)
                        PasoDeRestauracion.Fallida(e.message ?: NO_ES_UNA_COPIA)
                    } catch (e: IOException) {
                        registrar(e)
                        PasoDeRestauracion.Fallida(e.message ?: NO_SE_PUDO_LEER)
                    },
                )
            }
        }

        /**
         * Descifra la copia con [frase] y la restaura.
         *
         * La pantalla ya pidio la confirmacion. [frase] se borra en cuanto se ha
         * copiado, pase lo que pase.
         */
        fun restaurar(frase: CharArray) {
            val resumen = estado.value.paso as? PasoDeRestauracion.Resumen
            val uri = uriElegida
            if (resumen == null || uri == null || !cumpleElMinimo(frase)) {
                // Una frase que ni siquiera cumple el minimo no puede ser la de
                // ninguna copia: es una frase incorrecta mas.
                if (resumen != null) cambiarPaso(resumen.copy(fraseIncorrecta = true))
                frase.fill(BORRADO)
                return
            }

            val clave = FraseDeRespaldo(frase).also { frase.fill(BORRADO) }
            cambiarPaso(PasoDeRestauracion.Restaurando)
            viewModelScope.launch {
                try {
                    cambiarPaso(intentarRestaurar(uri, clave, resumen))
                } finally {
                    clave.olvidar()
                    refrescarCopiaPrevia()
                }
            }
        }

        /**
         * Restaura y traduce cada forma de fallar a lo que la pantalla tiene que
         * decir. En todos los casos salvo el de los ajustes, **no se ha cambiado
         * nada**: la transaccion de la base lo garantiza.
         */
        private suspend fun intentarRestaurar(
            uri: String,
            clave: FraseDeRespaldo,
            resumen: PasoDeRestauracion.Resumen,
        ): PasoDeRestauracion =
            try {
                withContext(trabajo) {
                    val copia = abridor.abrir(uri).use { archivo.leer(it, clave) }
                    restaurador.restaurar(copia, procedencia(), reloj.now().toEpochMilliseconds())
                }
                uriElegida = null
                PasoDeRestauracion.Restaurada()
            } catch (e: FraseIncorrecta) {
                registrar(e)
                resumen.copy(fraseIncorrecta = true)
            } catch (e: RestauracionIncompleta) {
                registrar(e)
                uriElegida = null
                PasoDeRestauracion.Restaurada(aviso = e.message)
            } catch (e: BackupInvalido) {
                registrar(e)
                PasoDeRestauracion.Fallida(e.message ?: NO_ES_UNA_COPIA)
            } catch (e: IOException) {
                registrar(e)
                PasoDeRestauracion.Fallida(NADA_CAMBIADO)
            } catch (e: SQLiteException) {
                // Una copia incoherente -un movimiento que apunta a una cuenta que
                // no trae- choca con las claves foraneas. La transaccion ya lo
                // deshizo todo.
                registrar(e)
                PasoDeRestauracion.Fallida(NADA_CAMBIADO)
            } catch (e: IllegalArgumentException) {
                // Un dato que el dominio no admite, como un importe negativo. Salta
                // al convertir, antes de escribir nada.
                registrar(e)
                PasoDeRestauracion.Fallida(NADA_CAMBIADO)
            }

        private fun cumpleElMinimo(frase: CharArray): Boolean =
            frase.size >= FraseDeRespaldo.LONGITUD_MINIMA && frase.any { !it.isWhitespace() }

        /** Devuelve la app a como estaba antes de la ultima restauracion. */
        fun deshacer() {
            if (estado.value.copiaPreviaEnMillis == null) return
            cambiarPaso(PasoDeRestauracion.Deshaciendo)
            viewModelScope.launch {
                cambiarPaso(
                    try {
                        withContext(trabajo) { restaurador.deshacer(procedencia(), reloj.now().toEpochMilliseconds()) }
                        PasoDeRestauracion.Deshecha
                    } catch (e: IOException) {
                        registrar(e)
                        PasoDeRestauracion.Fallida(e.message ?: NO_SE_PUDO_DESHACER)
                    } catch (e: BackupInvalido) {
                        registrar(e)
                        PasoDeRestauracion.Fallida(e.message ?: NO_SE_PUDO_DESHACER)
                    } catch (e: IllegalStateException) {
                        registrar(e)
                        PasoDeRestauracion.Fallida(e.message ?: NO_SE_PUDO_DESHACER)
                    } catch (e: RestauracionIncompleta) {
                        registrar(e)
                        PasoDeRestauracion.Fallida(e.message ?: NO_SE_PUDO_DESHACER)
                    } catch (e: SQLiteException) {
                        registrar(e)
                        PasoDeRestauracion.Fallida(NADA_CAMBIADO_AL_DESHACER)
                    } finally {
                        refrescarCopiaPrevia()
                    },
                )
            }
        }

        /** Vuelve al principio, olvidando el archivo elegido. */
        fun volverAlInicio() {
            uriElegida = null
            cambiarPaso(PasoDeRestauracion.Inicio)
        }

        private suspend fun recuentoActual(): Recuento =
            withContext(trabajo) {
                val datos = recolector.recolectar()
                Recuento(
                    cuentas = datos.cuentas.size,
                    categorias = datos.categorias.size,
                    transacciones = datos.transacciones.size,
                    planes = datos.planes.size,
                )
            }

        private fun refrescarCopiaPrevia() {
            viewModelScope.launch {
                val manifiesto =
                    try {
                        withContext(trabajo) { restaurador.copiaPreviaDisponible() }
                    } catch (e: IOException) {
                        registrar(e)
                        null
                    } catch (e: BackupInvalido) {
                        // Una copia previa ilegible no se puede deshacer: mejor no
                        // ofrecer un boton que va a fallar.
                        registrar(e)
                        null
                    }
                estado.update { it.copy(copiaPreviaEnMillis = manifiesto?.creadoEnMillis) }
            }
        }

        private fun procedencia() =
            Procedencia(
                versionDelEsquema = informacion.versionDelEsquema,
                versionDeLaApp = informacion.versionDeLaApp,
                dispositivo = informacion.dispositivo,
            )

        private fun cambiarPaso(paso: PasoDeRestauracion) = estado.update { it.copy(paso = paso) }

        /**
         * Deja constancia de por que fallo algo.
         *
         * La pantalla enseña un mensaje pensado para el usuario, que no dice la
         * causa tecnica. Sin esto, un fallo en una restauracion no se puede
         * diagnosticar: asi aparecio que borrar las categorias chocaba con sus
         * claves foraneas, y solo se vio metiendo trazas a mano. El registro de
         * Android solo lo lee quien tiene el telefono conectado por adb.
         */
        private fun registrar(causa: Throwable) {
            android.util.Log.w(ETIQUETA_DEL_REGISTRO, "Fallo en la copia de seguridad", causa)
        }

        private companion object {
            const val BORRADO = '\u0000'
            const val ETIQUETA_DEL_REGISTRO = "MiPlata.Copia"
            const val NO_ES_UNA_COPIA = "El archivo no es una copia de mi-plata"
            const val NO_SE_PUDO_LEER = "No se pudo leer el archivo"
            const val NO_SE_PUDO_DESHACER = "No se pudo deshacer la restauracion"
            const val ACTUALIZA_LA_APP =
                "Esta copia es de una version mas nueva de la app. Actualizala para poder restaurarla."
            const val NADA_CAMBIADO = "No se pudo restaurar la copia. No se ha cambiado nada."
            const val NADA_CAMBIADO_AL_DESHACER = "No se pudo volver a los datos anteriores. No se ha cambiado nada."
        }
    }
