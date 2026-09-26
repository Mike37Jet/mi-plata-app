package com.miplata.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** La proporcion aurea. */
internal const val PHI = 1.618

/**
 * Los unicos espacios de la app: cada uno es el anterior por [PHI] (8 · φⁿ).
 *
 * Lo que se percibe como armonia no es el numero 1.618 en si: es que todos
 * los espacios salgan de una misma escala, y que el salto entre dos niveles
 * sea lo bastante grande para leerse como jerarquia. Con una escala de 4 en 4
 * (12, 16, 20) los niveles se confunden; con φ, cada salto se nota.
 *
 * Un padding que no sale de aqui es un padding que alguien se invento.
 */
object Espacio {
    /** Entre un texto y el que lo acompaña: un importe y su detalle. */
    val xs = 8.dp

    /** Relleno vertical dentro de una fila; separacion entre elementos hermanos. */
    val s = 13.dp

    /** Margenes laterales de la pantalla y relleno horizontal de una fila. */
    val m = 21.dp

    /** Entre secciones. */
    val l = 34.dp

    /** Altura minima de una fila, y aire sobre la cifra principal. */
    val xl = 55.dp

    val xxl = 89.dp
}

/**
 * Esquinas: 13 para los grupos de una lista, 21 para las hojas que suben desde
 * abajo. Mas redondo cuanto mas grande y mas "encima" esta la pieza.
 */
internal val FormasDeLaApp =
    Shapes(
        extraSmall = RoundedCornerShape(Espacio.xs),
        small = RoundedCornerShape(Espacio.xs),
        medium = RoundedCornerShape(Espacio.s),
        large = RoundedCornerShape(Espacio.m),
        extraLarge = RoundedCornerShape(Espacio.m),
    )
