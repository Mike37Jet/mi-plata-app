package com.miplata.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test
import kotlin.math.sqrt

/**
 * Que los espacios y los tamaños de letra sigan saliendo de la escala aurea.
 *
 * Es facil que alguien "ajuste" un padding a 16 porque se ve mejor en su
 * pantalla, y la escala deja de ser una escala sin que nadie lo decida. Aqui se
 * ve.
 */
class EscalaAureaTest {
    /** Redondear a dp enteros aleja cada paso de φ, pero nunca mas que esto. */
    private val tolerancia = 0.05

    private fun proporciones(valores: List<Double>) = valores.zipWithNext { a, b -> b / a }

    @Test
    fun `cada espacio es el anterior por phi`() {
        val espacios = listOf(Espacio.xs, Espacio.s, Espacio.m, Espacio.l, Espacio.xl, Espacio.xxl).map(Dp::value)

        proporciones(espacios.map(Float::toDouble)).forEach { it shouldBe (PHI plusOrMinus tolerancia) }
    }

    @Test
    fun `los tamaños que marcan jerarquia van de phi en phi`() {
        val t = TipografiaDeLaApp
        val jerarquia =
            listOf(
                t.bodySmall,
                t.titleLarge,
                t.headlineLarge,
                t.displayLarge,
            ).map { it.fontSize.value.toDouble() }

        jerarquia shouldBe listOf(13.0, 21.0, 34.0, 55.0)
        proporciones(jerarquia).forEach { it shouldBe (PHI plusOrMinus tolerancia) }
    }

    @Test
    fun `los intermedios van de raiz de phi en raiz de phi`() {
        val t = TipografiaDeLaApp
        val escala =
            listOf(
                t.bodySmall,
                t.bodyLarge,
                t.titleLarge,
                t.headlineMedium,
                t.headlineLarge,
                t.displayMedium,
                t.displayLarge,
            ).map { it.fontSize.value.toDouble() }

        proporciones(escala).forEach { it shouldBe (sqrt(PHI) plusOrMinus tolerancia) }
    }

    @Test
    fun `la cifra principal es el tope de la escala`() {
        EstilosDeDinero.destacado.fontSize shouldBe TipografiaDeLaApp.displayLarge.fontSize
    }
}
