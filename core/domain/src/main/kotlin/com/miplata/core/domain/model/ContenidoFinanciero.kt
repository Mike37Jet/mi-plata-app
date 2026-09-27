package com.miplata.core.domain.model

/**
 * Todo lo que el usuario tiene apuntado, de una vez.
 *
 * Existe para las operaciones que tratan los datos como un bloque -restaurar una
 * copia, deshacer esa restauracion- y que no pueden hacerse cuenta a cuenta:
 * o se sustituye todo, o no se sustituye nada.
 *
 * No incluye los ajustes. Viven en otro almacen (DataStore, no la base), asi que
 * no pueden entrar en la misma transaccion, y quien restaura los trata aparte.
 */
data class ContenidoFinanciero(
    val cuentas: List<Cuenta> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val transacciones: List<Transaccion> = emptyList(),
    val planes: List<PlanMensual> = emptyList(),
    val cierres: List<CierreDeMes> = emptyList(),
)
