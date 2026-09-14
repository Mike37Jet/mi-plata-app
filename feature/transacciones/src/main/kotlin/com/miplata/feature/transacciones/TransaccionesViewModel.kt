package com.miplata.feature.transacciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.AgruparMovimientosPorDiaUseCase
import com.miplata.core.domain.usecase.DiaConMovimientos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Las piezas que dependen del mes elegido, resueltas a la vez. */
private data class DatosDelMes(
    val periodo: PeriodoMensual,
    val movimientos: List<Transaccion>,
    val lineasDelPlan: List<LineaDePlan>,
)

/** Los catalogos con los que se rellenan los selectores del editor. */
private data class Catalogos(
    val cuentas: List<Cuenta>,
    val categorias: List<Categoria>,
)

/**
 * El estado de la pantalla de movimientos.
 *
 * Es la mitad "realidad" del modelo (docs/00): lo que de verdad paso, frente a
 * lo que el plan decia que iba a pasar. Todo lo que se anota aqui aparece
 * inmediatamente en el Resumen y en los saldos de Cuentas, porque las tres
 * pantallas leen los mismos movimientos.
 *
 * Sobre el `@Suppress`: son ocho dependencias, y detekt avisa con razon para
 * una funcion normal. Aqui es un constructor que rellena Hilt, y cada una es
 * una pieza distinta que la pantalla necesita de verdad; agruparlas en un
 * objeto "de dependencias" solo moveria la lista de sitio.
 */
@Suppress("LongParameterList")
@HiltViewModel
class TransaccionesViewModel
    @Inject
    constructor(
        private val transacciones: TransaccionRepository,
        private val ids: GeneradorDeIds,
        private val calendario: Calendario,
        cuentas: CuentaRepository,
        categorias: CategoriaRepository,
        planes: PlanRepository,
        ajustes: AjustesRepository,
        private val agruparPorDia: AgruparMovimientosPorDiaUseCase,
    ) : ViewModel() {
        private val mesSeleccionado = MutableStateFlow(calendario.mesActual())

        /**
         * El editor vive fuera del flujo de datos.
         *
         * Si formara parte de lo que emite el repositorio, cada tecla escrita
         * competiria con la siguiente emision de la base y el cursor saltaria.
         */
        private val editor = MutableStateFlow<EditorDeMovimiento?>(null)

        private val catalogos =
            combine(cuentas.observarTodas(), categorias.observarTodas()) { lista, cats ->
                // Las cuentas archivadas no se ofrecen para anotar algo nuevo:
                // archivar es justamente decir "ya no uso esta".
                Catalogos(lista.filterNot { it.archivada }, cats)
            }

        /**
         * El periodo que corresponde al mes elegido.
         *
         * Sale de los ajustes, no de un dia 1 fijo: quien cobra el 25 tiene su
         * mes del 25 al 24, y tanto la lista como la fecha por defecto de un
         * movimiento nuevo tienen que respetarlo.
         */
        private val periodoActual: StateFlow<PeriodoMensual> =
            combine(mesSeleccionado, ajustes.observar()) { mes, configuracion ->
                configuracion.periodoDe(mes)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(CINCO_SEGUNDOS),
                initialValue = Ajustes().periodoDe(mesSeleccionado.value),
            )

        @OptIn(ExperimentalCoroutinesApi::class)
        private val datosDelMes =
            periodoActual.flatMapLatest { periodo ->
                combine(
                    transacciones.observarDelPeriodo(periodo),
                    planes.observarDe(periodo.mes),
                ) { movimientos, plan ->
                    DatosDelMes(periodo, movimientos, plan?.lineasActivas.orEmpty())
                }
            }

        val uiState: StateFlow<TransaccionesUiState> =
            combine(
                mesSeleccionado,
                datosDelMes,
                catalogos,
                ajustes.observar(),
                editor,
            ) { mes, delMes, catalogo, configuracion, abierto ->
                TransaccionesUiState(
                    mes = mes,
                    moneda = configuracion.moneda,
                    cargando = false,
                    dias = diasDe(delMes.movimientos, catalogo, delMes.lineasDelPlan),
                    ingresos = totalDe(delMes.movimientos, TipoDeTransaccion.INGRESO),
                    gastos = totalDe(delMes.movimientos, TipoDeTransaccion.GASTO),
                    cuentas = catalogo.cuentas,
                    categorias = catalogo.categorias,
                    lineasDelPlan = delMes.lineasDelPlan,
                    editor = abierto,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(CINCO_SEGUNDOS),
                initialValue = TransaccionesUiState(mes = mesSeleccionado.value),
            )

        fun alEvento(evento: EventoDeMovimientos) {
            when (evento) {
                EventoDeMovimientos.MesAnterior ->
                    mesSeleccionado.value = mesSeleccionado.value.anterior()

                EventoDeMovimientos.MesSiguiente ->
                    mesSeleccionado.value = mesSeleccionado.value.siguiente()

                EventoDeMovimientos.AnotarMovimiento -> editor.value = editorNuevo()
                is EventoDeMovimientos.EditarMovimiento -> editor.value = editorDe(evento.transaccion)
                EventoDeMovimientos.CerrarEditor -> editor.value = null

                EventoDeMovimientos.Guardar -> guardar()
                EventoDeMovimientos.Eliminar -> eliminar()

                is EventoDeMovimientos.CambioDeCampo -> editar { aplicar(evento, it) }
            }
        }

        /** Aplica un cambio de campo al formulario abierto. */
        private fun aplicar(
            cambio: EventoDeMovimientos.CambioDeCampo,
            actual: EditorDeMovimiento,
        ): EditorDeMovimiento =
            when (cambio) {
                is EventoDeMovimientos.CambioDeCampo.Monto -> actual.copy(monto = cambio.monto)
                is EventoDeMovimientos.CambioDeCampo.Tipo -> cambiarTipo(actual, cambio.tipo)
                is EventoDeMovimientos.CambioDeCampo.Fecha -> actual.copy(fecha = cambio.fecha)
                is EventoDeMovimientos.CambioDeCampo.CuentaOrigen ->
                    actual.copy(cuentaOrigenId = cambio.cuentaId)

                is EventoDeMovimientos.CambioDeCampo.CuentaDestino ->
                    actual.copy(cuentaDestinoId = cambio.cuentaId)

                is EventoDeMovimientos.CambioDeCampo.Categoria ->
                    actual.copy(categoriaId = cambio.categoriaId)

                is EventoDeMovimientos.CambioDeCampo.DeLineaDePlan ->
                    actual.copy(lineaDePlanId = cambio.lineaId)

                is EventoDeMovimientos.CambioDeCampo.Nota -> actual.copy(nota = cambio.nota)
            }

        /**
         * Un movimiento nuevo llega con todo lo que se puede adivinar ya puesto.
         *
         * Fecha de hoy y la primera cuenta: son los dos valores que casi siempre
         * acierta, y la diferencia entre anotar un gasto en dos toques o en seis.
         * Una app de finanzas en la que anotar cuesta se queda sin datos, y sin
         * datos el resumen no sirve para nada.
         */
        private fun editorNuevo(): EditorDeMovimiento {
            val hoy = calendario.hoy()
            val periodo = periodoActual.value

            return EditorDeMovimiento(
                // Si se esta mirando otro mes, la fecha por defecto es el primer
                // dia de ese mes: anotar algo con la fecha de hoy mientras miras
                // marzo lo haria desaparecer de la lista nada mas guardarlo.
                fecha = if (periodo.contiene(hoy)) hoy else periodo.inicio,
                cuentaOrigenId =
                    uiState.value.cuentas
                        .firstOrNull()
                        ?.id,
            )
        }

        private fun editorDe(transaccion: Transaccion) =
            EditorDeMovimiento(
                id = transaccion.id,
                monto = transaccion.monto,
                tipo = transaccion.tipo,
                fecha = transaccion.fecha,
                cuentaOrigenId = transaccion.cuentaOrigenId,
                cuentaDestinoId = transaccion.cuentaDestinoId,
                categoriaId = transaccion.categoriaId,
                lineaDePlanId = transaccion.lineaDePlanId,
                nota = transaccion.nota.orEmpty(),
            )

        /**
         * Cambiar de tipo limpia lo que deja de tener sentido.
         *
         * Una transferencia no lleva categoria ni linea de plan -no es un gasto,
         * es dinero cambiando de sitio- y solo ella lleva cuenta de destino. Sin
         * esta limpieza se podria guardar un gasto con destino, que el modelo
         * rechaza: el error saldria al pulsar Guardar y no antes.
         */
        private fun cambiarTipo(
            actual: EditorDeMovimiento,
            nuevo: TipoDeTransaccion,
        ): EditorDeMovimiento =
            if (nuevo == TipoDeTransaccion.TRANSFERENCIA) {
                actual.copy(tipo = nuevo, categoriaId = null, lineaDePlanId = null)
            } else {
                actual.copy(tipo = nuevo, cuentaDestinoId = null)
            }

        private fun editar(cambio: (EditorDeMovimiento) -> EditorDeMovimiento) {
            editor.value = editor.value?.let(cambio)
        }

        private fun guardar() {
            val enEdicion = editor.value ?: return
            if (!enEdicion.puedeGuardar) return

            val transaccion =
                Transaccion(
                    id = enEdicion.id ?: ids.nuevaTransaccionId(),
                    fecha = enEdicion.fecha,
                    monto = enEdicion.monto,
                    tipo = enEdicion.tipo,
                    // `puedeGuardar` ya garantizo que hay cuenta de origen.
                    cuentaOrigenId = enEdicion.cuentaOrigenId!!,
                    cuentaDestinoId = enEdicion.cuentaDestinoId.takeIf { enEdicion.esTransferencia },
                    categoriaId = enEdicion.categoriaId,
                    lineaDePlanId = enEdicion.lineaDePlanId,
                    nota = enEdicion.nota.trim().takeIf { it.isNotBlank() },
                )

            editor.value = null
            viewModelScope.launch { transacciones.guardar(transaccion) }
        }

        private fun eliminar() {
            val id = editor.value?.id
            editor.value = null
            if (id != null) viewModelScope.launch { transacciones.eliminar(id) }
        }

        private fun diasDe(
            movimientos: List<Transaccion>,
            catalogo: Catalogos,
            lineas: List<LineaDePlan>,
        ): List<DiaEnLista> {
            val cuentaPorId = catalogo.cuentas.associateBy { it.id }
            val categoriaPorId = catalogo.categorias.associateBy { it.id }
            val lineaPorId = lineas.associateBy { it.id }

            return agruparPorDia(movimientos).map { dia ->
                aLista(dia, cuentaPorId, categoriaPorId, lineaPorId)
            }
        }

        private fun aLista(
            dia: DiaConMovimientos,
            cuentaPorId: Map<CuentaId, Cuenta>,
            categoriaPorId: Map<CategoriaId, Categoria>,
            lineaPorId: Map<LineaId, LineaDePlan>,
        ) = DiaEnLista(
            fecha = dia.fecha,
            neto = dia.neto,
            movimientos =
                dia.movimientos.map { transaccion ->
                    MovimientoEnLista(
                        transaccion = transaccion,
                        // Puede no estar si la cuenta se archivo o se borro: el
                        // movimiento sigue siendo real y tiene que poder verse.
                        cuenta = cuentaPorId[transaccion.cuentaOrigenId]?.nombre.orEmpty(),
                        cuentaDestino =
                            transaccion.cuentaDestinoId?.let { cuentaPorId[it]?.nombre },
                        categoria = transaccion.categoriaId?.let { categoriaPorId[it]?.nombre },
                        // El puente con el plan: "esto era de la linea
                        // de Comida". Nulo en un gasto imprevisto, que es un
                        // caso normal y no un error.
                        lineaDePlan =
                            transaccion.lineaDePlanId
                                ?.let { lineaPorId[it]?.nombre }
                                ?.takeIf { it.isNotBlank() },
                    )
                },
        )

        private fun totalDe(
            movimientos: List<Transaccion>,
            tipo: TipoDeTransaccion,
        ): Money =
            movimientos
                .filter { it.tipo == tipo }
                .fold(Money.ZERO) { suma, transaccion -> suma + transaccion.monto }

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
        }
    }
