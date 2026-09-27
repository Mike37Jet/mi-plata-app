package com.miplata.core.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.miplata.core.data.mapper.aDominio
import com.miplata.core.domain.model.RolDeCuenta
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val BASE = "migraciones.db"

/**
 * Las migraciones del esquema, con datos de verdad dentro.
 *
 * Crea una base con el esquema commiteado de la version vieja, la rellena con
 * SQL a mano -como la dejo la app de entonces-, migra y comprueba que los datos
 * siguen ahi y se leen bien. Es lo que separa una migracion probada de una que
 * se descubre rota en el movil de alguien.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracionesTest {
    @get:Rule
    val ayudante = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MiPlataDatabase::class.java)

    @Test
    fun `de la 1 a la 2 las cuentas quedan independientes y los movimientos sin cierre`() =
        runTest {
            crearBaseDeLaVersion1()

            ayudante.runMigrationsAndValidate(BASE, MiPlataDatabase.VERSION, true).close()

            val db =
                Room
                    .databaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        MiPlataDatabase::class.java,
                        BASE,
                    ).build()
            try {
                val cuenta = db.cuentaDao().obtener("c1")!!.aDominio()
                cuenta.rol shouldBe RolDeCuenta.Independiente
                cuenta.nombre shouldBe "Pichincha"

                val movimiento =
                    db
                        .transaccionDao()
                        .observarTodas()
                        .first()
                        .single()
                        .aDominio()
                movimiento.ajusteDeCierre.shouldBeNull()
                movimiento.nota shouldBe "Corte"

                db.cierreDao().observarTodos().first() shouldBe emptyList()
            } finally {
                db.close()
            }
        }

    /** Una cuenta y un movimiento, escritos con el SQL de la version 1. */
    private fun crearBaseDeLaVersion1() {
        ayudante.createDatabase(BASE, 1).apply {
            execSQL(
                """
                INSERT INTO cuentas (id, nombre, tipo, saldoInicialCentavos, moneda, incluirEnTotal, archivada,
                    creadaEn, actualizadaEn, eliminadaEn)
                VALUES ('c1', 'Pichincha', 'BANCARIA', 63300, 'USD', 1, 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transacciones (id, fecha, montoCentavos, tipo, cuentaOrigenId, cuentaDestinoId,
                    categoriaId, lineaDePlanId, nota, creadaEn, actualizadaEn, eliminadaEn)
                VALUES ('t1', '2026-09-10', 1200, 'GASTO', 'c1', NULL, NULL, NULL, 'Corte', 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }
    }
}
