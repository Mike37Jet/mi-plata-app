package com.miplata.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** El paquete de la variante de release, que es la que se perfila y se mide. */
internal const val PAQUETE = "com.miplata.app"

private const val ESPERA_MS = 5_000L

/**
 * Si la app arranca en la bienvenida -la primera vez, sin datos-, la completa
 * con una cuenta, sin sobres y sin ingreso. Las siguientes veces ya no aparece.
 *
 * Se pasa por la bienvenida porque tambien es codigo que se ejecuta al
 * arrancar: la primera impresion es la que mas cuesta que vaya lenta.
 */
internal fun MacrobenchmarkScope.completarLaBienvenidaSiSale() {
    val empezar = device.wait(Until.findObject(By.text("Empezar")), ESPERA_MS) ?: return
    empezar.click()
    device.wait(Until.findObject(By.text("Siguiente")), ESPERA_MS)?.click()
    device.wait(Until.findObject(By.clazz("android.widget.EditText")), ESPERA_MS)?.text = "Cartera"
    device.wait(Until.findObject(By.text("Siguiente")), ESPERA_MS)?.click()
    // Los sobres: se deja sin marcar ninguno.
    device.wait(Until.findObject(By.text("Libertad financiera")), ESPERA_MS)
    device.findObject(By.text("Siguiente"))?.click()
    device.wait(Until.findObject(By.text("Lo haré después")), ESPERA_MS)?.click()
    device.wait(Until.hasObject(By.text("Resumen")), ESPERA_MS)
}

/**
 * Las pestañas, desplazando cada una: es el camino que se repite cada
 * vez que se abre la app, y el que el perfil tiene que tener compilado.
 */
internal fun MacrobenchmarkScope.recorrerLasPestanas() {
    listOf("Resumen", "Plan", "Cuentas").forEach { pestana ->
        device.wait(Until.findObject(By.text(pestana)), ESPERA_MS)?.click()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.let { lista ->
            lista.fling(Direction.DOWN)
            lista.fling(Direction.UP)
        }
    }
}
