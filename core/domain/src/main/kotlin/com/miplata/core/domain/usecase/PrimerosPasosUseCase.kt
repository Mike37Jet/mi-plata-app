package com.miplata.core.domain.usecase

import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Reparto
import com.miplata.core.domain.model.RolDeCuenta
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Si la app tiene que empezar por la bienvenida.
 *
 * La respuesta sale de los datos y no de una marca de "ya visto". Sin ninguna
 * cuenta no se puede anotar ni un movimiento, asi que es lo minimo para que la
 * app sirva. Y derivarlo tiene dos ventajas sobre una marca guardada:
 *
 * - Quien restaura una copia en un movil nuevo sale de la bienvenida solo, en
 *   cuanto la copia trae sus cuentas. Con una marca, habria que acordarse de
 *   ponerla tambien al restaurar, y viajar con el backup.
 * - Si la app muere antes de guardar la cuenta, la bienvenida vuelve a salir.
 *   Una marca puesta demasiado pronto dejaria al usuario en una app sin cuentas.
 */
class HayQueDarLaBienvenidaUseCase(
    private val cuentas: CuentaRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        cuentas
            .observarTodas()
            .map { it.isEmpty() }
            .distinctUntilChanged()
}

/** Lo que se pregunta en la bienvenida. */
data class PrimerosPasos(
    val moneda: Moneda,
    val primerDiaDelMes: Int,
    val nombreDeLaCuenta: String,
    val tipoDeCuenta: TipoDeCuenta,
    /** Lo que hay hoy en la cuenta: sera su saldo inicial. */
    val saldoActual: Money,
    /** Cuanto entra al mes, o `null` si el usuario lo deja para despues. */
    val ingresoMensual: Money?,
    /**
     * Como se llamara la linea del ingreso en el plan.
     *
     * Llega ya traducido, como el nombre de una [CategoriaSemilla]: el dominio
     * pone la estructura y quien llama pone las palabras.
     */
    val nombreDelIngreso: String = "",
    /**
     * Los sobres que el usuario quiere crear desde el principio (docs/adr/0007).
     * Si hay alguno, la primera cuenta pasa a ser la principal.
     */
    val sobres: List<SobreInicial> = emptyList(),
)

/** Un sobre creado en la bienvenida, con el nombre ya traducido. */
data class SobreInicial(
    val nombre: String,
    val saldoActual: Money,
    val reparto: Reparto = Reparto.PorDefecto,
    val intocable: Boolean = false,
)

/**
 * Deja la app lista para usarse con lo que se pregunto en la bienvenida.
 *
 * No es atomico, y no hace falta: las escrituras van en un orden en el que
 * **cada paso intermedio es un estado valido** de la app.
 *
 * 1. Ajustes. Sin cuenta, la bienvenida sigue en pie y los volvera a pedir.
 * 2. Cuenta. Una app con una cuenta y sin plan es una app normal.
 * 3. Sobres. Si la app muere antes, la principal queda sin sobres, que es
 *    como queda quien no los usa; se anaden luego en Cuentas.
 * 4. Plan. Su linea de ingreso apunta a la cuenta, que ya existe.
 *
 * El orden al reves -plan antes que cuenta- dejaria una linea apuntando a una
 * cuenta que no existe, y la base la rechaza por su clave foranea. Si la app
 * muere entre la cuenta y el plan, se pierde el ingreso y nada mas; y el
 * usuario aterriza justo en la pantalla donde se añade.
 */
class CompletarPrimerosPasosUseCase(
    private val ajustes: AjustesRepository,
    private val cuentas: CuentaRepository,
    private val planes: PlanRepository,
    private val ids: GeneradorDeIds,
    private val calendario: Calendario,
) {
    suspend operator fun invoke(pasos: PrimerosPasos) {
        ajustes.guardar(
            ajustes.obtener().copy(
                moneda = pasos.moneda,
                primerDiaDelMesFinanciero = pasos.primerDiaDelMes,
            ),
        )

        val idDeLaCuenta = ids.nuevaCuentaId()
        cuentas.guardar(
            Cuenta(
                id = idDeLaCuenta,
                nombre = pasos.nombreDeLaCuenta.trim(),
                tipo = pasos.tipoDeCuenta,
                saldoInicial = pasos.saldoActual,
                moneda = pasos.moneda,
                rol = if (pasos.sobres.isEmpty()) RolDeCuenta.Independiente else RolDeCuenta.Principal,
            ),
        )
        guardarSobres(pasos)
        guardarPlan(pasos, idDeLaCuenta)
    }

    private suspend fun guardarSobres(pasos: PrimerosPasos) {
        pasos.sobres.forEach { sobre ->
            cuentas.guardar(
                Cuenta(
                    id = ids.nuevaCuentaId(),
                    nombre = sobre.nombre.trim(),
                    tipo = TipoDeCuenta.AHORRO,
                    saldoInicial = sobre.saldoActual,
                    moneda = pasos.moneda,
                    rol = RolDeCuenta.Sobre(sobre.reparto, sobre.intocable),
                ),
            )
        }
    }

    private suspend fun guardarPlan(
        pasos: PrimerosPasos,
        idDeLaCuenta: CuentaId,
    ) {
        val ingreso = pasos.ingresoMensual?.takeUnless { it.esCero } ?: return
        val mes = calendario.mesActual()
        // Quien borro todas sus cuentas vuelve a pasar por aqui con su plan del
        // mes intacto. Ese plan es suyo: no se pisa.
        if (planes.obtenerDe(mes) != null) return
        planes.guardar(
            PlanMensual(
                id = ids.nuevoPlanId(),
                mes = mes,
                lineas =
                    listOf(
                        LineaDePlan(
                            id = ids.nuevaLineaId(),
                            nombre = pasos.nombreDelIngreso,
                            tipo = TipoDeLinea.INGRESO,
                            montoPlanificado = ingreso,
                            cuentaId = idDeLaCuenta,
                            // Quien dice que su mes empieza el 25 es porque
                            // cobra el 25.
                            diaDelMes = pasos.primerDiaDelMes,
                        ),
                    ),
            ),
        )
    }
}
