package com.miplata.core.domain

import com.miplata.core.domain.model.FrecuenciaDeRecordatorio

/** Lo que decide la politica: avisar o no, y con que dato. */
sealed interface DecisionDeRecordatorio {
    data object NoAvisar : DecisionDeRecordatorio

    /**
     * @param diasSinCopia cuantos dias hace de la ultima copia, o nulo si
     *   nunca se ha hecho ninguna. Es lo que dice la notificacion.
     */
    data class Avisar(
        val diasSinCopia: Int?,
    ) : DecisionDeRecordatorio
}

/**
 * Decide si toca recordar al usuario que haga una copia de seguridad.
 *
 * Es una funcion pura, sin reloj ni almacenes: recibe todo lo que necesita y
 * devuelve la decision. Por eso vive en el dominio y se prueba entera sin
 * WorkManager ni notificaciones, que son lo unico que no se puede probar bien
 * en la JVM.
 *
 * Las reglas, en orden:
 *
 * 1. Si el usuario eligio **no** recibir recordatorios, nunca se avisa.
 * 2. Si no hay datos que proteger -la app recien instalada, vacia-, tampoco:
 *    pedir una copia de nada es ruido, y enseña a ignorar el aviso.
 * 3. Se avisa si la ultima copia es mas antigua que la frecuencia elegida, o
 *    si nunca se ha hecho ninguna.
 * 4. Una vez avisado, no se repite hasta pasados [DIAS_ENTRE_AVISOS]. Un aviso
 *    diario acaba silenciado para siempre; uno que no se repite, olvidado.
 */
object PoliticaDeRecordatorio {
    /** Cuanto se espera antes de repetir un aviso que no se atendio. */
    const val DIAS_ENTRE_AVISOS: Int = 3

    private const val MILLIS_POR_DIA = 24L * 60 * 60 * 1000

    fun decidir(
        frecuencia: FrecuenciaDeRecordatorio,
        hayDatos: Boolean,
        ultimaCopiaEnMillis: Long?,
        ultimoAvisoEnMillis: Long?,
        ahoraEnMillis: Long,
    ): DecisionDeRecordatorio {
        val limite = frecuencia.dias
        val diasSinCopia = ultimaCopiaEnMillis?.let { diasEntre(it, ahoraEnMillis) }

        val vencida = limite != null && (diasSinCopia == null || diasSinCopia >= limite)
        // No hace falta mirar si el aviso fue antes o despues de la ultima copia.
        // Si fue antes y la copia ya vencio, el aviso es aun mas viejo que la
        // frecuencia, y toda frecuencia dura al menos DIAS_ENTRE_AVISOS: nunca
        // frena el siguiente. Un test vigila que eso siga siendo cierto.
        val avisadoHacePoco =
            ultimoAvisoEnMillis != null && diasEntre(ultimoAvisoEnMillis, ahoraEnMillis) < DIAS_ENTRE_AVISOS

        return if (hayDatos && vencida && !avisadoHacePoco) {
            DecisionDeRecordatorio.Avisar(diasSinCopia)
        } else {
            DecisionDeRecordatorio.NoAvisar
        }
    }

    /** Dias completos entre dos instantes. Un reloj que va hacia atras cuenta como cero. */
    private fun diasEntre(
        desde: Long,
        hasta: Long,
    ): Int = ((hasta - desde).coerceAtLeast(0) / MILLIS_POR_DIA).toInt()
}
