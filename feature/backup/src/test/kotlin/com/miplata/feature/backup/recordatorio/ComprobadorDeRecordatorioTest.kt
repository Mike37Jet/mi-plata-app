package com.miplata.feature.backup.recordatorio

import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.FrecuenciaDeRecordatorio
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCategoriaRepository
import com.miplata.core.domain.repository.FakeCierreRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val DIA = 24L * 60 * 60 * 1000
private const val AHORA = 1_790_000_000_000L

private class RegistroEnMemoria : RegistroDeAvisos {
    var ultimo: Long? = null

    override fun ultimoAvisoEnMillis() = ultimo

    override fun anotarAviso(enMillis: Long) {
        ultimo = enMillis
    }
}

/** Un notificador que recuerda lo que avisaria, y que puede estar "apagado". */
private class NotificadorDePrueba : NotificadorDeRecordatorio {
    val avisos = mutableListOf<Int?>()
    var activas = true

    override fun avisar(diasSinCopia: Int?): Boolean {
        if (!activas) return false
        avisos += diasSinCopia
        return true
    }
}

class ComprobadorDeRecordatorioTest {
    private val cuentas = FakeCuentaRepository()
    private val categorias = FakeCategoriaRepository()
    private val ajustes = FakeAjustesRepository()
    private val registro = RegistroEnMemoria()
    private val notificador = NotificadorDePrueba()

    private val comprobador =
        ComprobadorDeRecordatorio(
            ajustes = ajustes,
            recolector =
                RecolectorDeDatos(
                    cuentas,
                    categorias,
                    FakeTransaccionRepository(),
                    FakePlanRepository(),
                    ajustes,
                    FakeCierreRepository(),
                ),
            registro = registro,
            notificador = notificador,
        )

    private suspend fun conUnaCuenta() =
        cuentas.guardar(
            Cuenta(
                id = CuentaId("banco"),
                nombre = "Banco",
                tipo = TipoDeCuenta.BANCARIA,
                saldoInicial = Money.ZERO,
                moneda = Moneda("EUR"),
            ),
        )

    @Test
    fun `con una copia vencida avisa con los dias y lo apunta`() =
        runTest {
            conUnaCuenta()
            ajustes.guardar(Ajustes(ultimoBackupEnMillis = AHORA - 34 * DIA))

            comprobador.comprobar(AHORA)

            notificador.avisos shouldBe listOf(34)
            registro.ultimo shouldBe AHORA
        }

    @Test
    fun `con una copia reciente no avisa`() =
        runTest {
            conUnaCuenta()
            ajustes.guardar(Ajustes(ultimoBackupEnMillis = AHORA - 2 * DIA))

            comprobador.comprobar(AHORA)

            notificador.avisos shouldBe emptyList()
        }

    // Las categorias vienen sembradas de serie: una app con solo esas no tiene
    // nada del usuario que proteger.
    @Test
    fun `las categorias de serie no cuentan como datos que proteger`() =
        runTest {
            categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida"))

            comprobador.comprobar(AHORA)

            notificador.avisos shouldBe emptyList()
        }

    @Test
    fun `con datos y ninguna copia avisa`() =
        runTest {
            conUnaCuenta()

            comprobador.comprobar(AHORA)

            notificador.avisos shouldBe listOf(null)
        }

    // Si el aviso no llego a enseñarse -notificaciones apagadas-, no se apunta:
    // sigue pendiente y saldra en cuanto se activen.
    @Test
    fun `si no se pudo enseñar el aviso no se apunta`() =
        runTest {
            conUnaCuenta()
            notificador.activas = false

            comprobador.comprobar(AHORA)

            registro.ultimo.shouldBeNull()
        }

    @Test
    fun `respeta la frecuencia elegida`() =
        runTest {
            conUnaCuenta()
            ajustes.guardar(
                Ajustes(
                    ultimoBackupEnMillis = AHORA - 8 * DIA,
                    frecuenciaDeRecordatorio = FrecuenciaDeRecordatorio.SEMANAL,
                ),
            )

            comprobador.comprobar(AHORA)

            notificador.avisos shouldBe listOf(8)
        }

    @Test
    fun `no avisa dos dias seguidos`() =
        runTest {
            conUnaCuenta()

            comprobador.comprobar(AHORA)
            comprobador.comprobar(AHORA + DIA)

            notificador.avisos shouldBe listOf(null)
        }
}
