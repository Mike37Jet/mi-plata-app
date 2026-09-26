package com.miplata.feature.backup

import com.miplata.core.backup.Manifiesto
import com.miplata.core.backup.Recuento

/**
 * Por donde va la restauracion.
 *
 * Es una secuencia con un orden fijo -elegir, ver que trae, confirmar,
 * restaurar- y cada paso solo existe despues del anterior. Con banderas sueltas
 * ("hay archivo", "hay frase", "esta restaurando") se podrian combinar estados
 * imposibles, como restaurar sin haber enseñado el resumen.
 */
sealed interface PasoDeRestauracion {
    data object Inicio : PasoDeRestauracion

    data object LeyendoArchivo : PasoDeRestauracion

    /**
     * Lo que trae la copia comparado con lo que hay ahora, antes de tocar nada.
     *
     * @param fraseIncorrecta la frase ya se probo y no abria la copia: se enseña
     *   el aviso y se vuelve a pedir, sin volver a elegir el archivo.
     */
    data class Resumen(
        val copia: Manifiesto,
        val actual: Recuento,
        val fraseIncorrecta: Boolean = false,
    ) : PasoDeRestauracion

    data object Restaurando : PasoDeRestauracion

    /** @param aviso si los datos entraron pero los ajustes no. */
    data class Restaurada(
        val aviso: String? = null,
    ) : PasoDeRestauracion

    data object Deshaciendo : PasoDeRestauracion

    data object Deshecha : PasoDeRestauracion

    data class Fallida(
        val mensaje: String,
    ) : PasoDeRestauracion
}

data class RestaurarUiState(
    val paso: PasoDeRestauracion = PasoDeRestauracion.Inicio,
    /** Cuando se guardo la copia previa, si hay una restauracion que deshacer. */
    val copiaPreviaEnMillis: Long? = null,
)
