package com.miplata.core.data.repository

import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.TransaccionId
import java.util.UUID

/**
 * Identificadores basados en UUID.
 *
 * Se eligen UUID y no enteros autoincrementales porque el backup se exporta y se
 * reimporta en otro telefono (docs/05): para poder fusionar datos algun dia, los
 * identificadores tienen que ser estables y no depender de la secuencia de una
 * base de datos concreta.
 */
class GeneradorDeIdsUuid : GeneradorDeIds {
    override fun nuevoPlanId() = PlanId(nuevo())

    override fun nuevaLineaId() = LineaId(nuevo())

    override fun nuevaTransaccionId() = TransaccionId(nuevo())

    override fun nuevaCuentaId() = CuentaId(nuevo())

    override fun nuevaCategoriaId() = CategoriaId(nuevo())

    private fun nuevo() = UUID.randomUUID().toString()
}
