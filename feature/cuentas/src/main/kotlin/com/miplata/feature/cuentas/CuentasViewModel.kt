package com.miplata.feature.cuentas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.CalcularSaldosDeCuentasUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * El estado de la pantalla de cuentas.
 *
 * Los saldos no se guardan en ningun sitio: se calculan en el dominio a partir
 * del saldo inicial y de todos los movimientos, cada vez (docs/03). Por eso el
 * flujo escucha a la vez a las cuentas y a las transacciones: borrar un gasto
 * cambia un saldo, y la pantalla se entera sola.
 */
@HiltViewModel
class CuentasViewModel
    @Inject
    constructor(
        private val cuentas: CuentaRepository,
        private val ids: GeneradorDeIds,
        transacciones: TransaccionRepository,
        ajustes: AjustesRepository,
        calcularSaldos: CalcularSaldosDeCuentasUseCase,
    ) : ViewModel() {
        /**
         * El editor vive fuera del flujo de datos.
         *
         * Si formara parte de lo que emite el repositorio, cada tecla escrita
         * competiria con la siguiente emision de la base y el cursor saltaria.
         * Se combina con los datos, pero lo gobierna el usuario.
         */
        private val editor = MutableStateFlow<EditorDeCuenta?>(null)

        val uiState: StateFlow<CuentasUiState> =
            combine(
                cuentas.observarTodas(),
                transacciones.observarTodas(),
                ajustes.observar(),
                editor,
            ) { lista, movimientos, configuracion, abierto ->
                val saldos = calcularSaldos(lista, movimientos, configuracion.moneda)

                CuentasUiState(
                    cuentas = saldos.cuentas,
                    total = saldos.total,
                    moneda = configuracion.moneda,
                    cuentasEnOtraMoneda = saldos.cuentasEnOtraMoneda,
                    cargando = false,
                    editor = abierto,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(CINCO_SEGUNDOS),
                initialValue = CuentasUiState(),
            )

        fun alEvento(evento: EventoDeCuentas) {
            when (evento) {
                EventoDeCuentas.CrearCuenta -> editor.value = EditorDeCuenta()
                is EventoDeCuentas.EditarCuenta -> editor.value = editorDe(evento.cuenta)
                EventoDeCuentas.CerrarEditor -> editor.value = null

                is EventoDeCuentas.CambiarNombre -> editar { it.copy(nombre = evento.nombre) }
                is EventoDeCuentas.CambiarTipo -> editar { it.copy(tipo = evento.tipo) }
                is EventoDeCuentas.CambiarSaldoInicial -> editar { it.copy(saldoInicial = evento.saldo) }
                is EventoDeCuentas.CambiarIncluirEnTotal -> editar { it.copy(incluirEnTotal = evento.incluir) }
                is EventoDeCuentas.CambiarArchivada -> editar { it.copy(archivada = evento.archivada) }

                EventoDeCuentas.Guardar -> guardar()
                EventoDeCuentas.Eliminar -> eliminar()
            }
        }

        private fun editorDe(cuenta: Cuenta) =
            EditorDeCuenta(
                id = cuenta.id,
                nombre = cuenta.nombre,
                tipo = cuenta.tipo,
                saldoInicial = cuenta.saldoInicial,
                incluirEnTotal = cuenta.incluirEnTotal,
                archivada = cuenta.archivada,
            )

        private fun editar(cambio: (EditorDeCuenta) -> EditorDeCuenta) {
            editor.value = editor.value?.let(cambio)
        }

        /**
         * Guarda y cierra.
         *
         * La moneda de una cuenta nueva es la de los ajustes: pedirla en el alta
         * seria una pregunta mas en el camino para el caso raro -tener cuentas en
         * dos monedas- a costa del caso de todos los dias.
         */
        private fun guardar() {
            val enEdicion = editor.value ?: return
            if (!enEdicion.puedeGuardar) return

            val moneda = uiState.value.moneda
            val cuenta =
                Cuenta(
                    id = enEdicion.id ?: ids.nuevaCuentaId(),
                    nombre = enEdicion.nombre.trim(),
                    tipo = enEdicion.tipo,
                    saldoInicial = enEdicion.saldoInicial,
                    moneda = moneda,
                    incluirEnTotal = enEdicion.incluirEnTotal,
                    archivada = enEdicion.archivada,
                )

            editor.value = null
            viewModelScope.launch { cuentas.guardar(cuenta) }
        }

        /**
         * Borra la cuenta.
         *
         * El repositorio marca la fila como eliminada en vez de quitarla, asi que
         * las transacciones que la apuntan siguen teniendo a donde apuntar. Aun
         * asi, archivar es casi siempre lo que el usuario quiere: la pantalla lo
         * ofrece primero y deja el borrado detras de una confirmacion.
         */
        private fun eliminar() {
            val id = editor.value?.id
            editor.value = null
            if (id != null) viewModelScope.launch { cuentas.eliminar(id) }
        }

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
        }
    }
