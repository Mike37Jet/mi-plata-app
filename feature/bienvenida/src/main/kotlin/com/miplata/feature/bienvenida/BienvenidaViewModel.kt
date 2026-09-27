package com.miplata.feature.bienvenida

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.usecase.CompletarPrimerosPasosUseCase
import com.miplata.core.domain.usecase.PrimerosPasos
import com.miplata.core.domain.usecase.SobreInicial
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
                is EventoDeBienvenida.CambioDeCampo -> estado.update { it.con(evento) }
                is EventoDeBienvenida.Terminar -> terminar(evento)
            }
        }

        private fun BienvenidaUiState.con(cambio: EventoDeBienvenida.CambioDeCampo): BienvenidaUiState =
            when (cambio) {
                is EventoDeBienvenida.ElegirMoneda -> copy(moneda = cambio.moneda)
                is EventoDeBienvenida.CambiarPrimerDia -> copy(primerDiaDelMes = cambio.dia.coerceIn(1, ULTIMO_DIA))
                is EventoDeBienvenida.CambiarNombreDeLaCuenta -> copy(nombreDeLaCuenta = cambio.nombre)
                is EventoDeBienvenida.ElegirTipoDeCuenta -> copy(tipoDeCuenta = cambio.tipo)
                is EventoDeBienvenida.CambiarSaldo -> copy(saldoActual = cambio.saldo)
                is EventoDeBienvenida.CambiarIngreso -> copy(ingresoMensual = cambio.ingreso)
                is EventoDeBienvenida.AlternarSobre ->
                    copy(
                        sobres =
                            if (cambio.sobre in
                                sobres
                            ) {
                                sobres - cambio.sobre
                            } else {
                                sobres + (cambio.sobre to Money.ZERO)
                            },
                    )
                // Solo el saldo de un sobre marcado: uno desmarcado no tiene saldo.
                is EventoDeBienvenida.CambiarSaldoDeSobre ->
                    if (cambio.sobre in sobres) copy(sobres = sobres + (cambio.sobre to cambio.saldo)) else this
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
                                // En el orden de la lista, no en el que se marcaron.
                                sobres =
                                    SobreSugerido.entries.filter { it in actual.sobres }.map { sobre ->
                                        SobreInicial(
                                            nombre = evento.nombresDeLosSobres[sobre] ?: sobre.name,
                                            saldoActual = actual.sobres.getValue(sobre),
                                            intocable = sobre.intocable,
                                        )
                                    },
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
