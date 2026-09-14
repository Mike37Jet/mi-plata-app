package com.miplata.core.domain.model

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Ajustes")
class AjustesTest {
    @Test
    fun `por defecto el mes empieza el dia uno`() {
        val ajustes = Ajustes()

        ajustes.primerDiaDelMesFinanciero shouldBe 1
        ajustes.tema shouldBe Tema.SEGUN_EL_SISTEMA
        ajustes.ultimoBackupEnMillis shouldBe null
    }

    @Test
    fun `rechaza dias imposibles`() {
        assertThrows<IllegalArgumentException> { Ajustes(primerDiaDelMesFinanciero = 0) }
        assertThrows<IllegalArgumentException> { Ajustes(primerDiaDelMesFinanciero = 32) }
    }

    // La razon de que este campo sea del dominio y no de presentacion: decide
    // que movimientos entran en cada resumen.
    @Test
    fun `construye el periodo segun el dia configurado`() {
        val quienCobraEl25 = Ajustes(primerDiaDelMesFinanciero = 25)

        val marzo = quienCobraEl25.periodoDe(Mes.de(2026, 3))

        marzo.inicio shouldBe LocalDate(2026, 3, 25)
        marzo.fin shouldBe LocalDate(2026, 4, 24)
    }

    @Test
    fun `con el dia uno el periodo es el mes natural`() {
        val marzo = Ajustes().periodoDe(Mes.de(2026, 3))

        marzo.inicio shouldBe LocalDate(2026, 3, 1)
        marzo.fin shouldBe LocalDate(2026, 3, 31)
    }
}
