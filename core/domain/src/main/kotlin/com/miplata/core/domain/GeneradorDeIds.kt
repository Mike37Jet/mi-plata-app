package com.miplata.core.domain

import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.TransaccionId

/**
 * Fabrica los identificadores de las entidades nuevas.
 *
 * Es una interfaz y no una funcion que llame a `UUID.randomUUID()` porque el
 * dominio no puede depender de una fuente de azar: un use case que genera ids
 * aleatorios por dentro deja de ser una funcion pura y sus tests se vuelven
 * imposibles de escribir sin trucos. Con esto, el test inyecta un generador que
 * produce `linea-1`, `linea-2`... y puede afirmar exactamente que salio.
 *
 * La implementacion de verdad vive en la capa de datos (docs/01).
 */
interface GeneradorDeIds {
    fun nuevoPlanId(): PlanId

    fun nuevaLineaId(): LineaId

    fun nuevaTransaccionId(): TransaccionId

    fun nuevaCuentaId(): CuentaId

    fun nuevaCategoriaId(): CategoriaId
}
