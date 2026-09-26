package com.miplata.core.backup

import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.RepositorioDeRestauracion
import java.io.IOException

/**
 * Los datos se restauraron, pero los ajustes no.
 *
 * Viven en otro almacen y no pueden entrar en la misma transaccion que la base
 * (docs/05). Es un caso rarisimo -que falle escribir un archivo de preferencias
 * justo despues de haber escrito la base- y no deja nada inconsistente en el
 * dinero, pero hay que contarlo: la moneda o el dia de cobro pueden no ser los
 * de la copia. Deshacer sigue disponible.
 */
class RestauracionIncompleta(
    causa: Throwable,
) : Exception(
        "Tus datos se restauraron, pero no se pudieron recuperar los ajustes " +
            "(moneda, dia de cobro, tema). Revisalos, o deshaz la restauracion.",
        causa,
    )

/**
 * Restaura una copia sustituyendo todos los datos, y permite deshacerlo.
 *
 * Es la operacion mas peligrosa de la app, asi que el orden es el que marca
 * docs/05 y no otro:
 *
 * 1. **Copia de seguridad de lo que hay ahora.** Si no se puede guardar, se para
 *    aqui: no se toca nada sin poder deshacerlo.
 * 2. **Sustitucion de los datos en una sola transaccion.** O entra todo, o no
 *    entra nada.
 * 3. **Ajustes**, aparte, porque viven en otro almacen.
 *
 * Recibe una copia **ya leida y descifrada**: cuando llega aqui, la frase ya se
 * comprobo y el usuario ya vio el resumen previo. Esta clase no pregunta nada.
 */
class RestauradorDeCopias(
    private val recolector: RecolectorDeDatos,
    private val archivo: ArchivoDeBackup,
    private val repositorio: RepositorioDeRestauracion,
    private val ajustes: AjustesRepository,
    private val copiaPrevia: AlmacenDeLaCopiaPrevia,
) {
    /**
     * @param procedencia y [ahoraEnMillis] van al manifiesto de la copia previa.
     * @throws RestauracionIncompleta si los datos entraron pero los ajustes no.
     * @throws Exception de cualquier otro tipo si no se pudo restaurar. En ese
     *   caso **no se cambio nada**.
     */
    suspend fun restaurar(
        copia: BackupLeido,
        procedencia: Procedencia,
        ahoraEnMillis: Long,
    ) {
        guardarLoQueHayAhora(procedencia, ahoraEnMillis)

        repositorio.reemplazarTodo(copia.datos.aContenidoFinanciero())

        escribirAjustes {
            copia.datos.ajustes
                .aDominio()
                // La fecha de la ultima copia es la de ESTA copia. La que trae
                // dentro es la de la anterior: se apunto al exportar, antes de
                // que esta existiera.
                .copy(ultimoBackupEnMillis = copia.manifiesto.creadoEnMillis)
        }
    }

    /**
     * Devuelve los datos a como estaban en la copia previa, **guardando antes lo
     * que hay ahora**.
     *
     * Deshacer tambien sustituye todos los datos, asi que tiene el mismo riesgo
     * que restaurar: quien restauro, apunto una semana de gastos y entonces
     * deshace, perderia esa semana sin remedio si no se guardara antes. Por eso
     * lo de ahora pasa a ser la nueva copia previa, y deshacer se puede deshacer.
     *
     * Va en dos fases para no quedarse nunca sin copia:
     *
     * 1. Lo de ahora se escribe **aparte**. La copia previa sigue intacta.
     * 2. Se sustituyen los datos. Si falla, se tira lo escrito aparte y todo
     *    queda como estaba: datos y copia previa.
     * 3. Solo entonces lo escrito aparte pasa a ser la copia previa.
     *
     * @throws IllegalStateException si no hay copia previa.
     * @throws RestauracionIncompleta si los datos volvieron pero los ajustes no.
     */
    suspend fun deshacer(
        procedencia: Procedencia,
        ahoraEnMillis: Long,
    ) {
        val previa = leerCopiaPrevia() ?: error("No hay datos anteriores a los que volver")

        val frase = copiaPrevia.frase()
        val ahora = contenidoActual(procedencia, ahoraEnMillis)
        try {
            copiaPrevia.prepararSustituta { destino -> archivo.escribir(ahora, frase, destino) }
        } finally {
            frase.olvidar()
        }

        var sustituido = false
        try {
            repositorio.reemplazarTodo(previa.datos.aContenidoFinanciero())
            sustituido = true
        } finally {
            // Cualquier fallo al sustituir -o una cancelacion- deja los datos
            // como estaban, porque es una transaccion. Lo unico que queda por
            // hacer es no confirmar la sustituta.
            if (!sustituido) copiaPrevia.descartarSustituta()
        }

        // Los datos ya volvieron: se confirma la sustituta ANTES de los ajustes,
        // para que un fallo en ellos no impida volver a lo de hace un momento.
        copiaPrevia.confirmarSustituta()

        // Aqui si se respeta la fecha que trae dentro: es la de la ultima copia
        // que el usuario hizo de verdad antes de restaurar.
        escribirAjustes { previa.datos.ajustes.aDominio() }
    }

    /** El manifiesto de la copia previa, para saber si se puede deshacer y de cuando es. */
    fun copiaPreviaDisponible(): Manifiesto? = copiaPrevia.abrir()?.use { archivo.leerManifiesto(it) }

    private suspend fun contenidoActual(
        procedencia: Procedencia,
        ahoraEnMillis: Long,
    ) = ContenidoDelBackup(
        datos = recolector.recolectar(),
        versionDelEsquema = procedencia.versionDelEsquema,
        versionDeLaApp = procedencia.versionDeLaApp,
        creadoEnMillis = ahoraEnMillis,
        dispositivo = procedencia.dispositivo,
    )

    private suspend fun guardarLoQueHayAhora(
        procedencia: Procedencia,
        ahoraEnMillis: Long,
    ) {
        val contenido = contenidoActual(procedencia, ahoraEnMillis)
        val frase = copiaPrevia.frase()
        try {
            copiaPrevia.guardar { destino -> archivo.escribir(contenido, frase, destino) }
        } finally {
            frase.olvidar()
        }
    }

    private fun leerCopiaPrevia(): BackupLeido? {
        val origen = copiaPrevia.abrir() ?: return null
        val frase = copiaPrevia.frase()
        return try {
            origen.use { archivo.leer(it, frase) }
        } finally {
            frase.olvidar()
        }
    }

    private suspend fun escribirAjustes(nuevos: () -> Ajustes) {
        try {
            ajustes.guardar(nuevos())
        } catch (e: IOException) {
            throw RestauracionIncompleta(e)
        }
    }
}
