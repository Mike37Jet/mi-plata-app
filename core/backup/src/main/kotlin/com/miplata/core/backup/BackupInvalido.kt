package com.miplata.core.backup

/**
 * Un archivo de backup que no se puede leer.
 *
 * Todo lo que impide restaurar acaba aqui -archivo equivocado, corrupto, de una
 * version futura, con un enum que esta app no conoce- con un mensaje pensado
 * para que el usuario entienda que pasa y que puede hacer. Quien restaura casi
 * siempre acaba de perder un movil; no es el momento de un volcado tecnico.
 */
class BackupInvalido(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)
