package com.miplata.feature.backup.recordatorio

import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.domain.DecisionDeRecordatorio
import com.miplata.core.domain.PoliticaDeRecordatorio
import com.miplata.core.domain.repository.AjustesRepository

/** Donde se apunta cuando se aviso por ultima vez, para no avisar a diario. */
interface RegistroDeAvisos {
    fun ultimoAvisoEnMillis(): Long?

    fun anotarAviso(enMillis: Long)
}

/** Lo que enseña el aviso. La implementacion de Android es una notificacion. */
fun interface NotificadorDeRecordatorio {
    /**
     * @param diasSinCopia cuantos dias hace de la ultima copia, o nulo si nunca
     *   se ha hecho ninguna.
     * @return si el aviso llego a enseñarse. Con las notificaciones desactivadas
     *   no llega, y entonces no se anota: el aviso sigue pendiente.
     */
    fun avisar(diasSinCopia: Int?): Boolean
}

/**
 * La comprobacion que hace el trabajo periodico: junta los datos, aplica la
 * politica y, si toca, avisa.
 *
 * Esta separada del `Worker` para poder probarla en la JVM. El `Worker` es solo
 * el enchufe con WorkManager, y WorkManager no se deja probar bien fuera de un
 * dispositivo.
 */
class ComprobadorDeRecordatorio(
    private val ajustes: AjustesRepository,
    private val recolector: RecolectorDeDatos,
    private val registro: RegistroDeAvisos,
    private val notificador: NotificadorDeRecordatorio,
) {
    suspend fun comprobar(ahoraEnMillis: Long) {
        val configuracion = ajustes.obtener()
        val datos = recolector.recolectar()

        val decision =
            PoliticaDeRecordatorio.decidir(
                frecuencia = configuracion.frecuenciaDeRecordatorio,
                // Las categorias no cuentan: vienen sembradas de serie, y una app
                // que solo tiene esas no tiene nada del usuario que proteger.
                hayDatos = datos.cuentas.isNotEmpty() || datos.transacciones.isNotEmpty() || datos.planes.isNotEmpty(),
                ultimaCopiaEnMillis = configuracion.ultimoBackupEnMillis,
                ultimoAvisoEnMillis = registro.ultimoAvisoEnMillis(),
                ahoraEnMillis = ahoraEnMillis,
            )

        if (decision is DecisionDeRecordatorio.Avisar && notificador.avisar(decision.diasSinCopia)) {
            registro.anotarAviso(ahoraEnMillis)
        }
    }
}
