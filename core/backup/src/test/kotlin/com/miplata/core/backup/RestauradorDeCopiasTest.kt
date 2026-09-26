package com.miplata.core.backup

import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCategoriaRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeRepositorioDeRestauracion
import com.miplata.core.domain.repository.FakeTransaccionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

private val PROCEDENCIA = Procedencia(versionDelEsquema = 1, versionDeLaApp = "0.1.0", dispositivo = "Pixel")
private const val AHORA = 1_790_000_000_000L
private const val CREADA_EN = 1_785_000_000_000L

private fun cuenta(
    id: String,
    nombre: String,
) = Cuenta(
    id = CuentaId(id),
    nombre = nombre,
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = Money.deUnidades(100),
    moneda = Moneda("EUR"),
)

/** Ajustes que no dejan escribir: el caso rarisimo de RestauracionIncompleta. */
private class AjustesQueFallan(
    private val real: FakeAjustesRepository,
) : AjustesRepository by real {
    var fallar = false

    override suspend fun guardar(ajustes: Ajustes) {
        if (fallar) throw IOException("Disco lleno")
        real.guardar(ajustes)
    }

    override fun observar(): Flow<Ajustes> = real.observar()
}

class RestauradorDeCopiasTest {
    private val cuentas = FakeCuentaRepository()
    private val categorias = FakeCategoriaRepository()
    private val transacciones = FakeTransaccionRepository()
    private val planes = FakePlanRepository()
    private val ajustesReales = FakeAjustesRepository(Ajustes(moneda = Moneda("USD"), primerDiaDelMesFinanciero = 1))
    private val ajustes = AjustesQueFallan(ajustesReales)
    private val repositorio = FakeRepositorioDeRestauracion(cuentas, categorias, transacciones, planes)
    private val almacen = AlmacenEnMemoria()
    private val archivo = archivoRapido()
    private val recolector = RecolectorDeDatos(cuentas, categorias, transacciones, planes, ajustes)

    private val restaurador = RestauradorDeCopias(recolector, archivo, repositorio, ajustes, almacen)

    /** Una copia con una cuenta y ajustes en euros, empezando el 25. */
    private fun copia(): BackupLeido {
        val datos =
            DatosDelBackup(
                ajustes =
                    AjustesDto(
                        moneda = "EUR",
                        primerDiaDelMesFinanciero = 25,
                        tema = "OSCURO",
                        ultimoBackupEnMillis = 1_000L,
                    ),
                cuentas =
                    listOf(
                        CuentaDto(
                            id = "de-la-copia",
                            nombre = "Cuenta de la copia",
                            tipo = "BANCARIA",
                            saldoInicialEnCentavos = 50_000,
                            moneda = "EUR",
                        ),
                    ),
            )
        val bytes =
            archivo.escribirABytes(
                ContenidoDelBackup(datos, 1, "0.1.0", creadoEnMillis = CREADA_EN, dispositivo = "Otro movil"),
                FRASE,
            )
        return archivo.leer(ByteArrayInputStream(bytes), FRASE)
    }

    private suspend fun conDatosActuales() = cuentas.guardar(cuenta("actual", "Cuenta de ahora"))

    @Test
    fun `sustituye los datos y los ajustes por los de la copia`() =
        runTest {
            conDatosActuales()

            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)

            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de la copia")
            ajustes.obtener().moneda shouldBe Moneda("EUR")
            ajustes.obtener().primerDiaDelMesFinanciero shouldBe 25
        }

    // La fecha que trae dentro es la de la copia ANTERIOR: se apunto al exportar,
    // antes de que esta existiera.
    @Test
    fun `la ultima copia pasa a ser la restaurada`() =
        runTest {
            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)

            ajustes.obtener().ultimoBackupEnMillis shouldBe CREADA_EN
        }

    // docs/05: copia de seguridad automatica ANTES de escribir nada.
    @Test
    fun `antes de sustituir guarda lo que habia, y deshacer lo devuelve`() =
        runTest {
            conDatosActuales()
            val ajustesDeAntes = ajustes.obtener()

            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)
            restaurador.deshacer(PROCEDENCIA, AHORA)

            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de ahora")
            ajustes.obtener() shouldBe ajustesDeAntes
        }

    // Sin copia previa no hay forma de deshacer, asi que no se toca nada.
    @Test
    fun `si no puede guardar la copia previa no toca nada`() =
        runTest {
            conDatosActuales()
            almacen.fallarAlGuardar = true

            shouldThrowAny { restaurador.restaurar(copia(), PROCEDENCIA, AHORA) }

            repositorio.vecesReemplazado shouldBe 0
            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de ahora")
            ajustes.obtener().moneda shouldBe Moneda("USD")
        }

    // Si la sustitucion falla, los ajustes tampoco cambian: nada de datos viejos
    // con la moneda de la copia.
    @Test
    fun `si la sustitucion falla los ajustes no cambian`() =
        runTest {
            conDatosActuales()
            repositorio.fallarLaProximaVez = true

            shouldThrowAny { restaurador.restaurar(copia(), PROCEDENCIA, AHORA) }

            ajustes.obtener().moneda shouldBe Moneda("USD")
            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de ahora")
        }

    @Test
    fun `si solo fallan los ajustes se avisa, los datos quedan y se puede deshacer`() =
        runTest {
            conDatosActuales()
            ajustes.fallar = true

            shouldThrow<RestauracionIncompleta> { restaurador.restaurar(copia(), PROCEDENCIA, AHORA) }

            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de la copia")
            restaurador.copiaPreviaDisponible().shouldNotBeNull()
        }

    @Test
    fun `tras restaurar se sabe de cuando es la copia previa`() =
        runTest {
            restaurador.copiaPreviaDisponible().shouldBeNull()

            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)

            restaurador.copiaPreviaDisponible()!!.creadoEnMillis shouldBe AHORA
        }

    // Deshacer tambien sustituye todos los datos: lo de ahora se guarda antes, y
    // deshacer dos veces vuelve a lo restaurado. Sin esto, quien restaura, apunta
    // una semana de gastos y deshace, perderia esa semana sin remedio.
    @Test
    fun `deshacer guarda lo de ahora y se puede volver a ello`() =
        runTest {
            conDatosActuales()
            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)
            cuentas.guardar(cuenta("apuntada-despues", "Apuntada despues de restaurar"))

            restaurador.deshacer(PROCEDENCIA, AHORA)
            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de ahora")

            restaurador.deshacer(PROCEDENCIA, AHORA)
            cuentas
                .observarTodas()
                .first()
                .map { it.nombre }
                .sorted() shouldBe
                listOf("Apuntada despues de restaurar", "Cuenta de la copia")
        }

    // Primera fase: si no se puede guardar lo de ahora, no se toca nada.
    @Test
    fun `si no puede guardar lo de ahora, deshacer no toca nada`() =
        runTest {
            conDatosActuales()
            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)
            almacen.fallarAlPrepararSustituta = true

            shouldThrowAny { restaurador.deshacer(PROCEDENCIA, AHORA) }

            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de la copia")
        }

    // Segunda fase: si falla la sustitucion, la copia previa sigue siendo la de
    // antes. Con una sola fase se habria sobrescrito ya, y lo de antes de
    // restaurar se perderia justo cuando se queria volver a ello.
    @Test
    fun `si la sustitucion falla al deshacer, la copia previa sigue siendo la de antes`() =
        runTest {
            conDatosActuales()
            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)
            repositorio.fallarLaProximaVez = true

            shouldThrowAny { restaurador.deshacer(PROCEDENCIA, AHORA) }
            restaurador.deshacer(PROCEDENCIA, AHORA)

            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de ahora")
        }

    @Test
    fun `sin restauracion previa no hay nada que deshacer`() =
        runTest {
            shouldThrow<IllegalStateException> { restaurador.deshacer(PROCEDENCIA, AHORA) }
        }

    // La copia previa se queda en el telefono: tambien va cifrada.
    @Test
    fun `la copia previa va cifrada`() =
        runTest {
            conDatosActuales()

            restaurador.restaurar(copia(), PROCEDENCIA, AHORA)

            val guardada = piezasDe(almacen.contenido!!).values.joinToString("") { it.decodeToString() }
            guardada shouldNotContain "Cuenta de ahora"
        }
}
