package com.miplata.core.data.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.miplata.core.data.database.entity.CategoriaEntity
import com.miplata.core.data.database.entity.CuentaEntity
import com.miplata.core.data.database.entity.LineaDePlanEntity
import com.miplata.core.data.database.entity.PlanEntity
import com.miplata.core.data.database.entity.TransaccionEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val AHORA = 1_772_000_000_000L

private fun cuenta(
    id: String,
    nombre: String = "Cuenta $id",
) = CuentaEntity(
    id = id,
    nombre = nombre,
    tipo = "BANCARIA",
    saldoInicialCentavos = 0,
    moneda = "USD",
    incluirEnTotal = true,
    archivada = false,
    creadaEn = AHORA,
    actualizadaEn = AHORA,
)

private fun transaccion(
    id: String,
    fecha: String,
    origen: String = "c1",
    destino: String? = null,
) = TransaccionEntity(
    id = id,
    fecha = fecha,
    montoCentavos = 1000,
    tipo = "GASTO",
    cuentaOrigenId = origen,
    cuentaDestinoId = destino,
    creadaEn = AHORA,
    actualizadaEn = AHORA,
)

private fun plan(
    id: String,
    mes: String,
) = PlanEntity(id = id, mes = mes, creadoEn = AHORA, actualizadoEn = AHORA)

private fun linea(
    id: String,
    planId: String,
    orden: Int,
    nombre: String = "Linea $id",
) = LineaDePlanEntity(
    id = id,
    planId = planId,
    nombre = nombre,
    tipo = "GASTO_FIJO",
    montoPlanificadoCentavos = 10_000,
    activa = true,
    orden = orden,
    creadaEn = AHORA,
    actualizadaEn = AHORA,
)

/**
 * Tests de los DAO contra una base real en memoria.
 *
 * Con Robolectric corren en la JVM, sin emulador, asi que CI los ejecuta como
 * cualquier otro test unitario. Un test de persistencia que necesite un emulador
 * acaba no ejecutandose nunca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaosTest {
    private lateinit var db: MiPlataDatabase

    @Before
    fun crearBase() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MiPlataDatabase::class.java,
                ).build()
    }

    @After
    fun cerrarBase() {
        db.close()
    }

    // --- Cuentas ---

    @Test
    fun `guarda y recupera una cuenta`() =
        runTest {
            db.cuentaDao().guardar(cuenta("c1", "Banco"))

            db.cuentaDao().obtener("c1")?.nombre shouldBe "Banco"
        }

    @Test
    fun `guardar con el mismo id actualiza en vez de duplicar`() =
        runTest {
            db.cuentaDao().guardar(cuenta("c1", "Viejo"))
            db.cuentaDao().guardar(cuenta("c1", "Nuevo"))

            db
                .cuentaDao()
                .observarTodas()
                .first()
                .size shouldBe 1
            db.cuentaDao().obtener("c1")?.nombre shouldBe "Nuevo"
        }

    // Lo que hace que la UI reaccione sola: la base es la fuente de verdad.
    @Test
    fun `el flujo de cuentas emite al insertar`() =
        runTest {
            db.cuentaDao().observarTodas().test {
                awaitItem() shouldBe emptyList()

                db.cuentaDao().guardar(cuenta("c1"))
                awaitItem().map { it.id } shouldBe listOf("c1")
            }
        }

    // El borrado es logico: la fila sigue ahi, pero deja de verse.
    @Test
    fun `una cuenta eliminada deja de aparecer pero la fila sobrevive`() =
        runTest {
            db.cuentaDao().guardar(cuenta("c1"))

            db.cuentaDao().marcarEliminada("c1", AHORA)

            db.cuentaDao().obtener("c1").shouldBeNull()
            db.cuentaDao().observarTodas().first() shouldBe emptyList()
        }

    // --- Integridad referencial ---

    // Un movimiento que apunta a una cuenta inexistente es un dato corrupto. La
    // clave foranea lo impide en la propia base, no solo en el codigo de Kotlin.
    @Test
    fun `no se puede guardar un movimiento de una cuenta que no existe`() =
        runTest {
            shouldThrow<SQLiteConstraintException> {
                db.transaccionDao().guardar(transaccion("t1", "2026-03-15", origen = "fantasma"))
            }
        }

    @Test
    fun `no se puede guardar una linea de un plan que no existe`() =
        runTest {
            shouldThrow<SQLiteConstraintException> {
                db.planDao().guardarLineas(listOf(linea("l1", planId = "fantasma", orden = 0)))
            }
        }

    // --- Transacciones ---

    @Test
    fun `filtra los movimientos por rango de fechas`() =
        runTest {
            db.cuentaDao().guardar(cuenta("c1"))
            listOf(
                transaccion("antes", "2026-02-28"),
                transaccion("primero", "2026-03-01"),
                transaccion("ultimo", "2026-03-31"),
                transaccion("despues", "2026-04-01"),
            ).forEach { db.transaccionDao().guardar(it) }

            val delMes =
                db.transaccionDao().observarEntreFechas("2026-03-01", "2026-03-31").first()

            delMes.map { it.id } shouldBe listOf("primero", "ultimo")
        }

    // El caso de quien cobra el 25: su periodo cruza el cambio de mes.
    @Test
    fun `un rango que cruza el cambio de mes funciona igual`() =
        runTest {
            db.cuentaDao().guardar(cuenta("c1"))
            listOf(
                transaccion("fuera", "2026-03-24"),
                transaccion("dentro", "2026-04-10"),
            ).forEach { db.transaccionDao().guardar(it) }

            db
                .transaccionDao()
                .observarEntreFechas("2026-03-25", "2026-04-24")
                .first()
                .map { it.id } shouldBe listOf("dentro")
        }

    @Test
    fun `busca por cuenta incluyendo las transferencias recibidas`() =
        runTest {
            db.cuentaDao().guardar(cuenta("c1"))
            db.cuentaDao().guardar(cuenta("c2"))
            db.transaccionDao().guardar(transaccion("t1", "2026-03-10", destino = "c2"))

            db
                .transaccionDao()
                .observarDeCuenta("c2")
                .first()
                .map { it.id } shouldBe listOf("t1")
            db
                .transaccionDao()
                .observarDeCuenta("c1")
                .first()
                .map { it.id } shouldBe listOf("t1")
        }

    // --- Planes ---

    @Test
    fun `guarda un plan con sus lineas y las devuelve juntas`() =
        runTest {
            db.planDao().guardarPlanConLineas(
                plan("p1", "2026-03"),
                listOf(linea("l1", "p1", orden = 0), linea("l2", "p1", orden = 1)),
            )

            val guardado = db.planDao().obtenerPorMes("2026-03")

            guardado?.plan?.mes shouldBe "2026-03"
            guardado?.lineas?.size shouldBe 2
        }

    // @Relation no admite ORDER BY: sin la columna `orden` el plan del usuario
    // saldria en el orden que le viniera bien a SQLite.
    @Test
    fun `las lineas se devuelven en el orden del usuario`() =
        runTest {
            db.planDao().guardarPlanConLineas(
                plan("p1", "2026-03"),
                listOf(
                    linea("tercera", "p1", orden = 2, nombre = "Tercera"),
                    linea("primera", "p1", orden = 0, nombre = "Primera"),
                    linea("segunda", "p1", orden = 1, nombre = "Segunda"),
                ),
            )

            db
                .planDao()
                .obtenerPorMes("2026-03")
                ?.lineasOrdenadas
                ?.map { it.nombre } shouldBe
                listOf("Primera", "Segunda", "Tercera")
        }

    // Guardar un plan reemplaza sus lineas; no las acumula.
    @Test
    fun `volver a guardar un plan reemplaza sus lineas`() =
        runTest {
            db.planDao().guardarPlanConLineas(plan("p1", "2026-03"), listOf(linea("l1", "p1", 0)))

            db.planDao().guardarPlanConLineas(plan("p1", "2026-03"), listOf(linea("l2", "p1", 0)))

            val guardado = db.planDao().obtenerPorMes("2026-03")
            guardado?.lineas?.map { it.id } shouldBe listOf("l2")
        }

    // Lo que necesita la materializacion cuando hay huecos.
    @Test
    fun `encuentra el ultimo plan anterior saltando huecos`() =
        runTest {
            db.planDao().guardarPlanConLineas(plan("p1", "2025-11"), emptyList())
            db.planDao().guardarPlanConLineas(plan("p2", "2026-01"), emptyList())

            db
                .planDao()
                .obtenerUltimoAnteriorA("2026-06")
                ?.plan
                ?.mes shouldBe "2026-01"
        }

    @Test
    fun `no devuelve planes futuros ni el del propio mes`() =
        runTest {
            db.planDao().guardarPlanConLineas(plan("p1", "2026-03"), emptyList())

            db.planDao().obtenerUltimoAnteriorA("2026-03").shouldBeNull()
            db.planDao().obtenerUltimoAnteriorA("2026-01").shouldBeNull()
        }

    @Test
    fun `el flujo del plan emite al guardar`() =
        runTest {
            db.planDao().observarPorMes("2026-03").test {
                awaitItem().shouldBeNull()

                db.planDao().guardarPlanConLineas(plan("p1", "2026-03"), emptyList())
                awaitItem()?.plan?.id shouldBe "p1"
            }
        }

    // --- Categorias ---

    @Test
    fun `guarda una jerarquia de dos niveles`() =
        runTest {
            val madre =
                CategoriaEntity(id = "comida", nombre = "Comida", creadaEn = AHORA, actualizadaEn = AHORA)
            val hija =
                CategoriaEntity(
                    id = "resto",
                    nombre = "Restaurantes",
                    padreId = "comida",
                    creadaEn = AHORA,
                    actualizadaEn = AHORA,
                )

            db.categoriaDao().guardarTodas(listOf(madre, hija))

            db.categoriaDao().cuantasHay() shouldBe 2
            db.categoriaDao().obtener("resto")?.padreId shouldBe "comida"
        }

    @Test
    fun `no se puede apuntar a una categoria madre inexistente`() =
        runTest {
            shouldThrow<SQLiteConstraintException> {
                db.categoriaDao().guardar(
                    CategoriaEntity(
                        id = "huerfana",
                        nombre = "Huerfana",
                        padreId = "fantasma",
                        creadaEn = AHORA,
                        actualizadaEn = AHORA,
                    ),
                )
            }
        }
}
