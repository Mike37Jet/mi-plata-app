package com.miplata.feature.bienvenida

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.usecase.CompletarPrimerosPasosUseCase
import com.miplata.core.domain.usecase.PrimerosPasos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/**
 * Los primeros pasos, de principio a fin.
 *
 * No se guarda nada hasta el final. Si el usuario abandona a medias, la app no
 * queda con una moneda elegida y sin cuenta: vuelve a empezar la bienvenida la
 * proxima vez, entera.
 */
@HiltViewModel
class BienvenidaViewModel
    @Inject
    constructor(
        private val completar: CompletarPrimerosPasosUseCase,
    ) : ViewModel() {
        private val estado = MutableStateFlow(estadoInicial(Locale.getDefault()))
        val uiState: StateFlow<BienvenidaUiState> = estado.asStateFlow()

        fun alEvento(evento: EventoDeBienvenida) {
            when (evento) {
                EventoDeBienvenida.Siguiente -> moverse(+1)
                EventoDeBienvenida.Atras -> moverse(-1)
                is EventoDeBienvenida.ElegirMoneda -> estado.update { it.copy(moneda = evento.moneda) }
                is EventoDeBienvenida.CambiarPrimerDia ->
                    estado.update { it.copy(primerDiaDelMes = evento.dia.coerceIn(1, ULTIMO_DIA)) }
                is EventoDeBienvenida.CambiarNombreDeLaCuenta ->
                    estado.update { it.copy(nombreDeLaCuenta = evento.nombre) }
                is EventoDeBienvenida.ElegirTipoDeCuenta -> estado.update { it.copy(tipoDeCuenta = evento.tipo) }
                is EventoDeBienvenida.CambiarSaldo -> estado.update { it.copy(saldoActual = evento.saldo) }
                is EventoDeBienvenida.CambiarIngreso -> estado.update { it.copy(ingresoMensual = evento.ingreso) }
                is EventoDeBienvenida.Terminar -> terminar(evento)
            }
        }

        private fun moverse(pasos: Int) {
            estado.update { actual ->
                if (pasos > 0 && !actual.puedeSeguir) return@update actual
                val destino = (actual.paso.ordinal + pasos).coerceIn(0, PasoDeBienvenida.entries.lastIndex)
                actual.copy(paso = PasoDeBienvenida.entries[destino])
            }
        }

        private fun terminar(evento: EventoDeBienvenida.Terminar) {
            val actual = estado.value
            if (actual.guardando || actual.nombreDeLaCuenta.isBlank()) return
            estado.update { it.copy(guardando = true, errorAlGuardar = null) }

            viewModelScope.launch {
                try {
                    // NonCancellable porque esta pantalla desaparece A MITAD de
                    // guardar: en cuanto existe la cuenta, la app sale sola de la
                    // bienvenida y este ViewModel se destruye. Sin esto, cancelar
                    // su scope cortaria el guardado del plan, que va despues.
                    withContext(NonCancellable) {
                        completar(
                            PrimerosPasos(
                                moneda = actual.moneda,
                                primerDiaDelMes = actual.primerDiaDelMes,
                                nombreDeLaCuenta = actual.nombreDeLaCuenta,
                                tipoDeCuenta = actual.tipoDeCuenta,
                                saldoActual = actual.saldoActual,
                                ingresoMensual = actual.ingresoMensual.takeIf { evento.conIngreso },
                                nombreDelIngreso = evento.nombreDelIngreso,
                            ),
                        )
                    }
                    // No hay nada mas que hacer: en cuanto existe la cuenta, la app
                    // sale sola de la bienvenida (HayQueDarLaBienvenidaUseCase).
                } catch (e: IOException) {
                    // Los ajustes (DataStore) no se pudieron escribir.
                    noSePudoGuardar(e)
                } catch (e: SQLException) {
                    // La base (Room) no acepto la cuenta o el plan.
                    noSePudoGuardar(e)
                }
            }
        }

        /**
         * Lo que haya en pantalla sigue ahi: el usuario puede volver a pulsar sin
         * reescribirlo. Si la cuenta llego a guardarse, la app ya habra salido de
         * la bienvenida; si no, lo guardado hasta entonces -los ajustes- se
         * sobrescribe al reintentar.
         */
        private fun noSePudoGuardar(causa: Exception) {
            estado.update { it.copy(guardando = false, errorAlGuardar = causa.message.orEmpty()) }
        }

        internal companion object {
            const val ULTIMO_DIA = 31

            fun estadoInicial(locale: Locale): BienvenidaUiState {
                val sugeridas = monedasSugeridas(locale)
                return BienvenidaUiState(moneda = sugeridas.first(), monedasSugeridas = sugeridas)
            }
        }
    }

/** Para las previsualizaciones y los tests de la pantalla. */
internal fun estadoDeEjemplo(paso: PasoDeBienvenida = PasoDeBienvenida.BIENVENIDA) =
    BienvenidaViewModel.estadoInicial(Locale.US).copy(paso = paso, moneda = Moneda("USD"))
