package com.miplata.core.domain

import com.miplata.core.domain.model.FrecuenciaDeRecordatorio
import com.miplata.core.domain.model.FrecuenciaDeRecordatorio.MENSUAL
import com.miplata.core.domain.model.FrecuenciaDeRecordatorio.NUNCA
import com.miplata.core.domain.model.FrecuenciaDeRecordatorio.SEMANAL
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val DIA = 24L * 60 * 60 * 1000
private const val AHORA = 1_790_000_000_000L

private fun haceDias(dias: Int) = AHORA - dias * DIA

private fun decidir(
    frecuencia: FrecuenciaDeRecordatorio = MENSUAL,
    hayDatos: Boolean = true,
    ultimaCopia: Long? = null,
    ultimoAviso: Long? = null,
) = PoliticaDeRecordatorio.decidir(frecuencia, hayDatos, ultimaCopia, ultimoAviso, AHORA)

class PoliticaDeRecordatorioTest {
    @Test
    fun `con una copia reciente no se avisa`() {
        decidir(ultimaCopia = haceDias(5)) shouldBe DecisionDeRecordatorio.NoAvisar
    }

    @Test
    fun `con una copia mas vieja que la frecuencia se avisa con los dias`() {
        decidir(ultimaCopia = haceDias(34)) shouldBe DecisionDeRecordatorio.Avisar(diasSinCopia = 34)
    }

    // El limite se cuenta en dias completos: a los 30 justos ya esta vencida.
    @Test
    fun `justo en el limite ya se avisa`() {
        decidir(ultimaCopia = haceDias(30)) shouldBe DecisionDeRecordatorio.Avisar(30)
        decidir(ultimaCopia = haceDias(29)) shouldBe DecisionDeRecordatorio.NoAvisar
    }

    @Test
    fun `la frecuencia semanal vence antes que la mensual`() {
        decidir(frecuencia = SEMANAL, ultimaCopia = haceDias(8)) shouldBe DecisionDeRecordatorio.Avisar(8)
        decidir(frecuencia = MENSUAL, ultimaCopia = haceDias(8)) shouldBe DecisionDeRecordatorio.NoAvisar
    }

    @Test
    fun `quien eligio no recibir recordatorios no los recibe nunca`() {
        decidir(frecuencia = NUNCA, ultimaCopia = null) shouldBe DecisionDeRecordatorio.NoAvisar
        decidir(frecuencia = NUNCA, ultimaCopia = haceDias(400)) shouldBe DecisionDeRecordatorio.NoAvisar
    }

    // Una app recien instalada no tiene nada que proteger: pedir una copia de
    // nada enseña a ignorar el aviso.
    @Test
    fun `sin datos que proteger no se avisa`() {
        decidir(hayDatos = false, ultimaCopia = null) shouldBe DecisionDeRecordatorio.NoAvisar
    }

    @Test
    fun `con datos y ninguna copia se avisa sin dias`() {
        decidir(ultimaCopia = null) shouldBe DecisionDeRecordatorio.Avisar(diasSinCopia = null)
    }

    // Un aviso diario acaba silenciado para siempre.
    @Test
    fun `un aviso reciente no se repite`() {
        decidir(ultimaCopia = haceDias(40), ultimoAviso = haceDias(1)) shouldBe DecisionDeRecordatorio.NoAvisar
    }

    // Pero uno que no se repite nunca, se olvida.
    @Test
    fun `pasados unos dias sin atenderlo se vuelve a avisar`() {
        decidir(
            ultimaCopia = haceDias(40),
            ultimoAviso = haceDias(PoliticaDeRecordatorio.DIAS_ENTRE_AVISOS),
        ) shouldBe DecisionDeRecordatorio.Avisar(40)
    }

    // La politica no comprueba si el ultimo aviso fue antes de la ultima copia,
    // porque no le hace falta: si la copia vencio, un aviso anterior a ella es
    // aun mas viejo que la frecuencia y nunca frena el siguiente. Eso solo es
    // verdad mientras ninguna frecuencia sea mas corta que la pausa entre avisos.
    // Si alguien añade una frecuencia diaria, este test falla y obliga a
    // repensar la regla en vez de dejarla rota en silencio.
    @Test
    fun `ninguna frecuencia es mas corta que la pausa entre avisos`() {
        FrecuenciaDeRecordatorio.entries.mapNotNull { it.dias }.forEach { dias ->
            (dias >= PoliticaDeRecordatorio.DIAS_ENTRE_AVISOS) shouldBe true
        }
    }

    @Test
    fun `tras una copia nueva, el siguiente vencimiento avisa en cuanto llega`() {
        decidir(
            frecuencia = SEMANAL,
            ultimaCopia = haceDias(7),
            ultimoAviso = haceDias(8),
        ) shouldBe DecisionDeRecordatorio.Avisar(7)
    }

    @Test
    fun `sin copias el aviso tambien se espacia`() {
        decidir(ultimaCopia = null, ultimoAviso = haceDias(1)) shouldBe DecisionDeRecordatorio.NoAvisar
        decidir(ultimaCopia = null, ultimoAviso = haceDias(3)) shouldBe DecisionDeRecordatorio.Avisar(null)
    }

    // Un reloj que se atrasa -cambio de zona, ajuste manual- no puede dar dias
    // negativos ni avisos absurdos.
    @Test
    fun `una copia fechada en el futuro cuenta como reciente`() {
        decidir(ultimaCopia = AHORA + 5 * DIA) shouldBe DecisionDeRecordatorio.NoAvisar
    }

    @Test
    fun `las frecuencias declaran sus dias`() {
        SEMANAL.dias shouldBe 7
        MENSUAL.dias shouldBe 30
        NUNCA.dias shouldBe null
    }
}
