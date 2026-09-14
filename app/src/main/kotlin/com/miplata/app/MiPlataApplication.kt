package com.miplata.app

import android.app.Application
import com.miplata.core.data.categorias.SembradorDeCategorias
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Punto de entrada del grafo de Hilt.
 *
 * Lo unico que hace al arrancar es sembrar las categorias iniciales, y lo hace
 * **fuera del hilo principal**: retrasar el primer pixel por algo que al usuario
 * le da igual que tarde 50ms mas seria pagar arranque a cambio de nada. El use
 * case es idempotente, asi que en el segundo arranque no hace nada.
 */
@HiltAndroidApp
class MiPlataApplication : Application() {
    @Inject
    lateinit var sembrador: SembradorDeCategorias

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        alcance.launch { sembrador.sembrarSiHaceFalta() }
    }
}
