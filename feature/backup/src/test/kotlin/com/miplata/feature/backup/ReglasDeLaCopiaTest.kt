package com.miplata.feature.backup

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test

private const val FRASE_BUENA = "correcto caballo bateria grapa"
private val MADRID = TimeZone.of("Europe/Madrid")

class ReglasDeLaCopiaTest {
    @Test
    fun `una frase larga, repetida y con el riesgo asumido se puede usar`() {
        problemaCon(FRASE_BUENA, FRASE_BUENA, riesgoAsumido = true).shouldBeNull()
    }

    @Test
    fun `una frase corta es el primer problema`() {
        problemaCon("corta", "corta", riesgoAsumido = true) shouldBe ProblemaConLaFrase.DEMASIADO_CORTA
    }

    // Un error al teclearla no se nota hasta el dia de restaurar, y ese dia ya
    // no tiene arreglo.
    @Test
    fun `si la repeticion no coincide no se puede exportar`() {
        problemaCon(FRASE_BUENA, "$FRASE_BUENA!", riesgoAsumido = true) shouldBe ProblemaConLaFrase.NO_COINCIDEN
    }

    @Test
    fun `sin asumir el riesgo no se puede exportar`() {
        problemaCon(FRASE_BUENA, FRASE_BUENA, riesgoAsumido = false) shouldBe
            ProblemaConLaFrase.SIN_ASUMIR_EL_RIESGO
    }

    // Se informa de un problema cada vez, en el orden en que conviene
    // arreglarlos: no tiene sentido pedir que coincidan dos frases demasiado
    // cortas.
    @Test
    fun `con varios problemas se dice primero el de la longitud`() {
        problemaCon("corta", "otra", riesgoAsumido = false) shouldBe ProblemaConLaFrase.DEMASIADO_CORTA
    }

    @Test
    fun `solo espacios no cuentan como frase aunque sean muchos`() {
        val espacios = " ".repeat(20)
        problemaCon(espacios, espacios, riesgoAsumido = true) shouldBe ProblemaConLaFrase.DEMASIADO_CORTA
    }

    @Test
    fun `el nombre lleva fecha y hora ordenables y la extension propia`() {
        val instante = LocalDateTime(2026, 9, 26, 10, 42).toInstant(MADRID)

        nombreDelArchivo(instante, MADRID) shouldBe "miplata-backup-2026-09-26-1042.mpb"
    }

    // Con ceros a la izquierda, ordenar por nombre sigue siendo ordenar por fecha.
    @Test
    fun `las horas de un digito llevan cero delante`() {
        val instante = LocalDateTime(2026, 1, 5, 7, 3).toInstant(MADRID)

        nombreDelArchivo(instante, MADRID) shouldBe "miplata-backup-2026-01-05-0703.mpb"
    }

    // La hora es la del reloj del usuario, no la de Greenwich: una copia hecha a
    // las 00:30 en Madrid no puede llamarse como si fuera del dia anterior.
    @Test
    fun `el nombre usa la zona horaria del usuario`() {
        val instante = LocalDateTime(2026, 9, 26, 0, 30).toInstant(MADRID)

        nombreDelArchivo(instante, MADRID) shouldBe "miplata-backup-2026-09-26-0030.mpb"
        nombreDelArchivo(instante, TimeZone.UTC) shouldBe "miplata-backup-2026-09-25-2230.mpb"
    }

    @Test
    fun `la fecha de la ultima copia se lee con hora`() {
        val millis = LocalDateTime(2026, 9, 26, 10, 42).toInstant(MADRID).toEpochMilliseconds()

        fechaDeLaCopia(millis, MADRID) shouldBe "2026-09-26 10:42"
    }
}
