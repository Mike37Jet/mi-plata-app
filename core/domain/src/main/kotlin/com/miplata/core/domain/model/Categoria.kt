package com.miplata.core.domain.model

/**
 * Una etiqueta para clasificar movimientos: "Comida", "Transporte", "Sueldo".
 *
 * La jerarquia es de **dos niveles como mucho**: una categoria raiz
 * ([padreId] nulo) y sus subcategorias. Sin arboles arbitrarios, porque en una
 * app de finanzas personales el tercer nivel no aporta y en cambio complica cada
 * informe, cada selector y cada consulta.
 *
 * Esta clase solo puede garantizar que una categoria no sea su propia madre; que
 * no haya nietas se comprueba al ensamblar el arbol, que es donde se conoce el
 * conjunto entero.
 */
data class Categoria(
    val id: CategoriaId,
    val nombre: String,
    val padreId: CategoriaId? = null,
    val icono: String = "",
    /** Color elegido por el usuario, en formato ARGB. */
    val color: Int = 0,
) {
    val esRaiz: Boolean get() = padreId == null

    init {
        require(nombre.isNotBlank()) { "Una categoria necesita un nombre" }
        require(padreId != id) { "Una categoria no puede ser su propia madre" }
    }
}
