package com.miplata.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * El despachador para trabajo que bloquea un hilo un buen rato: escribir un
 * archivo, o derivar una clave con 600.000 iteraciones de PBKDF2.
 *
 * Es `Dispatchers.IO` y no `Default` a proposito, aunque derivar la clave sea
 * calculo puro. `Default` tiene tantos hilos como nucleos: tener uno ocupado dos
 * segundos seguidos le quita un cuarto de la capacidad a toda la app en un movil
 * de cuatro nucleos. `IO` esta hecho para hilos que se quedan bloqueados.
 *
 * Se inyecta en vez de escribirse a mano para que los tests lo sustituyan por el
 * despachador de prueba: con `Dispatchers.IO` fijo en el codigo, el test tiene
 * que esperar a hilos reales y acaba dependiendo de cuanto tarde la maquina.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DespachadorBloqueante

@Module
@InstallIn(SingletonComponent::class)
object ModuloDeDespachadores {
    @Provides
    @DespachadorBloqueante
    fun proveerDespachadorBloqueante(): CoroutineDispatcher = Dispatchers.IO
}
