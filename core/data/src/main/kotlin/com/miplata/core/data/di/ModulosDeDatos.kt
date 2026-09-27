package com.miplata.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.miplata.core.data.ajustes.AjustesEnDataStore
import com.miplata.core.data.ajustes.AjustesGuardados
import com.miplata.core.data.ajustes.SerializadorDeAjustes
import com.miplata.core.data.categorias.SembradorDeCategorias
import com.miplata.core.data.database.FabricaDeBaseDeDatos
import com.miplata.core.data.database.MiPlataDatabase
import com.miplata.core.data.database.dao.CategoriaDao
import com.miplata.core.data.database.dao.CierreDao
import com.miplata.core.data.database.dao.CuentaDao
import com.miplata.core.data.database.dao.PlanDao
import com.miplata.core.data.database.dao.TransaccionDao
import com.miplata.core.data.repository.GeneradorDeIdsUuid
import com.miplata.core.data.repository.Reloj
import com.miplata.core.data.repository.RoomCategoriaRepository
import com.miplata.core.data.repository.RoomCierreRepository
import com.miplata.core.data.repository.RoomCuentaRepository
import com.miplata.core.data.repository.RoomPlanRepository
import com.miplata.core.data.repository.RoomRepositorioDeRestauracion
import com.miplata.core.data.repository.RoomTransaccionRepository
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CierreRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.RepositorioDeRestauracion
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import com.miplata.core.domain.usecase.AgruparMovimientosPorDiaUseCase
import com.miplata.core.domain.usecase.CalcularCierreDeMesUseCase
import com.miplata.core.domain.usecase.CalcularPlanPorCuentasUseCase
import com.miplata.core.domain.usecase.CalcularResumenMensualUseCase
import com.miplata.core.domain.usecase.CalcularSaldosDeCuentasUseCase
import com.miplata.core.domain.usecase.CerrarMesUseCase
import com.miplata.core.domain.usecase.CompletarPrimerosPasosUseCase
import com.miplata.core.domain.usecase.GuardarCuentaUseCase
import com.miplata.core.domain.usecase.HayQueDarLaBienvenidaUseCase
import com.miplata.core.domain.usecase.MaterializarPlanDelMesUseCase
import com.miplata.core.domain.usecase.ReabrirMesUseCase
import com.miplata.core.domain.usecase.RegistrarRepartoUseCase
import com.miplata.core.domain.usecase.SembrarCategoriasPorDefectoUseCase
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
    fun proveerCierreDao(db: MiPlataDatabase): CierreDao = db.cierreDao()

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
    fun proveerCierreRepository(
        dao: CierreDao,
        reloj: Reloj,
    ): CierreRepository = RoomCierreRepository(dao, reloj)

    @Provides
    @Singleton
    fun proveerRepositorioDeRestauracion(
        db: MiPlataDatabase,
        reloj: Reloj,
    ): RepositorioDeRestauracion = RoomRepositorioDeRestauracion(db, reloj)

    @Provides
    @Singleton
    fun proveerAjustesRepository(dataStore: DataStore<AjustesGuardados>): AjustesRepository =
        AjustesEnDataStore(dataStore)

    @Provides
    @Singleton
    fun proveerGeneradorDeIds(): GeneradorDeIds = GeneradorDeIdsUuid()

    @Provides
    @Singleton
    fun proveerCalendario(): Calendario = Calendario.DEL_SISTEMA

    @Provides
    @Singleton
    fun proveerSembrador(
        @ApplicationContext context: Context,
        categorias: CategoriaRepository,
        ids: GeneradorDeIds,
    ): SembradorDeCategorias = SembradorDeCategorias(context, SembrarCategoriasPorDefectoUseCase(categorias, ids))
}

/**
 * Los casos de uso del dominio.
 *
 * En un modulo aparte del de repositorios porque son cosas distintas: uno dice
 * de donde salen los datos y este que se hace con ellos. Se instancian aqui, y
 * no con `@Inject` en el dominio, porque `:core:domain` no conoce Hilt -ni
 * ninguna otra cosa de Android- a proposito (docs/01).
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDeCasosDeUso {
    @Provides
    fun proveerAbrirPlan(
        planes: PlanRepository,
        ids: GeneradorDeIds,
    ): AbrirPlanDelMesUseCase = AbrirPlanDelMesUseCase(planes, MaterializarPlanDelMesUseCase(ids))

    // Sin estado y sin dependencias: funciones puras envueltas en una clase.
    @Provides
    fun proveerCalcularResumen(): CalcularResumenMensualUseCase = CalcularResumenMensualUseCase()

    @Provides
    fun proveerCalcularSaldos(): CalcularSaldosDeCuentasUseCase = CalcularSaldosDeCuentasUseCase()

    @Provides
    fun proveerAgruparPorDia(): AgruparMovimientosPorDiaUseCase = AgruparMovimientosPorDiaUseCase()

    @Provides
    fun proveerCalcularPlanPorCuentas(): CalcularPlanPorCuentasUseCase = CalcularPlanPorCuentasUseCase()

    @Provides
    fun proveerCerrarMes(
        transacciones: TransaccionRepository,
        cierres: CierreRepository,
        ids: GeneradorDeIds,
        calendario: Calendario,
    ): CerrarMesUseCase = CerrarMesUseCase(transacciones, cierres, CalcularCierreDeMesUseCase(ids), calendario)

    @Provides
    fun proveerReabrirMes(
        transacciones: TransaccionRepository,
        cierres: CierreRepository,
    ): ReabrirMesUseCase = ReabrirMesUseCase(transacciones, cierres)

    @Provides
    fun proveerGuardarCuenta(cuentas: CuentaRepository): GuardarCuentaUseCase = GuardarCuentaUseCase(cuentas)

    @Provides
    fun proveerRegistrarReparto(
        transacciones: TransaccionRepository,
        ids: GeneradorDeIds,
        calendario: Calendario,
    ): RegistrarRepartoUseCase = RegistrarRepartoUseCase(transacciones, ids, calendario)

    @Provides
    fun proveerHayQueDarLaBienvenida(cuentas: CuentaRepository): HayQueDarLaBienvenidaUseCase =
        HayQueDarLaBienvenidaUseCase(cuentas)

    @Provides
    fun proveerCompletarPrimerosPasos(
        ajustes: AjustesRepository,
        cuentas: CuentaRepository,
        planes: PlanRepository,
        ids: GeneradorDeIds,
        calendario: Calendario,
    ): CompletarPrimerosPasosUseCase = CompletarPrimerosPasosUseCase(ajustes, cuentas, planes, ids, calendario)
}
