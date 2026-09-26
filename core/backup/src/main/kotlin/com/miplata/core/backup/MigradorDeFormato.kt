package com.miplata.core.backup

import kotlinx.serialization.json.JsonObject

/** Convierte los datos de una version del formato a la siguiente. */
fun interface MigracionDeFormato {
    fun migrar(datos: JsonObject): JsonObject
}

/**
 * Las migraciones de formato que existen, por version de origen: la entrada
 * `1` convierte datos del formato 1 al 2, la `2` del 2 al 3, y asi.
 *
 * **Hoy esta vacia**, porque solo existe el formato 1. El dia que se suba
 * [VERSION_DEL_FORMATO] hay que añadir aqui la migracion desde la version
 * anterior, y commitear un archivo de ejemplo del formato nuevo en
 * `src/test/resources/backups/`. Dos tests vigilan las dos cosas: sin ellas, el
 * build no pasa.
 *
 * Se migra el JSON y no los objetos, a proposito: los DTOs de hoy solo saben
 * leer el formato de hoy. Un campo renombrado o partido en dos en la version
 * nueva no cabria en ellos; en el JSON si se puede mover a mano.
 */
val MIGRACIONES_DE_FORMATO: Map<Int, MigracionDeFormato> = emptyMap()

/**
 * Lleva los datos de una copia antigua hasta el formato actual, paso a paso.
 *
 * En cadena y no con una conversion directa de cada version a la actual: con
 * cinco versiones harian falta cinco conversiones que mantener, y cada cambio de
 * formato obligaria a tocarlas todas. En cadena, cada version solo sabe pasar a
 * la siguiente.
 */
class MigradorDeFormato(
    private val migraciones: Map<Int, MigracionDeFormato> = MIGRACIONES_DE_FORMATO,
    private val versionActual: Int = VERSION_DEL_FORMATO,
) {
    /**
     * @param desde la version del formato en que se escribio la copia.
     * @throws BackupInvalido si la version no es valida o falta un paso de la
     *   cadena. Esto ultimo es un fallo de programacion -alguien subio la
     *   version sin escribir la migracion-, pero se trata como copia ilegible:
     *   mejor no restaurar que restaurar algo a medio convertir.
     */
    fun migrar(
        datos: JsonObject,
        desde: Int,
    ): JsonObject {
        if (desde < 1 || desde > versionActual) {
            throw BackupInvalido("La copia declara una version de formato imposible: $desde")
        }
        return (desde until versionActual).fold(datos) { actuales, version ->
            val paso =
                migraciones[version]
                    ?: throw BackupInvalido("Esta app no sabe convertir copias del formato $version al ${version + 1}")
            paso.migrar(actuales)
        }
    }
}
