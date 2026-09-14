package com.miplata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.miplata.core.data.database.MiPlataDatabase
import com.miplata.core.data.database.entity.CuentaEntity
import com.miplata.core.data.database.entity.TransaccionEntity
import com.miplata.core.data.mapper.DatoGuardadoInvalido
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.usecase.CalcularResumenMensualUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PRIMER_INSTANTE = 1_000L
private const val SEGUNDO_INSTANTE = 2_000L

private val MARZO = Mes.de(2026, 3)
private val USD = Moneda("USD")

/** Reloj que el test mueve a mano, para poder afirmar que se guardo. */
private class RelojFalso(
    var instante: Long = PRIMER_INSTANTE,
) : Reloj {
    override fun ahoraEnMillis(): Long = instante
}

private fun cuenta(
    id: String,
    nombre: String = "Cuenta $id",
    saldo: Long = 0,
) = Cuenta(
    id = CuentaId(id),
    nombre = nombre,
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = Money.deUnidades(saldo),
    moneda = USD,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoriosDeRoomTest {
    private lateinit var db: MiPlataDatabase
    private val reloj = RelojFalso()

    private lateinit var cuentas: RoomCuentaRepository
    private lateinit var categorias: RoomCategoriaRepository
    private lateinit var transacciones: RoomTransaccionRepository
    private lateinit var planes: RoomPlanRepository

    @Before
    fun crear() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MiPlataDatabase::class.java,
                ).build()
        cuentas = RoomCuentaRepository(db.cuentaDao(), reloj)
        categorias = RoomCategoriaRepository(db.categoriaDao(), reloj)
        transacciones = RoomTransaccionRepository(db.transaccionDao(), reloj)
        planes = RoomPlanRepository(db.planDao(), reloj)
    }

    @After
    fun cerrar() = db.close()

    // --- Ida y vuelta ---

    // Lo que de verdad prueba el mapper: lo que entra tiene que salir igual.
    @Test
    fun `una cuenta sobrevive al ida y vuelta sin perder nada`() =
        runTest {
            val original =
                cuenta("c1", "Banco", saldo = -250).copy(
                    tipo = TipoDeCuenta.TARJETA_CREDITO,
                    incluirEnTotal = false,
                    archivada = true,
                )

            cuentas.guardar(original)

            cuentas.obtener(CuentaId("c1")) shouldBe original
        }

    @Test
    fun `una categoria con madre sobrevive al ida y vuelta`() =
        runTest {
            categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida"))
            val hija =
                Categoria(
                    id = CategoriaId("resto"),
                    nombre = "Restaurantes",
                    padreId = CategoriaId("comida"),
                    icono = "cubiertos",
                    color = 0xFF00FF,
                )

            categorias.guardar(hija)

            categorias.obtener(CategoriaId("resto")) shouldBe hija
        }

    @Test
    fun `una transferencia sobrevive al ida y vuelta`() =
        runTest {
            cuentas.guardar(cuenta("origen"))
            cuentas.guardar(cuenta("destino"))
            val original =
                Transaccion(
                    id = TransaccionId("t1"),
                    fecha = LocalDate(2026, 3, 15),
                    monto = Money.deUnidades(500),
                    tipo = TipoDeTransaccion.TRANSFERENCIA,
                    cuentaOrigenId = CuentaId("origen"),
                    cuentaDestinoId = CuentaId("destino"),
                    nota = "traspaso a ahorros",
                )

            transacciones.guardar(original)

            transacciones.obtener(TransaccionId("t1")) shouldBe original
        }

    @Test
    fun `un plan con sus lineas sobrevive al ida y vuelta conservando el orden`() =
        runTest {
            val original =
                PlanMensual(
                    id = PlanId("p1"),
                    mes = MARZO,
                    lineas =
                        listOf(
                            linea("sueldo", TipoDeLinea.INGRESO, 2000),
                            linea("arriendo", TipoDeLinea.GASTO_FIJO, 450, dia = 5),
                            linea("comida", TipoDeLinea.GASTO_VARIABLE, 400, activa = false),
                        ),
                )

            planes.guardar(original)

            planes.obtenerDe(MARZO) shouldBe original
        }

    // --- Datos corruptos ---

    // Los enums se guardan por nombre. Si aparece un valor que no existe -por
    // corrupcion, o por una fila escrita por una version que no deberia haber
    // existido- se falla en vez de adivinar. Adivinar en una app de finanzas
    // termina en cifras plausibles y equivocadas.
    @Test
    fun `un tipo de cuenta desconocido falla en vez de adivinar`() =
        runTest {
            db.cuentaDao().guardar(
                CuentaEntity(
                    id = "c1",
                    nombre = "Rara",
                    tipo = "CRIPTOMONEDA_LUNAR",
                    saldoInicialCentavos = 0,
                    moneda = "USD",
                    incluirEnTotal = true,
                    archivada = false,
                    creadaEn = PRIMER_INSTANTE,
                    actualizadaEn = PRIMER_INSTANTE,
                ),
            )

            shouldThrow<DatoGuardadoInvalido> { cuentas.obtener(CuentaId("c1")) }
        }

    @Test
    fun `una fecha corrupta falla en vez de adivinar`() =
        runTest {
            cuentas.guardar(cuenta("c1"))
            db.transaccionDao().guardar(
                TransaccionEntity(
                    id = "t1",
                    fecha = "el martes",
                    montoCentavos = 100,
                    tipo = "GASTO",
                    cuentaOrigenId = "c1",
                    creadaEn = PRIMER_INSTANTE,
                    actualizadaEn = PRIMER_INSTANTE,
                ),
            )

            shouldThrow<DatoGuardadoInvalido> { transacciones.obtener(TransaccionId("t1")) }
        }

    // --- Marcas de tiempo ---

    // creadaEn es cuando el usuario creo la cuenta, no cuando la edito por
    // ultima vez. Perderlo al editar borraria la antiguedad del dato.
    @Test
    fun `editar conserva la fecha de creacion y actualiza la de modificacion`() =
        runTest {
            cuentas.guardar(cuenta("c1", "Viejo"))
            reloj.instante = SEGUNDO_INSTANTE

            cuentas.guardar(cuenta("c1", "Nuevo"))

            val fila = db.cuentaDao().obtener("c1")
            fila?.creadaEn shouldBe PRIMER_INSTANTE
            fila?.actualizadaEn shouldBe SEGUNDO_INSTANTE
        }

    // --- Borrado logico ---

    @Test
    fun `eliminar oculta la cuenta pero la fila sigue ahi`() =
        runTest {
            cuentas.guardar(cuenta("c1"))

            cuentas.eliminar(CuentaId("c1"))

            cuentas.obtener(CuentaId("c1")).shouldBeNull()
            cuentas.observarTodas().first() shouldBe emptyList()
        }

    // --- Flujos ---

    @Test
    fun `el flujo de cuentas emite al guardar`() =
        runTest {
            cuentas.observarTodas().test {
                awaitItem() shouldBe emptyList()

                cuentas.guardar(cuenta("c1"))
                awaitItem().map { it.id } shouldBe listOf(CuentaId("c1"))
            }
        }

    @Test
    fun `el flujo del plan emite al guardar`() =
        runTest {
            planes.observarDe(MARZO).test {
                awaitItem().shouldBeNull()

                planes.guardar(PlanMensual(id = PlanId("p1"), mes = MARZO))
                awaitItem()?.mes shouldBe MARZO
            }
        }

    // --- Consultas del dominio ---

    @Test
    fun `filtra transacciones por un periodo desplazado`() =
        runTest {
            cuentas.guardar(cuenta("c1"))
            transacciones.guardar(gasto("fuera", LocalDate(2026, 3, 24)))
            transacciones.guardar(gasto("dentro", LocalDate(2026, 4, 10)))

            val periodo = PeriodoMensual(MARZO, primerDia = 25)

            transacciones.observarDelPeriodo(periodo).first().map { it.id } shouldBe
                listOf(TransaccionId("dentro"))
        }

    @Test
    fun `encuentra el ultimo plan anterior saltando huecos`() =
        runTest {
            planes.guardar(PlanMensual(id = PlanId("p1"), mes = Mes.de(2025, 11)))
            planes.guardar(PlanMensual(id = PlanId("p2"), mes = Mes.de(2026, 1)))

            planes.obtenerUltimoAnteriorA(Mes.de(2026, 6))?.mes shouldBe Mes.de(2026, 1)
        }

    // --- El dominio contra datos reales ---

    // Hasta ahora el use case solo habia visto listas construidas a mano. Aqui
    // corre sobre lo que devuelve Room, pasando por los mappers: es el primer
    // momento en que las dos mitades del proyecto se tocan.
    @Test
    fun `el resumen mensual cuadra leyendo de la base`() =
        runTest {
            sembrarMarzoConPlanYMovimientos()

            val periodo = PeriodoMensual(MARZO)
            val resumen =
                CalcularResumenMensualUseCase()(
                    periodo = periodo,
                    plan = planes.obtenerDe(MARZO),
                    transacciones = transacciones.observarDelPeriodo(periodo).first(),
                )

            resumen.ingresosReales shouldBe Money.deUnidades(2000)
            resumen.gastosReales shouldBe Money.deUnidades(520)
            resumen.disponibleReal shouldBe Money.deUnidades(1480)
            resumen.enSobregiro shouldBe false

            val comida = resumen.desviacionPorLinea.first { it.linea.id == LineaId("comida") }
            comida.desviacion shouldBe Money.deUnidades(120)
            comida.esDesfavorable shouldBe true
        }

    /** Un marzo con sueldo planificado de 2000, comida de 400, y 520 gastados. */
    private suspend fun sembrarMarzoConPlanYMovimientos() {
        cuentas.guardar(cuenta("c1"))
        categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida"))
        planes.guardar(
            PlanMensual(
                id = PlanId("p1"),
                mes = MARZO,
                lineas =
                    listOf(
                        linea("sueldo", TipoDeLinea.INGRESO, 2000),
                        linea("comida", TipoDeLinea.GASTO_VARIABLE, 400),
                    ),
            ),
        )
        transacciones.guardar(gasto("t1", LocalDate(2026, 3, 5), monto = 520, linea = "comida"))
        transacciones.guardar(
            Transaccion(
                id = TransaccionId("t2"),
                fecha = LocalDate(2026, 3, 1),
                monto = Money.deUnidades(2000),
                tipo = TipoDeTransaccion.INGRESO,
                cuentaOrigenId = CuentaId("c1"),
                lineaDePlanId = LineaId("sueldo"),
            ),
        )
    }

    private fun linea(
        id: String,
        tipo: TipoDeLinea,
        monto: Long,
        dia: Int? = null,
        activa: Boolean = true,
    ) = LineaDePlan(
        id = LineaId(id),
        nombre = "Linea $id",
        tipo = tipo,
        montoPlanificado = Money.deUnidades(monto),
        diaDelMes = dia,
        activa = activa,
    )

    private fun gasto(
        id: String,
        fecha: LocalDate,
        monto: Long = 10,
        linea: String? = null,
    ) = Transaccion(
        id = TransaccionId(id),
        fecha = fecha,
        monto = Money.deUnidades(monto),
        tipo = TipoDeTransaccion.GASTO,
        cuentaOrigenId = CuentaId("c1"),
        lineaDePlanId = linea?.let(::LineaId),
    )
}
