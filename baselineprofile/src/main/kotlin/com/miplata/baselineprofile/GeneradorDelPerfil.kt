package com.miplata.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera el Baseline Profile: la lista de clases y metodos que la app usa al
 * arrancar y al moverse por las pestañas.
 *
 * Con esa lista, Android los compila a codigo maquina al instalar el APK, en
 * vez de interpretarlos las primeras veces hasta que el JIT se entera de que
 * son importantes. Sin perfil, cada arranque en frio de las primeras semanas
 * paga ese peaje.
 *
 * Se ejecuta con `./gradlew :app:generateBaselineProfile` y un dispositivo
 * conectado (Android 13+, o uno con root). Deja el resultado en
 * `app/src/release/generated/baselineProfiles/`, que se commitea.
 */
@RunWith(AndroidJUnit4::class)
class GeneradorDelPerfil {
    @get:Rule
    val regla = BaselineProfileRule()

    @Test
    fun generar() =
        regla.collect(packageName = PAQUETE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            completarLaBienvenidaSiSale()
            recorrerLasPestanas()
        }
}
