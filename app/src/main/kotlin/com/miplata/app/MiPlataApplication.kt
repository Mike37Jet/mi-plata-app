package com.miplata.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Punto de entrada del grafo de Hilt.
 *
 * Deliberadamente vacía: la inicialización de librerías se hace con
 * `androidx.startup` o de forma perezosa en sus propios módulos, no
 * amontonándola en `onCreate()`. Todo lo que se ponga aquí se ejecuta antes de
 * que el usuario vea el primer pixel.
 */
@HiltAndroidApp
class MiPlataApplication : Application()
