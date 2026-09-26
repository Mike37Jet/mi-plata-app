package com.miplata.feature.backup

/** En que punto esta la exportacion. */
sealed interface Exportacion {
    data object Inactiva : Exportacion

    /** Derivar la clave tarda un par de segundos en un movil: hay que enseñarlo. */
    data object EnCurso : Exportacion

    data class Terminada(
        val nombre: String,
    ) : Exportacion

    data class Fallida(
        val mensaje: String,
    ) : Exportacion
}

/**
 * Todo lo que la pantalla de copia de seguridad necesita para dibujarse.
 *
 * **La frase no esta aqui**, y es deliberado. Lo que vive en el estado del
 * ViewModel puede acabar en un `SavedStateHandle`, y eso se escribe en disco
 * cuando el sistema mata la app en segundo plano. La frase vive solo en la
 * pantalla, en un `remember` sin guardar, y se le pasa al ViewModel en el
 * momento de exportar.
 */
data class CopiaUiState(
    /** Cuando se hizo la ultima copia, o nulo si nunca. */
    val ultimaCopiaEnMillis: Long? = null,
    val exportacion: Exportacion = Exportacion.Inactiva,
)
