package com.miplata.feature.backup

import com.miplata.core.backup.FraseDeRespaldo

/** Lo que impide exportar todavia, en el orden en que conviene decirlo. */
enum class ProblemaConLaFrase {
    DEMASIADO_CORTA,
    NO_COINCIDEN,

    /** El usuario no ha confirmado que entiende que sin frase no hay backup. */
    SIN_ASUMIR_EL_RIESGO,
}

/**
 * Comprueba la frase antes de dejar exportar.
 *
 * Se pide dos veces porque un error al teclearla no se nota hasta el dia de
 * restaurar, y ese dia ya no tiene arreglo: la frase no se guarda en ningun
 * sitio. Y se pide asumir el riesgo de forma explicita porque docs/05 exige
 * decirlo "con todas sus letras, al menos dos veces": una en el texto de la
 * pantalla, otra en la casilla que hay que marcar.
 *
 * @return el primer problema, o nulo si se puede exportar.
 */
fun problemaCon(
    frase: CharSequence,
    confirmacion: CharSequence,
    riesgoAsumido: Boolean,
): ProblemaConLaFrase? =
    when {
        frase.length < FraseDeRespaldo.LONGITUD_MINIMA || frase.isBlank() -> ProblemaConLaFrase.DEMASIADO_CORTA
        frase.toString() != confirmacion.toString() -> ProblemaConLaFrase.NO_COINCIDEN
        !riesgoAsumido -> ProblemaConLaFrase.SIN_ASUMIR_EL_RIESGO
        else -> null
    }
