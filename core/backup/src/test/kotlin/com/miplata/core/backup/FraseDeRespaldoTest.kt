package com.miplata.core.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class FraseDeRespaldoTest {
    @Test
    fun `una frase de varias palabras es valida`() {
        FraseDeRespaldo.de("correcto caballo bateria grapa")
    }

    @Test
    fun `una frase demasiado corta se rechaza`() {
        shouldThrow<IllegalArgumentException> { FraseDeRespaldo.de("corta") }
    }

    @Test
    fun `justo en el minimo se acepta`() {
        FraseDeRespaldo.de("a".repeat(FraseDeRespaldo.LONGITUD_MINIMA))
    }

    @Test
    fun `solo espacios no es una frase`() {
        shouldThrow<IllegalArgumentException> { FraseDeRespaldo.de(" ".repeat(20)) }
    }

    // Un log, un informe de errores o un toString accidental no pueden filtrarla.
    @Test
    fun `nunca aparece al imprimirla`() {
        FraseDeRespaldo.de("correcto caballo bateria grapa").toString() shouldNotContain "caballo"
    }

    @Test
    fun `olvidarla borra los caracteres de memoria`() {
        val frase = FraseDeRespaldo.de("correcto caballo bateria grapa")

        frase.olvidar()

        frase.caracteres.all { it == '\u0000' } shouldBe true
    }

    // Guarda su propia copia: borrar el array con el que se creo no la estropea,
    // y olvidarla no toca el array del que la llamo.
    @Test
    fun `guarda una copia propia de lo tecleado`() {
        val tecleado = "correcto caballo bateria grapa".toCharArray()
        val frase = FraseDeRespaldo(tecleado)

        tecleado.fill('x')

        String(frase.caracteres) shouldBe "correcto caballo bateria grapa"
    }
}
