package com.miplata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miplata.core.data.database.MiPlataDatabase
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.ContenidoFinanciero
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import io.kotest.assertions.throwables.shouldThrowAny
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
import java.util.concurrent.Executors

private val RELOJ = Reloj { 1_000L }

private fun cuenta(
    id: String,
    nombre: String = id,
) = Cuenta(
    id = CuentaId(id),
    nombre = nombre,
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = Money.deUnidades(100),
    moneda = Moneda("EUR"),
)

private fun gasto(
    id: String,
    cuenta: String,
    lineaId: String? = null,
) = Transaccion(
    id = TransaccionId(id),
    fecha = LocalDate(2026, 3, 15),
    monto = Money.deUnidades(10),
    tipo = TipoDeTransaccion.GASTO,
    cuentaOrigenId = CuentaId(cuenta),
    lineaDePlanId = lineaId?.let(::LineaId),
)

/**
 * La restauracion contra una base Room de verdad.
 *
 * Lo que se prueba aqui no se puede probar con fakes: que la transaccion de
 * SQLite deshace de verdad todo lo hecho cuando algo falla a mitad. Es la
 * garantia en la que se apoya docs/05, "nunca un estado a medias".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRepositorioDeRestauracionTest {
    private lateinit var db: MiPlataDatabase
    private lateinit var restauracion: RoomRepositorioDeRestauracion
    private lateinit var cuentas: RoomCuentaRepository
    private lateinit var categorias: RoomCategoriaRepository
    private lateinit var transacciones: RoomTransaccionRepository
    private lateinit var planes: RoomPlanRepository

    @Before
    fun crearBase() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MiPlataDatabase::class.java)
                // Un hilo propio para las transacciones: `withTransaction` lo
                // necesita, y con el del test se quedaria esperando a si mismo.
                .setTransactionExecutor(Executors.newSingleThreadExecutor())
                .build()
        restauracion = RoomRepositorioDeRestauracion(db, RELOJ)
        cuentas = RoomCuentaRepository(db.cuentaDao(), RELOJ)
        categorias = RoomCategoriaRepository(db.categoriaDao(), RELOJ)
        transacciones = RoomTransaccionRepository(db.transaccionDao(), RELOJ)
        planes = RoomPlanRepository(db.planDao(), RELOJ)
    }

    @After
    fun cerrarBase() = db.close()

    /** Lo que habia antes de restaurar: lo que no se puede perder si algo falla. */
    private suspend fun conDatosPrevios() {
        cuentas.guardar(cuenta("vieja", "Cuenta de antes"))
        transacciones.guardar(gasto("t-vieja", cuenta = "vieja"))
    }

    private suspend fun contenidoActual() =
        ContenidoFinanciero(
            cuentas = cuentas.observarTodas().first(),
            categorias = categorias.observarTodas().first(),
            transacciones = transacciones.observarTodas().first(),
            planes = planes.observarTodos().first(),
        )

    @Test
    fun `sustituye lo que habia por el contenido de la copia`() =
        runTest {
            conDatosPrevios()

            restauracion.reemplazarTodo(
                ContenidoFinanciero(
                    cuentas = listOf(cuenta("nueva", "Cuenta de la copia")),
                    transacciones = listOf(gasto("t-nueva", cuenta = "nueva")),
                ),
            )

            val ahora = contenidoActual()
            ahora.cuentas.map { it.nombre } shouldBe listOf("Cuenta de la copia")
            ahora.transacciones.map { it.id.valor } shouldBe listOf("t-nueva")
        }

    // El test que justifica la clase. Una copia incoherente -un movimiento que
    // apunta a una cuenta que la copia no trae- hace fallar la insercion a
    // mitad, DESPUES de haber borrado lo que habia. La transaccion tiene que
    // deshacer ese borrado: la base queda exactamente como estaba.
    @Test
    fun `si algo falla a mitad no se toca nada`() =
        runTest {
            conDatosPrevios()
            val antes = contenidoActual()

            shouldThrowAny {
                restauracion.reemplazarTodo(
                    ContenidoFinanciero(
                        cuentas = listOf(cuenta("nueva")),
                        transacciones = listOf(gasto("t-huerfana", cuenta = "no-existe")),
                    ),
                )
            }

            contenidoActual() shouldBe antes
        }

    // Un id repetido dentro de la copia no se sobrescribe en silencio con el
    // ultimo: hace fallar la restauracion entera.
    @Test
    fun `un id repetido en la copia la rechaza sin tocar nada`() =
        runTest {
            conDatosPrevios()
            val antes = contenidoActual()

            shouldThrowAny {
                restauracion.reemplazarTodo(
                    ContenidoFinanciero(cuentas = listOf(cuenta("repetida", "Una"), cuenta("repetida", "Otra"))),
                )
            }

            contenidoActual() shouldBe antes
        }

    // La copia sale de la base en orden alfabetico, y una subcategoria que
    // empiece por "A" llegaria antes que su madre. Sin ordenar, su clave foranea
    // haria fallar la restauracion entera.
    @Test
    fun `una subcategoria que llega antes que su madre se restaura`() =
        runTest {
            restauracion.reemplazarTodo(
                ContenidoFinanciero(
                    categorias =
                        listOf(
                            Categoria(
                                id = CategoriaId("a-resto"),
                                nombre = "A restaurantes",
                                padreId = CategoriaId("z-comida"),
                            ),
                            Categoria(id = CategoriaId("z-comida"), nombre = "Z comida"),
                        ),
                ),
            )

            categorias.observarTodas().first().size shouldBe 2
        }

    // Lo que fallo en el emulador y ningun test cubria: BORRAR una jerarquia de
    // categorias que ya existe. Las categorias sembradas de serie tienen
    // subcategorias, asi que cualquier telefono real esta en este caso. Con un
    // `DELETE FROM categorias` a secas, SQLite borraba una madre antes que su
    // hija y la clave foranea RESTRICT paraba la restauracion entera.
    @Test
    fun `restaura sobre una base que ya tiene subcategorias`() =
        runTest {
            categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida"))
            categorias.guardar(
                Categoria(id = CategoriaId("resto"), nombre = "Restaurantes", padreId = CategoriaId("comida")),
            )

            restauracion.reemplazarTodo(
                ContenidoFinanciero(categorias = listOf(Categoria(id = CategoriaId("otra"), nombre = "Otra"))),
            )

            categorias.observarTodas().first().map { it.nombre } shouldBe listOf("Otra")
        }

    @Test
    fun `los planes vuelven con sus lineas y los movimientos enganchados a ellas`() =
        runTest {
            val plan =
                PlanMensual(
                    id = PlanId("p"),
                    mes = Mes.de(2026, 3),
                    lineas =
                        listOf(
                            LineaDePlan(
                                id = LineaId("comida"),
                                nombre = "Comida",
                                tipo = TipoDeLinea.GASTO_VARIABLE,
                                montoPlanificado = Money.deUnidades(400),
                            ),
                        ),
                )

            restauracion.reemplazarTodo(
                ContenidoFinanciero(
                    cuentas = listOf(cuenta("banco")),
                    planes = listOf(plan),
                    transacciones = listOf(gasto("t1", cuenta = "banco", lineaId = "comida")),
                ),
            )

            planes.obtenerDe(Mes.de(2026, 3)) shouldBe plan
            transacciones.obtener(TransaccionId("t1"))!!.lineaDePlanId shouldBe LineaId("comida")
        }

    // Restaurar borra de verdad, no marca como borrado. Una cuenta eliminada
    // antes -que sigue en la tabla, marcada- tendria el mismo id que la de la
    // copia y la insercion chocaria.
    @Test
    fun `lo borrado antes no estorba a la copia`() =
        runTest {
            cuentas.guardar(cuenta("banco", "Borrada"))
            cuentas.eliminar(CuentaId("banco"))

            restauracion.reemplazarTodo(ContenidoFinanciero(cuentas = listOf(cuenta("banco", "De la copia"))))

            cuentas.obtener(CuentaId("banco"))!!.nombre shouldBe "De la copia"
        }

    @Test
    fun `una copia vacia deja la base vacia`() =
        runTest {
            conDatosPrevios()

            restauracion.reemplazarTodo(ContenidoFinanciero())

            contenidoActual() shouldBe ContenidoFinanciero()
        }
}
