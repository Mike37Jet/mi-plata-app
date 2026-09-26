package com.miplata.feature.backup.recordatorio

import android.content.Context
import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.domain.repository.AjustesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ModuloDelRecordatorio {
    @Provides
    fun proveerComprobador(
        @ApplicationContext context: Context,
        ajustes: AjustesRepository,
        recolector: RecolectorDeDatos,
    ): ComprobadorDeRecordatorio =
        ComprobadorDeRecordatorio(
            ajustes = ajustes,
            recolector = recolector,
            registro = RegistroEnPreferencias(context),
            notificador = NotificadorEnAndroid(context),
        )
}
