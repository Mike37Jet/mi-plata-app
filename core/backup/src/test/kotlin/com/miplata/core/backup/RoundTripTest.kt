package com.miplata.core.backup

import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCategoriaRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * El test que de verdad importa: exportar, importar en una app vacia, y que
 * todo quede **identico**.
 *
 * Es el escenario del usuario -"al cambiar de celular importarla"- comprobado
 * de punta a punta: modelos del dominio, repositorios, archivo, y vuelta. Si
 * este test pasa, un backup sirve para lo que promete.
 */
class RoundTripTest {
    private val archivo = ArchivoDeBackup()

    private fun app() = AppEnMemoria()

    /** Una app con datos variados, para que el round-trip tenga algo que perder. */
    private suspend fun AppEnMemoria.llenar() {
        ponerAjustes()
        ponerCuentas()
        ponerCategorias()
        ponerPlanes()
        ponerTransacciones()
    }

    private suspend fun AppEnMemoria.ponerAjustes() {
        ajustes.guardar(
            Ajustes(
                moneda = Moneda("EUR"),
                primerDiaDelMesFinanciero = 25,
                tema = Tema.OSCURO,
                ultimoBackupEnMillis = 1_772_000_000_000L,
            ),
        )
    }

    private suspend fun AppEnMemoria.ponerCuentas() {
        cuentas.guardar(
            Cuenta(
                id = CuentaId("banco"),
                nombre = "Cuenta del banco",
                tipo = TipoDeCuenta.BANCARIA,
                saldoInicial = Money.deUnidades(2500),
                moneda = Moneda("EUR"),
            ),
        )
        cuentas.guardar(
            Cuenta(
                id = CuentaId("visa"),
                nombre = "Visa",
                tipo = TipoDeCuenta.TARJETA_CREDITO,
                saldoInicial = Money.deUnidades(-430),
                moneda = Moneda("EUR"),
                incluirEnTotal = false,
                archivada = true,
            ),
        )
    }

    private suspend fun AppEnMemoria.ponerCategorias() {
        categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida", color = -12345))
        categorias.guardar(
            Categoria(id = CategoriaId("resto"), nombre = "Restaurantes", padreId = CategoriaId("comida")),
        )
    }

    private suspend fun AppEnMemoria.ponerPlanes() {
        planes.guardar(
            PlanMensual(
                id = PlanId("p3"),
                mes = Mes.de(2026, 3),
                lineas =
                    listOf(
                        LineaDePlan(
                            id = LineaId("sueldo"),
                            nombre = "Sueldo",
                            tipo = TipoDeLinea.INGRESO,
                            montoPlanificado = Money.deUnidades(2000),
                            diaDelMes = 25,
                        ),
                        LineaDePlan(
                            id = LineaId("comida"),
                            nombre = "Comida",
                            tipo = TipoDeLinea.GASTO_VARIABLE,
                            montoPlanificado = Money.deCentavos(40_050),
                            categoriaId = CategoriaId("comida"),
                            activa = false,
                        ),
                    ),
            ),
        )
        planes.guardar(PlanMensual(id = PlanId("p4"), mes = Mes.de(2026, 4)))
    }

    private suspend fun AppEnMemoria.ponerTransacciones() {
        transacciones.guardar(
            Transaccion(
                id = TransaccionId("t1"),
                fecha = LocalDate(2026, 3, 15),
                monto = Money.deCentavos(4_275),
                tipo = TipoDeTransaccion.GASTO,
                cuentaOrigenId = CuentaId("banco"),
                categoriaId = CategoriaId("resto"),
                lineaDePlanId = LineaId("comida"),
                nota = "Cena con Ana",
            ),
        )
        transacciones.guardar(
            Transaccion(
                id = TransaccionId("t2"),
                fecha = LocalDate(2026, 3, 25),
                monto = Money.deUnidades(2000),
                tipo = TipoDeTransaccion.INGRESO,
                cuentaOrigenId = CuentaId("banco"),
            ),
        )
        transacciones.guardar(
            Transaccion(
                id = TransaccionId("t3"),
                fecha = LocalDate(2026, 3, 26),
                monto = Money.deUnidades(100),
                tipo = TipoDeTransaccion.TRANSFERENCIA,
                cuentaOrigenId = CuentaId("banco"),
                cuentaDestinoId = CuentaId("visa"),
            ),
        )
    }

    @Test
    fun `exportar e importar en un movil nuevo deja todo identico`() =
        runTest {
            val original = app().also { it.llenar() }
            val bytes = archivo.escribirABytes(original.aContenido())

            val nuevo = app()
            nuevo.restaurador.restaurar(archivo.leer(ByteArrayInputStream(bytes)).datos)

            nuevo.ajustes.obtener() shouldBe original.ajustes.obtener()
            nuevo.cuentas.observarTodas().first() shouldBe original.cuentas.observarTodas().first()
            nuevo.categorias.observarTodas().first() shouldBe original.categorias.observarTodas().first()
            nuevo.transacciones.observarTodas().first() shouldBe original.transacciones.observarTodas().first()
            nuevo.planes.observarTodos().first() shouldBe original.planes.observarTodos().first()
        }

    // Los centavos viajan como enteros justamente para esto: un 400,50 escrito
    // como decimal en JSON puede volver como 400,49999999.
    @Test
    fun `los importes vuelven al centavo`() =
        runTest {
            val original = app().also { it.llenar() }
            val bytes = archivo.escribirABytes(original.aContenido())

            val nuevo = app()
            nuevo.restaurador.restaurar(archivo.leer(ByteArrayInputStream(bytes)).datos)

            nuevo.transacciones.obtener(TransaccionId("t1"))!!.monto shouldBe Money.deCentavos(4_275)
            nuevo.planes
                .obtenerDe(Mes.de(2026, 3))!!
                .lineas
                .first { it.id == LineaId("comida") }
                .montoPlanificado shouldBe Money.deCentavos(40_050)
        }

    // Un plan sin movimientos es justo el mes que acabas de armar: es lo primero
    // que se perderia si los meses se dedujeran de las transacciones.
    @Test
    fun `un plan sin movimientos tambien viaja`() =
        runTest {
            val original = app().also { it.llenar() }
            val bytes = archivo.escribirABytes(original.aContenido())

            val nuevo = app()
            nuevo.restaurador.restaurar(archivo.leer(ByteArrayInputStream(bytes)).datos)

            nuevo.planes.obtenerDe(Mes.de(2026, 4)) shouldBe original.planes.obtenerDe(Mes.de(2026, 4))
        }

    // Lo que distingue una transferencia de un gasto son sus dos cuentas; si el
    // destino se perdiera, el backup convertiria transferencias en gastos.
    @Test
    fun `una transferencia conserva sus dos cuentas`() =
        runTest {
            val original = app().also { it.llenar() }
            val bytes = archivo.escribirABytes(original.aContenido())

            val nuevo = app()
            nuevo.restaurador.restaurar(archivo.leer(ByteArrayInputStream(bytes)).datos)

            val transferencia = nuevo.transacciones.obtener(TransaccionId("t3"))!!
            transferencia.tipo shouldBe TipoDeTransaccion.TRANSFERENCIA
            transferencia.cuentaOrigenId shouldBe CuentaId("banco")
            transferencia.cuentaDestinoId shouldBe CuentaId("visa")
        }

    @Test
    fun `las banderas de una cuenta sobreviven`() =
        runTest {
            val original = app().also { it.llenar() }
            val bytes = archivo.escribirABytes(original.aContenido())

            val nuevo = app()
            nuevo.restaurador.restaurar(archivo.leer(ByteArrayInputStream(bytes)).datos)

            val visa = nuevo.cuentas.obtener(CuentaId("visa"))!!
            visa.archivada shouldBe true
            visa.incluirEnTotal shouldBe false
            visa.saldoInicial shouldBe Money.deUnidades(-430)
        }

    @Test
    fun `una linea desactivada sigue desactivada`() =
        runTest {
            val original = app().also { it.llenar() }
            val bytes = archivo.escribirABytes(original.aContenido())

            val nuevo = app()
            nuevo.restaurador.restaurar(archivo.leer(ByteArrayInputStream(bytes)).datos)

            nuevo.planes
                .obtenerDe(Mes.de(2026, 3))!!
                .lineas
                .first { it.id == LineaId("comida") }
                .activa shouldBe false
        }

    @Test
    fun `el recuento del manifiesto coincide con lo que hay`() =
        runTest {
            val original = app().also { it.llenar() }

            val manifiesto =
                archivo
                    .leer(ByteArrayInputStream(archivo.escribirABytes(original.aContenido())))
                    .manifiesto

            manifiesto.contenido shouldBe Recuento(cuentas = 2, categorias = 2, transacciones = 3, planes = 2)
        }
}

/** Una app entera en memoria: los mismos contratos que usa la de verdad. */
private class AppEnMemoria {
    val cuentas = FakeCuentaRepository()
    val categorias = FakeCategoriaRepository()
    val transacciones = FakeTransaccionRepository()
    val planes = FakePlanRepository()
    val ajustes = FakeAjustesRepository()

    val recolector = RecolectorDeDatos(cuentas, categorias, transacciones, planes, ajustes)
    val restaurador = Restaurador(cuentas, categorias, transacciones, planes, ajustes)

    suspend fun aContenido() =
        ContenidoDelBackup(
            datos = recolector.recolectar(),
            versionDelEsquema = 1,
            versionDeLaApp = "0.1.0",
            creadoEnMillis = 1_772_000_000_000L,
            dispositivo = "Pixel de prueba",
        )
}
