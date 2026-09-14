package com.miplata.core.designsystem.formato

import com.miplata.core.domain.model.Money
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.Locale

class AnalizadorDeDineroTest {
    private val enEspanol = AnalizadorDeDinero(Locale.forLanguageTag("es-ES"))
    private val enIngles = AnalizadorDeDinero(Locale.US)

    @Test
    fun `lee una cantidad con decimales`() {
        enEspanol.parsear("1234,56") shouldBe Money.deCentavos(123_456)
        enIngles.parsear("1234.56") shouldBe Money.deCentavos(123_456)
    }

    @Test
    fun `lee una cantidad entera`() {
        enEspanol.parsear("450") shouldBe Money.deUnidades(450)
    }

    // El motivo de mirar el idioma en vez de adivinar por la forma del texto: el
    // MISMO texto significa cosas distintas.
    @Test
    fun `el mismo texto se lee distinto segun el idioma`() {
        enEspanol.parsear("1.234") shouldBe Money.deUnidades(1234)
        enIngles.parsear("1.234") shouldBe Money.deCentavos(123)
    }

    @Test
    fun `ignora los separadores de millares`() {
        enEspanol.parsear("1.234.567,89") shouldBe Money.deCentavos(123_456_789)
        enIngles.parsear("1,234,567.89") shouldBe Money.deCentavos(123_456_789)
    }

    @Test
    fun `acepta negativos`() {
        enEspanol.parsear("-250,50") shouldBe Money.deCentavos(-25_050)
    }

    @Test
    fun `un solo decimal son decimas, no centesimas`() {
        enEspanol.parsear("12,5") shouldBe Money.deCentavos(1250)
    }

    // Mas de dos decimales se redondean como en el resto del proyecto: HALF_EVEN.
    @Test
    fun `mas de dos decimales se redondean`() {
        enEspanol.parsear("12,567") shouldBe Money.deCentavos(1257)
        enEspanol.parsear("12,125") shouldBe Money.deCentavos(1212)
    }

    // Mientras se teclea, el texto pasa por estados que no son cantidades. Eso es
    // normal: se devuelve null, no se lanza.
    @Test
    fun `devuelve nulo cuando no es una cantidad`() {
        listOf("", "   ", "abc", "12a", "1,2,3", "--5").forEach {
            enEspanol.parsear(it).shouldBeNull()
        }
    }

    // Los separadores de millares son decoracion: se descartan esten donde esten.
    // "1.2.3,4" en espanol no es un agrupamiento correcto, pero un lector humano
    // tampoco vería ahi otra cosa que 123,4. Ser estricto con la posicion de los
    // puntos solo serviria para rechazar lo que el usuario ya esta viendo bien.
    @Test
    fun `los separadores de millares mal puestos no invalidan la cantidad`() {
        enEspanol.parsear("1.2.3,4") shouldBe Money.deCentavos(12_340)
    }

    @Test
    fun `tolera los estados intermedios de teclear`() {
        enEspanol.parsear("12,") shouldBe Money.deUnidades(12)
        enEspanol.parsear("-").shouldBeNull()
    }

    @Test
    fun `ignora los espacios, incluido el no separable`() {
        enEspanol.parsear(" 1 234,56 ") shouldBe Money.deCentavos(123_456)
        enEspanol.parsear("1 234,56") shouldBe Money.deCentavos(123_456)
    }
}
