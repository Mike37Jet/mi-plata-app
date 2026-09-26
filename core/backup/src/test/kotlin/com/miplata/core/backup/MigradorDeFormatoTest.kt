package com.miplata.core.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/** Añade una marca, para ver en que orden se aplican los pasos. */
private fun marcar(paso: String) =
    MigracionDeFormato { datos ->
        JsonObject(datos + ("pasos" to JsonPrimitive((datos["pasos"]?.jsonPrimitive?.content ?: "") + paso)))
    }

/**
 * La mecanica de las migraciones de formato.
 *
 * Hoy no hay ninguna migracion real -solo existe el formato 1-, asi que se
 * prueba con migraciones de mentira. Lo que se comprueba es la cadena: que se
 * aplica entera, en orden, desde la version del archivo, y que falla en voz alta
 * si falta un eslabon.
 */
class MigradorDeFormatoTest {
    private val datos = buildJsonObject { put("valor", 1) }

    @Test
    fun `una copia del formato actual pasa sin cambios`() {
        MigradorDeFormato(migraciones = emptyMap(), versionActual = 1).migrar(datos, desde = 1) shouldBe datos
    }

    @Test
    fun `una copia antigua pasa por todos los pasos, en orden`() {
        val migrador = MigradorDeFormato(mapOf(1 to marcar("a"), 2 to marcar("b")), versionActual = 3)

        migrador.migrar(datos, desde = 1)["pasos"]!!.jsonPrimitive.content shouldBe "ab"
    }

    // Una copia del formato 2 ya tiene hecho el paso del 1 al 2: no se repite.
    @Test
    fun `se empieza en la version del archivo, no en la primera`() {
        val migrador = MigradorDeFormato(mapOf(1 to marcar("a"), 2 to marcar("b")), versionActual = 3)

        migrador.migrar(datos, desde = 2)["pasos"]!!.jsonPrimitive.content shouldBe "b"
    }

    // Subir la version sin escribir la migracion es un fallo de programacion.
    // Mejor no restaurar que restaurar algo a medio convertir.
    @Test
    fun `si falta un paso de la cadena la copia se rechaza`() {
        val migrador = MigradorDeFormato(mapOf(1 to marcar("a")), versionActual = 3)

        val error = shouldThrow<BackupInvalido> { migrador.migrar(datos, desde = 1) }

        error.message!! shouldContain "del formato 2 al 3"
    }

    @Test
    fun `una version imposible se rechaza`() {
        val migrador = MigradorDeFormato(emptyMap(), versionActual = 1)

        shouldThrow<BackupInvalido> { migrador.migrar(datos, desde = 0) }
        shouldThrow<BackupInvalido> { migrador.migrar(datos, desde = 2) }
    }

    // De punta a punta: la lectura de un archivo pasa de verdad por el
    // migrador, y con la version que dice el archivo. Se simula una app de un
    // formato futuro cuya migracion renombra las cuentas.
    @Test
    fun `leer una copia antigua aplica la migracion`() {
        val datosDeHoy =
            DatosDelBackup(
                ajustes = AjustesDto("EUR", 1, "CLARO"),
                cuentas = listOf(CuentaDto("c", "Cuenta vieja", "BANCARIA", 100, "EUR")),
            )
        val copiaDeHoy =
            archivoRapido().escribirABytes(
                ContenidoDelBackup(datosDeHoy, 1, "0.1.0", 1L, "Pixel"),
                FRASE,
            )
        val renombrarCuentas =
            MigracionDeFormato { json ->
                val cuentas =
                    json["cuentas"]!!.jsonArray.map { cuenta ->
                        JsonObject(cuenta.jsonObject + ("nombre" to JsonPrimitive("Migrada")))
                    }
                JsonObject(json + ("cuentas" to kotlinx.serialization.json.JsonArray(cuentas)))
            }
        val appDelFuturo =
            ArchivoDeBackup(
                cifrador = CifradorDeBackup(iteraciones = ITERACIONES_DE_TEST),
                migrador = MigradorDeFormato(mapOf(1 to renombrarCuentas), versionActual = 2),
            )

        val leido = appDelFuturo.leer(ByteArrayInputStream(copiaDeHoy), FRASE)

        leido.datos.cuentas
            .single()
            .nombre shouldBe "Migrada"
    }

    // Guardian: subir VERSION_DEL_FORMATO sin añadir la migracion desde la
    // version anterior hace fallar este test. Sin el, las copias de la version
    // anterior dejarian de abrirse en silencio.
    @Test
    fun `hay una migracion por cada version anterior a la actual`() {
        MIGRACIONES_DE_FORMATO.keys shouldBe (1 until VERSION_DEL_FORMATO).toSet()
    }
}
