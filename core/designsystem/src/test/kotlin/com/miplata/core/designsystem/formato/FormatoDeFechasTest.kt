package com.miplata.core.designsystem.formato

import com.miplata.core.domain.model.Mes
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale

class FormatoDeFechasTest {
    @Test
    fun `el mes se dice con su nombre y su año`() {
        nombreDelMes(Mes.de(2026, 3)) shouldBe "Marzo 2026"
        nombreDelMes(Mes.de(2025, 12)) shouldBe "Diciembre 2025"
    }

    @Test
    fun `el dia se dice con su nombre, su numero y su mes`() {
        fechaLarga(LocalDate(2026, 3, 26)) shouldBe "Jueves, 26 de marzo"
        fechaLarga(LocalDate(2026, 3, 1)) shouldBe "Domingo, 1 de marzo"
    }

    @Test
    fun `lo reciente se dice como hablando`() {
        val hoy = LocalDate(2026, 3, 26)

        fechaRelativa(hoy, hoy) shouldBe "Hoy"
        fechaRelativa(LocalDate(2026, 3, 25), hoy) shouldBe "Ayer"
        fechaRelativa(LocalDate(2026, 3, 24), hoy) shouldBe "Martes, 24 de marzo"
    }

    /** El primero de mes, "ayer" es el ultimo dia del mes anterior. */
    @Test
    fun `ayer cruza el cambio de mes`() {
        fechaRelativa(LocalDate(2026, 2, 28), LocalDate(2026, 3, 1)) shouldBe "Ayer"
    }

    /** Con el telefono en ingles, las fechas siguen en el idioma de la app. */
    @Test
    fun `no depende del idioma del telefono`() {
        val antes = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)

            nombreDelMes(Mes.de(2026, 3)) shouldBe "Marzo 2026"
            fechaLarga(LocalDate(2026, 3, 26)) shouldBe "Jueves, 26 de marzo"
        } finally {
            Locale.setDefault(antes)
        }
    }
}
