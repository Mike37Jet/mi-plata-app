package com.miplata.core.data.repository

/**
 * De donde sale la hora.
 *
 * Es una interfaz y no una llamada directa a `System.currentTimeMillis()` para
 * que los tests puedan fijarla. Un repositorio que lee el reloj del sistema por
 * dentro obliga a escribir aserciones del tipo "creadoEn deberia estar cerca de
 * ahora", que es como se escriben los tests que fallan un martes cualquiera.
 */
fun interface Reloj {
    fun ahoraEnMillis(): Long

    companion object {
        val DEL_SISTEMA = Reloj { System.currentTimeMillis() }
    }
}
