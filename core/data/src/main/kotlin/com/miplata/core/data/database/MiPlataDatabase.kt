package com.miplata.core.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.miplata.core.data.database.dao.CargaDeRestauracionDao
import com.miplata.core.data.database.dao.CategoriaDao
import com.miplata.core.data.database.dao.CierreDao
import com.miplata.core.data.database.dao.CuentaDao
import com.miplata.core.data.database.dao.PlanDao
import com.miplata.core.data.database.dao.TransaccionDao
import com.miplata.core.data.database.dao.VaciadoDeRestauracionDao
import com.miplata.core.data.database.entity.CategoriaEntity
import com.miplata.core.data.database.entity.CierreEntity
import com.miplata.core.data.database.entity.CuentaEntity
import com.miplata.core.data.database.entity.LineaDePlanEntity
import com.miplata.core.data.database.entity.PlanEntity
import com.miplata.core.data.database.entity.SaldoDeCierreEntity
import com.miplata.core.data.database.entity.TransaccionEntity

/**
 * La base de datos de la app.
 *
 * `exportSchema` esta activo y el esquema de cada version se escribe a JSON en
 * `core/data/schemas/` **y se commitea**. Eso es lo que permite escribir tests
 * de migracion de verdad: con el esquema de la version N en el repositorio, un
 * test puede crear una base vieja, aplicar la migracion y comprobar que los
 * datos siguen ahi. Sin los esquemas commiteados, una migracion solo se puede
 * probar a mano y a posteriori, cuando ya se han perdido los datos de alguien.
 *
 * Sin `fallbackToDestructiveMigration`, ni ahora ni nunca: esa opcion borra la
 * base del usuario cuando falta una migracion. En una app de finanzas eso no es
 * una estrategia de recuperacion, es el peor fallo posible.
 */
@Database(
    entities = [
        CuentaEntity::class,
        CategoriaEntity::class,
        TransaccionEntity::class,
        PlanEntity::class,
        LineaDePlanEntity::class,
        CierreEntity::class,
        SaldoDeCierreEntity::class,
    ],
    version = MiPlataDatabase.VERSION,
    exportSchema = true,
    // Solo columnas con valor por defecto y tablas nuevas: Room escribe la
    // migracion desde los esquemas commiteados, y `MigracionesTest` la
    // comprueba con una base de la version 1 con datos.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MiPlataDatabase : RoomDatabase() {
    abstract fun cuentaDao(): CuentaDao

    abstract fun categoriaDao(): CategoriaDao

    abstract fun transaccionDao(): TransaccionDao

    abstract fun planDao(): PlanDao

    abstract fun cierreDao(): CierreDao

    internal abstract fun vaciadoDeRestauracionDao(): VaciadoDeRestauracionDao

    internal abstract fun cargaDeRestauracionDao(): CargaDeRestauracionDao

    companion object {
        const val NOMBRE = "mi-plata.db"

        /**
         * La version del esquema, en un solo sitio.
         *
         * La lee Room en la anotacion de arriba y la lee el backup para
         * anotarla en el manifiesto: asi, al restaurar un archivo de otra
         * version, se sabe de que esquema salieron sus datos. Con el numero
         * escrito dos veces, el dia que se suba uno y se olvide el otro, el
         * backup mentiria sobre su origen.
         */
        const val VERSION = 2
    }
}
