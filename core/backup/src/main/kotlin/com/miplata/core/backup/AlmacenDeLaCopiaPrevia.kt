package com.miplata.core.backup

import java.io.InputStream
import java.io.OutputStream

/**
 * Donde se guarda lo que habia antes de restaurar, para poder deshacerlo.
 *
 * docs/05 exige una copia de seguridad automatica del estado actual antes de
 * escribir nada. No protege de un fallo a medias -de eso se encarga la
 * transaccion de la base-, sino de algo mas corriente: restaurar **la copia
 * equivocada**, una vieja, y perder lo apuntado despues. Con esto, "deshacer"
 * devuelve la app a como estaba un segundo antes de restaurar.
 *
 * La implementacion vive en `:app`, que sabe donde guardar archivos privados y
 * con que clave cifrarlos.
 */
interface AlmacenDeLaCopiaPrevia {
    /**
     * La frase con la que se cifra la copia previa.
     *
     * No la elige el usuario -no se le puede pedir otra frase justo antes de
     * restaurar- y tampoco puede ser fija. Ver la implementacion para de donde
     * sale.
     */
    fun frase(): FraseDeRespaldo

    /**
     * Guarda una copia nueva en lugar de la anterior.
     *
     * Tiene que ser atomico: si [escribir] falla a mitad, la copia previa que
     * hubiera sigue intacta. Una copia previa a medias es peor que ninguna,
     * porque el dia que hace falta no se puede abrir.
     */
    fun guardar(escribir: (OutputStream) -> Unit)

    /** La copia previa, o nulo si no hay ninguna. */
    fun abrir(): InputStream?

    fun borrar()

    /**
     * Escribe una copia **aparte**, sin tocar la actual todavia.
     *
     * Es la primera mitad de un cambio en dos fases, para deshacer: hay que
     * guardar lo que hay ahora sin perder la copia previa, que aun hace falta
     * por si falla la sustitucion. Despues se llama a [confirmarSustituta] o a
     * [descartarSustituta].
     */
    fun prepararSustituta(escribir: (OutputStream) -> Unit)

    /** La copia preparada pasa a ser la copia previa, de forma atomica. */
    fun confirmarSustituta()

    /** Tira la copia preparada. La copia previa queda como estaba. */
    fun descartarSustituta()
}

/** De donde sale una copia: lo que va en su manifiesto ademas de los datos. */
data class Procedencia(
    val versionDelEsquema: Int,
    val versionDeLaApp: String,
    val dispositivo: String,
)
