package com.miplata.core.data.mapper

/**
 * Una fila guardada no se puede traducir al dominio.
 *
 * Significa corrupcion: un enum con un valor que no existe, un mes que no es un
 * mes. Se falla en vez de adivinar, porque adivinar en una app de finanzas
 * termina en cifras plausibles y equivocadas.
 */
class DatoGuardadoInvalido(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)
