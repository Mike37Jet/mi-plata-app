package com.miplata.core.designsystem.formato

import com.miplata.core.domain.model.Money
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class FormateadorDeDineroTest {
    private fun formateador(
        locale: Locale,
        moneda: String,
    ) = FormateadorDeDinero(
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(moneda)
        },
    )

    private val enDolares = formateador(Locale.US, "USD")

    @Test
    fun `pone los decimales y el separador de miles`() {
        enDolares.formatear(Money.deCentavos(148_075)) shouldBe "$1,480.75"
    }

    @Test
    fun `los centavos sueltos no se pierden`() {
        enDolares.formatear(Money.deCentavos(5)) shouldBe "$0.05"
        enDolares.formatear(Money.ZERO) shouldBe "$0.00"
    }

    @Test
    fun `un negativo se ve como negativo`() {
        enDolares.formatear(Money.deUnidades(-520)) shouldBe "-$520.00"
    }

    // El signo delante solo cuando suma: un negativo ya trae el suyo.
    @Test
    fun `el signo explicito solo se anade a lo positivo`() {
        enDolares.formatearConSigno(Money.deUnidades(2000)) shouldBe "+$2,000.00"
        enDolares.formatearConSigno(Money.deUnidades(-520)) shouldBe "-$520.00"
        enDolares.formatearConSigno(Money.ZERO) shouldBe "$0.00"
    }

    // La razon de que el formateo no viva en el dominio: depende del idioma y de
    // la moneda, y ninguna de las dos cosas tiene que ver con calcular un saldo.
    //
    // El espacio antes del simbolo es NO SEPARABLE (U+00A0), no uno normal: es lo
    // correcto, evita que el simbolo de moneda caiga solo a la linea siguiente.
    // Escribirlo como espacio normal en una asercion es un error facil de cometer
    // y dificil de ver leyendo el codigo.
    @Test
    fun `el mismo importe se escribe segun el idioma y la moneda`() {
        val cantidad = Money.deCentavos(148_075)

        formateador(Locale.US, "USD").formatear(cantidad) shouldBe "$1,480.75"
        formateador(Locale.forLanguageTag("es-ES"), "EUR").formatear(cantidad) shouldBe
            "1.480,75\u00A0€"
    }

    @Test
    fun `cifras muy grandes siguen siendo exactas`() {
        enDolares.formatear(Money.deUnidades(50_000_000)) shouldBe "$50,000,000.00"
    }
}
