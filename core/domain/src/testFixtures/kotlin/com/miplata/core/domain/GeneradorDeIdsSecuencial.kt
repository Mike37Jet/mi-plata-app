package com.miplata.core.domain

import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.TransaccionId

/**
 * Genera identificadores predecibles: `plan-1`, `linea-1`, `linea-2`...
 *
 * Un fake escrito a mano, no un mock: se lee de un vistazo y deja que los tests
 * afirmen exactamente que identificador salio, cosa imposible con UUIDs.
 */
class GeneradorDeIdsSecuencial : GeneradorDeIds {
    private var planes = 0
    private var lineas = 0
    private var transacciones = 0
    private var cuentas = 0
    private var categorias = 0

    override fun nuevoPlanId() = PlanId("plan-${++planes}")

    override fun nuevaLineaId() = LineaId("linea-${++lineas}")

    override fun nuevaTransaccionId() = TransaccionId("transaccion-${++transacciones}")

    override fun nuevaCuentaId() = CuentaId("cuenta-${++cuentas}")

    override fun nuevaCategoriaId() = CategoriaId("categoria-${++categorias}")
}
