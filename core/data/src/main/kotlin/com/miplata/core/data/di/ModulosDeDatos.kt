package com.miplata.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.miplata.core.data.ajustes.AjustesEnDataStore
import com.miplata.core.data.ajustes.AjustesGuardados
import com.miplata.core.data.ajustes.SerializadorDeAjustes
import com.miplata.core.data.database.FabricaDeBaseDeDatos
import com.miplata.core.data.database.MiPlataDatabase
import com.miplata.core.data.database.dao.CategoriaDao
import com.miplata.core.data.database.dao.CuentaDao
import com.miplata.core.data.database.dao.PlanDao
import com.miplata.core.data.database.dao.TransaccionDao
import com.miplata.core.data.repository.GeneradorDeIdsUuid
import com.miplata.core.data.repository.Reloj
import com.miplata.core.data.repository.RoomCategoriaRepository
import com.miplata.core.data.repository.RoomCuentaRepository
import com.miplata.core.data.repository.RoomPlanRepository
import com.miplata.core.data.repository.RoomTransaccionRepository
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * El grafo de la capa de datos.
 *
 * Todo lo que se expone hacia arriba son **interfaces del dominio**. Los modulos
 * `:feature:*` piden un `CuentaRepository` y reciben el de Room sin saberlo, ni
 * poder saberlo: su classpath no incluye `:core:data` (docs/04).
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDeBaseDeDatos {
    @Provides
    @Singleton
    fun proveerBaseDeDatos(
        @ApplicationContext context: Context,
    ): MiPlataDatabase = FabricaDeBaseDeDatos.crear(context)

    @Provides
    fun proveerCuentaDao(db: MiPlataDatabase): CuentaDao = db.cuentaDao()

    @Provides
    fun proveerCategoriaDao(db: MiPlataDatabase): CategoriaDao = db.categoriaDao()

    @Provides
    fun proveerTransaccionDao(db: MiPlataDatabase): TransaccionDao = db.transaccionDao()

    @Provides
    fun proveerPlanDao(db: MiPlataDatabase): PlanDao = db.planDao()

    @Provides
    @Singleton
    fun proveerReloj(): Reloj = Reloj.DEL_SISTEMA
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloDeAjustes {
    private const val ARCHIVO = "ajustes.json"

    @Provides
    @Singleton
    fun proveerDataStore(
        @ApplicationContext context: Context,
    ): DataStore<AjustesGuardados> =
        DataStoreFactory.create(serializer = SerializadorDeAjustes) {
            context.dataStoreFile(ARCHIVO)
        }
}

/**
 * Las implementaciones que cumplen cada contrato del dominio.
 *
 * Son `@Provides` y no `@Binds` porque las clases de Room reciben su DAO y su
 * reloj por constructor sin llevar `@Inject`: el dominio no conoce Hilt y la
 * capa de datos tampoco tiene por que ensuciarse con anotaciones de inyeccion
 * en cada clase.
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDeRepositorios {
    @Provides
    @Singleton
    fun proveerCuentaRepository(
        dao: CuentaDao,
        reloj: Reloj,
    ): CuentaRepository = RoomCuentaRepository(dao, reloj)

    @Provides
    @Singleton
    fun proveerCategoriaRepository(
        dao: CategoriaDao,
        reloj: Reloj,
    ): CategoriaRepository = RoomCategoriaRepository(dao, reloj)

    @Provides
    @Singleton
    fun proveerTransaccionRepository(
        dao: TransaccionDao,
        reloj: Reloj,
    ): TransaccionRepository = RoomTransaccionRepository(dao, reloj)

    @Provides
    @Singleton
    fun proveerPlanRepository(
        dao: PlanDao,
        reloj: Reloj,
    ): PlanRepository = RoomPlanRepository(dao, reloj)

    @Provides
    @Singleton
    fun proveerAjustesRepository(dataStore: DataStore<AjustesGuardados>): AjustesRepository =
        AjustesEnDataStore(dataStore)

    @Provides
    @Singleton
    fun proveerGeneradorDeIds(): GeneradorDeIds = GeneradorDeIdsUuid()
}
