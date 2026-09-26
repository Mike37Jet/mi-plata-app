package com.miplata.core.backup

/**
 * Un archivo de backup que no se puede leer.
 *
 * Todo lo que impide restaurar acaba aqui -archivo equivocado, dañado, de una
 * version futura, con un enum que esta app no conoce- con un mensaje pensado
 * para que el usuario entienda que pasa y que puede hacer. Quien restaura casi
 * siempre acaba de perder un movil; no es el momento de un volcado tecnico.
 *
 * Es `open` porque hay un caso que la interfaz tiene que distinguir del resto:
 * [FraseIncorrecta]. Ante un archivo dañado lo unico sensato es abortar; ante
 * una frase mal tecleada, lo sensato es volver a pedirla.
 */
open class BackupInvalido(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)

/**
 * La frase no abre el backup.
 *
 * AES-GCM no puede distinguir "frase equivocada" de "datos manipulados": en los
 * dos casos la etiqueta de autenticacion no cuadra. Pero el checksum del
 * contenido cifrado se comprueba **antes** de intentar descifrar, asi que si se
 * llega hasta aqui el archivo esta entero y lo casi seguro es la frase. El
 * mensaje lo dice asi, sin afirmar mas de lo que se sabe.
 */
class FraseIncorrecta(
    causa: Throwable? = null,
) : BackupInvalido(
        "La frase no abre este backup. Revisa que este bien escrita, " +
            "con mayusculas y espacios incluidos.",
        causa,
    )
