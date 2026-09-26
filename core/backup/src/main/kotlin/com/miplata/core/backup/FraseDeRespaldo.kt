package com.miplata.core.backup

/**
 * La frase con la que se cifra el backup.
 *
 * **No se guarda en ninguna parte** (docs/05): ni en la base, ni en los ajustes,
 * ni en el Keystore. Si se pierde, el backup se pierde. Es el precio de que un
 * archivo que va a acabar en la nube de un tercero sea ilegible para ese tercero,
 * y la interfaz tiene que decirlo con todas sus letras antes de continuar.
 *
 * Se guarda como `CharArray` y no como `String` para poder **borrarla** de
 * memoria al terminar ([olvidar]). Un `String` es inmutable y se queda en el
 * heap hasta que al recolector le parezca bien. La JVM no garantiza que el
 * borrado llegue a todas las copias, pero no dejar la frase entera en un objeto
 * que no se puede tocar es lo minimo razonable.
 */
class FraseDeRespaldo(
    frase: CharArray,
) {
    internal val caracteres: CharArray = frase.copyOf()

    init {
        require(caracteres.any { !it.isWhitespace() }) { "La frase no puede estar vacia" }
        require(caracteres.size >= LONGITUD_MINIMA) {
            "La frase necesita al menos $LONGITUD_MINIMA caracteres; mejor aun, tres o cuatro palabras"
        }
    }

    /** Borra la frase de memoria. A partir de aqui ya no sirve para nada. */
    fun olvidar() {
        caracteres.fill('\u0000')
    }

    /** Nunca se imprime: un log o un informe de errores no es sitio para ella. */
    override fun toString(): String = "FraseDeRespaldo(***)"

    companion object {
        /**
         * Doce caracteres como minimo.
         *
         * Con 600.000 iteraciones de PBKDF2 cada intento de adivinarla cuesta
         * medio segundo de CPU; por debajo de esta longitud, ni asi basta contra
         * un diccionario. Y cuatro palabras normales ya la superan con holgura,
         * que es lo que la interfaz va a recomendar.
         */
        const val LONGITUD_MINIMA: Int = 12

        fun de(texto: String): FraseDeRespaldo = FraseDeRespaldo(texto.toCharArray())
    }
}
