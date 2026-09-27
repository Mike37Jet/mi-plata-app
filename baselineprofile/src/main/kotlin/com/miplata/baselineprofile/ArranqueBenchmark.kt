package com.miplata.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cuanto tarda la app en arrancar en frio, sin perfil y con el.
 *
 * - [sinCompilar]: como la primera vez tras instalar sin perfil. Todo se
 *   interpreta hasta que el JIT lo compila.
 * - [conElPerfil]: lo que compila el Baseline Profile, que es lo que tendra el
 *   APK recien instalado gracias a `profileinstaller`.
 *
 * La diferencia entre los dos es lo que gana el perfil. Se ejecuta con
 * `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ArranqueBenchmark {
    @get:Rule
    val regla = MacrobenchmarkRule()

    @Test
    fun sinCompilar() = arrancar(CompilationMode.None())

    @Test
    fun conElPerfil() = arrancar(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun arrancar(modo: CompilationMode) =
        regla.measureRepeated(
            packageName = PAQUETE,
            metrics = listOf(StartupTimingMetric()),
            iterations = ITERACIONES,
            startupMode = StartupMode.COLD,
            compilationMode = modo,
            // Si es la primera vez, se completa la bienvenida antes de medir:
            // las dos mediciones tienen que arrancar en la misma pantalla.
            setupBlock = {
                pressHome()
                startActivityAndWait()
                completarLaBienvenidaSiSale()
                pressHome()
            },
        ) {
            startActivityAndWait()
        }

    private companion object {
        const val ITERACIONES = 10
    }
}
