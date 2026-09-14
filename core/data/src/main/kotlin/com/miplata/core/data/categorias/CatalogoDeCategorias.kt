package com.miplata.core.data.categorias

import android.content.Context
import com.miplata.core.data.R
import com.miplata.core.domain.usecase.CategoriaSemilla

/**
 * El juego de categorias con el que arranca la app.
 *
 * Los nombres salen de `strings.xml`; aqui solo estan la estructura y el color.
 * Las hijas heredan el color de su madre, asi que un informe por categorias se
 * lee de un vistazo.
 *
 * La lista es corta a proposito. Una taxonomia de cuarenta categorias parece
 * completa y en la practica hace que el usuario dude en cada gasto y acabe
 * usando "Otros" para todo. Lo que falte se anade; lo que sobra, estorba.
 */
object CatalogoDeCategorias {
    fun porDefecto(context: Context): List<CategoriaSemilla> =
        listOf(
            raiz(context, R.string.categoria_sueldo, VERDE),
            raiz(context, R.string.categoria_otros_ingresos, VERDE_CLARO),
            raiz(
                context,
                R.string.categoria_vivienda,
                AZUL,
                R.string.categoria_arriendo,
                R.string.categoria_servicios,
                R.string.categoria_internet,
            ),
            raiz(
                context,
                R.string.categoria_alimentacion,
                NARANJA,
                R.string.categoria_mercado,
                R.string.categoria_restaurantes,
            ),
            raiz(
                context,
                R.string.categoria_transporte,
                MORADO,
                R.string.categoria_combustible,
                R.string.categoria_transporte_publico,
            ),
            raiz(context, R.string.categoria_salud, ROJO),
            raiz(context, R.string.categoria_educacion, INDIGO),
            raiz(context, R.string.categoria_ocio, ROSA),
            raiz(context, R.string.categoria_suscripciones, CIAN),
            raiz(context, R.string.categoria_ropa, AMBAR),
            raiz(context, R.string.categoria_ahorro, TEAL),
            raiz(context, R.string.categoria_otros_gastos, GRIS),
        )

    private fun raiz(
        context: Context,
        nombre: Int,
        color: Int,
        vararg hijas: Int,
    ) = CategoriaSemilla(
        nombre = context.getString(nombre),
        color = color,
        hijas = hijas.map { CategoriaSemilla(nombre = context.getString(it), color = color) },
    )

    private const val VERDE = 0xFF2E7D32.toInt()
    private const val VERDE_CLARO = 0xFF66BB6A.toInt()
    private const val AZUL = 0xFF1565C0.toInt()
    private const val NARANJA = 0xFFEF6C00.toInt()
    private const val MORADO = 0xFF6A1B9A.toInt()
    private const val ROJO = 0xFFC62828.toInt()
    private const val INDIGO = 0xFF283593.toInt()
    private const val ROSA = 0xFFAD1457.toInt()
    private const val CIAN = 0xFF00838F.toInt()
    private const val AMBAR = 0xFFFF8F00.toInt()
    private const val TEAL = 0xFF00695C.toInt()
    private const val GRIS = 0xFF546E7A.toInt()
}
