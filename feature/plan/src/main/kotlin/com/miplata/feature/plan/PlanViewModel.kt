package com.miplata.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.model.sumar
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import com.miplata.core.domain.usecase.CalcularPlanPorCuentasUseCase
import com.miplata.core.domain.usecase.RegistrarRepartoUseCase
import com.miplata.core.domain.usecase.repartoPendiente
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Lo necesario para deshacer una eliminacion **del todo**.
 *
 * No basta con la linea. Al borrarla, la base suelta los movimientos que
 * estaban enganchados a ella (clave foranea `ON DELETE SET NULL`), y volver a
 * crear la linea no los vuelve a enganchar: el resumen seguiria sin saber de
 * que linea eran. Por eso se apuntan al borrar y se restauran al deshacer.
 */
private data class Eliminacion(
    val linea: LineaDePlan,
    val mes: Mes,
    /** Donde estaba, para volver a su sitio y no al final. */
    val posicion: Int,
    val movimientos: List<TransaccionId>,
)

/** Lo que hace falta, ademas del plan, para verlo por cuentas. */
private data class DatosDeCuentas(
    val cuentas: List<Cuenta>,
    val movimientos: List<Transaccion>,
    val ajustes: Ajustes,
)

/** El plan que se esta viendo, y si ya existe en la base o es una propuesta. */
private data class PlanEnPantalla(
    val plan: PlanMensual,
    val esBorrador: Boolean,
)

/**
 * El estado de la pantalla del plan.
 *
 * Expone un solo `StateFlow` y recibe eventos; nunca al reves (docs/01). La
 * pantalla no conoce repositorios ni use cases: solo dibuja un estado y avisa de
 * lo que el usuario hizo.
 */
@HiltViewModel
class PlanViewModel
    @Inject
    constructor(
        private val planes: PlanRepository,
        private val transacciones: TransaccionRepository,
        private val abrirPlan: AbrirPlanDelMesUseCase,
        private val ids: GeneradorDeIds,
        private val ajustes: AjustesRepository,
        private val calendario: Calendario,
        private val calcularPlanPorCuentas: CalcularPlanPorCuentasUseCase,
        private val registrarReparto: RegistrarRepartoUseCase,
        cuentas: CuentaRepository,
    ) : ViewModel() {
        private val mesSeleccionado = MutableStateFlow(calendario.mesActual())

        /** Una escritura del plan cada vez; ver [editarPlan]. */
        private val escrituras = Mutex()

        private val editor = MutableStateFlow<EditorDeLinea?>(null)
        private val eliminacion = MutableStateFlow<Eliminacion?>(null)

        /**
         * El plan del mes en pantalla.
         *
         * El borrador se calcula **una vez por mes**. Hacerlo dentro del flujo
         * observado lo recalcularia en cada emision, generando identificadores
         * nuevos cada vez y con la pantalla saltando bajo el dedo del usuario.
         *
         * En cuanto el plan existe en la base, `observarDe` emite el guardado y
         * el borrador deja de usarse.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val planEnPantalla: StateFlow<PlanEnPantalla?> =
            mesSeleccionado
                .flatMapLatest { mes ->
                    flow {
                        val abierto = abrirPlan(mes)
                        emitAll(
                            planes.observarDe(mes).map { guardado ->
                                PlanEnPantalla(
                                    plan = guardado ?: abierto.plan,
                                    esBorrador = guardado == null,
                                )
                            },
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(CINCO_SEGUNDOS), null)

        private val datos =
            combine(cuentas.observarTodas(), transacciones.observarTodas(), ajustes.observar(), ::DatosDeCuentas)

        val uiState: StateFlow<PlanUiState> =
            combine(
                mesSeleccionado,
                planEnPantalla,
                datos,
                editor,
                eliminacion,
            ) { mes, enPantalla, datos, abierto, eliminada ->
                val vigentes = datos.cuentas.filterNot { it.archivada }
                if (enPantalla == null) {
                    PlanUiState(mes = mes, moneda = datos.ajustes.moneda, editor = abierto, cuentas = vigentes)
                } else {
                    val plan = enPantalla.plan
                    val periodo = PeriodoMensual(mes, datos.ajustes.primerDiaDelMesFinanciero)
                    val porCuentas =
                        calcularPlanPorCuentas(periodo, datos.cuentas, plan, datos.movimientos, calendario.hoy())
                    PlanUiState(
                        mes = mes,
                        moneda = datos.ajustes.moneda,
                        secciones = seccionesDe(plan),
                        ingresos = plan.totalPlanificadoDe(TipoDeLinea.INGRESO),
                        salidas = salidasDe(plan),
                        cargando = false,
                        esBorrador = enPantalla.esBorrador,
                        editor = abierto,
                        eliminada = eliminada?.linea,
                        porCuentas = porCuentas,
                        repartoPendiente =
                            porCuentas?.let { repartoPendiente(periodo, it, datos.movimientos).values.sumar() }
                                ?: Money.ZERO,
                        cuentas = vigentes,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                // Sigue vivo 5s tras perder el ultimo observador, asi que una
                // rotacion no obliga a releer la base.
                started = SharingStarted.WhileSubscribed(CINCO_SEGUNDOS),
                initialValue = PlanUiState(mes = mesSeleccionado.value),
            )

        fun alEvento(evento: EventoDelPlan) {
            when (evento) {
                EventoDelPlan.MesAnterior -> mesSeleccionado.value = mesSeleccionado.value.anterior()
                EventoDelPlan.MesSiguiente ->
                    mesSeleccionado.value = mesSeleccionado.value.siguiente()

                is EventoDelPlan.EventoDeLaHoja -> alHoja(evento)
                is EventoDelPlan.EliminarLinea -> eliminar(evento.id)
                EventoDelPlan.DeshacerEliminacion -> deshacerEliminacion()
                EventoDelPlan.OlvidarEliminacion -> eliminacion.value = null
                EventoDelPlan.RegistrarReparto -> registrarElReparto()
            }
        }

        private fun alHoja(evento: EventoDelPlan.EventoDeLaHoja) {
            when (evento) {
                EventoDelPlan.NuevaLinea -> editor.value = hojaNueva()
                is EventoDelPlan.EditarLinea -> editor.value = hojaDe(evento.linea)
                EventoDelPlan.CerrarEditor -> editor.value = null
                EventoDelPlan.GuardarLinea -> guardarLinea()
                is EventoDelPlan.CambioEnEditor -> editor.update { it?.aplicar(evento) }
            }
        }

        /**
         * Una hoja en blanco. Con el plan vacio empieza en los ingresos, que es el
         * orden en el que la gente piensa su mes (y lo que dice el aviso de la
         * pantalla vacia).
         */
        private fun hojaNueva() =
            EditorDeLinea(tipo = if (uiState.value.estaVacio) TipoDeLinea.INGRESO else TipoDeLinea.GASTO_VARIABLE)

        private fun hojaDe(linea: LineaDePlan) =
            EditorDeLinea(
                id = linea.id,
                nombre = linea.nombre,
                tipo = linea.tipo,
                monto = linea.montoPlanificado,
                activa = linea.activa,
                cuentaId = linea.cuentaId,
            )

        private fun EditorDeLinea.aplicar(cambio: EventoDelPlan.CambioEnEditor) =
            when (cambio) {
                is EventoDelPlan.CambioEnEditor.Nombre -> copy(nombre = cambio.nombre)
                is EventoDelPlan.CambioEnEditor.Monto -> copy(monto = cambio.monto)
                is EventoDelPlan.CambioEnEditor.Tipo -> copy(tipo = cambio.tipo)
                is EventoDelPlan.CambioEnEditor.Activa -> copy(activa = cambio.activa)
                is EventoDelPlan.CambioEnEditor.Cuenta -> copy(cuentaId = cambio.cuentaId)
            }

        /**
         * Anota las transferencias del reparto que faltan en el mes en pantalla.
         *
         * Con el mes y el plan que se ven, no con los de hoy: quien mira abril
         * y pulsa el boton espera que se anote el reparto de abril.
         */
        private fun registrarElReparto() {
            val porCuentas = uiState.value.porCuentas ?: return
            val mes = uiState.value.mes
            viewModelScope.launch {
                registrarReparto(PeriodoMensual(mes, ajustes.obtener().primerDiaDelMesFinanciero), porCuentas)
            }
        }

        /**
         * Lleva lo que hay en la hoja al plan: una linea nueva al final, o la
         * existente cambiada **conservando lo que la hoja no toca** -categoria,
         * dia-, que una copia desde cero perderia.
         */
        private fun guardarLinea() {
            val enHoja = editor.value ?: return
            editor.value = null
            val id = enHoja.id
            if (id == null) {
                editarPlan { plan ->
                    plan.copy(
                        lineas =
                            plan.lineas +
                                LineaDePlan(
                                    id = ids.nuevaLineaId(),
                                    nombre = enHoja.nombre.trim(),
                                    tipo = enHoja.tipo,
                                    montoPlanificado = enHoja.monto,
                                    activa = enHoja.activa,
                                    cuentaId = enHoja.cuentaId,
                                ),
                    )
                }
            } else {
                editarLinea(id) {
                    it.copy(
                        nombre = enHoja.nombre.trim(),
                        tipo = enHoja.tipo,
                        montoPlanificado = enHoja.monto,
                        activa = enHoja.activa,
                        cuentaId = enHoja.cuentaId,
                    )
                }
            }
        }

        private fun eliminar(id: LineaId) {
            val enPantalla = planEnPantalla.value?.plan ?: return
            if (editor.value?.id == id) editor.value = null
            viewModelScope.launch {
                escrituras.withLock {
                    val actual = planes.obtenerDe(enPantalla.mes) ?: enPantalla
                    val posicion = actual.lineas.indexOfFirst { it.id == id }
                    if (posicion < 0) return@withLock
                    // Antes de borrar: despues, la base ya los habra soltado.
                    val enganchados =
                        transacciones
                            .observarTodas()
                            .first()
                            .filter { it.lineaDePlanId == id }
                            .map { it.id }
                    planes.guardar(actual.copy(lineas = actual.lineas.filterNot { it.id == id }))
                    eliminacion.value = Eliminacion(actual.lineas[posicion], actual.mes, posicion, enganchados)
                }
            }
        }

        private fun deshacerEliminacion() {
            val deshecha = eliminacion.value ?: return
            eliminacion.value = null
            viewModelScope.launch {
                escrituras.withLock {
                    val actual = planes.obtenerDe(deshecha.mes) ?: return@withLock
                    val lineas =
                        actual.lineas.toMutableList().apply {
                            add(deshecha.posicion.coerceAtMost(size), deshecha.linea)
                        }
                    planes.guardar(actual.copy(lineas = lineas))
                    deshecha.movimientos.forEach { id ->
                        transacciones.obtener(id)?.let {
                            transacciones.guardar(it.copy(lineaDePlanId = deshecha.linea.id))
                        }
                    }
                }
            }
        }

        private fun editarLinea(
            id: LineaId,
            cambio: (LineaDePlan) -> LineaDePlan,
        ) = editarPlan { plan ->
            plan.copy(lineas = plan.lineas.map { if (it.id == id) cambio(it) else it })
        }

        /**
         * Aplica un cambio al plan en pantalla y lo guarda.
         *
         * Cualquier edicion lo persiste, incluido el borrador copiado del mes
         * anterior: desde el primer cambio deja de ser una propuesta y pasa a ser
         * el plan del usuario.
         *
         * Las escrituras van **de una en una y en orden**, y cada cambio se aplica
         * sobre lo ultimo que se guardo, no sobre lo que hay en pantalla. Escribir
         * un nombre genera una edicion por tecla, mucho mas rapido de lo que la
         * base tarda en devolver el plan: con el plan en pantalla como punto de
         * partida, cada edicion pisaria a las anteriores que aun no han vuelto
         * -el importe recien tecleado borraria el nombre- y dos guardados en
         * paralelo podrian terminar en cualquier orden, dejando en la base una
         * version a medias.
         */
        private fun editarPlan(cambio: (PlanMensual) -> PlanMensual) {
            val enPantalla = planEnPantalla.value?.plan ?: return
            viewModelScope.launch {
                escrituras.withLock {
                    val actual = planes.obtenerDe(enPantalla.mes) ?: enPantalla
                    planes.guardar(cambio(actual))
                }
            }
        }

        private fun seccionesDe(plan: PlanMensual): List<SeccionDelPlan> =
            TipoDeLinea.entries.map { tipo ->
                SeccionDelPlan(
                    tipo = tipo,
                    lineas = plan.lineas.filter { it.tipo == tipo },
                    total = plan.totalPlanificadoDe(tipo),
                )
            }

        private fun salidasDe(plan: PlanMensual): Money =
            TipoDeLinea.entries
                .filter { it.restaDelDisponible }
                .fold(Money.ZERO) { suma, tipo -> suma + plan.totalPlanificadoDe(tipo) }

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
        }
    }
