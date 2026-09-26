package com.miplata.feature.backup.recordatorio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Clock
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Como obtiene el trabajo sus dependencias del grafo de Hilt.
 *
 * Con un punto de entrada y no con `@HiltWorker` a proposito: `@HiltWorker`
 * obliga a quitar el inicializador automatico de WorkManager del manifiesto y a
 * que la `Application` configure una fabrica propia. Para un unico trabajo es
 * mucha maquinaria y un sitio mas donde algo puede quedar mal enganchado.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DependenciasDelRecordatorio {
    fun comprobador(): ComprobadorDeRecordatorio

    fun reloj(): Clock
}

/**
 * El trabajo periodico: una vez al dia, comprueba si toca recordar la copia.
 *
 * Diario y no semanal o mensual: la frecuencia la elige el usuario y la ultima
 * copia cambia en cualquier momento, asi que en vez de reprogramar el trabajo
 * cada vez, se mira a diario y decide la politica. Asi cambiar la frecuencia en
 * los ajustes funciona sin tocar WorkManager.
 */
class TrabajoDeRecordatorio(
    contexto: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val dependencias =
            EntryPointAccessors.fromApplication(
                applicationContext,
                DependenciasDelRecordatorio::class.java,
            )
        return try {
            dependencias.comprobador().comprobar(dependencias.reloj().now().toEpochMilliseconds())
            Result.success()
        } catch (e: IOException) {
            // Leer los ajustes o la base puede fallar puntualmente; mañana se
            // vuelve a intentar igual, no hace falta reintentar ahora.
            android.util.Log.w("MiPlata.Copia", "No se pudo comprobar el recordatorio", e)
            Result.success()
        }
    }

    companion object {
        private const val NOMBRE = "recordatorio-copia-de-seguridad"
        private const val HORAS_ENTRE_COMPROBACIONES = 24L

        /**
         * Programa el trabajo, si no lo estaba ya.
         *
         * Con `KEEP`: se llama en cada arranque de la app, y reprogramarlo cada
         * vez reiniciaria la cuenta de las 24 horas, de modo que alguien que abre
         * la app a diario no llegaria a recibir nunca el aviso.
         */
        fun programar(context: Context) {
            val peticion =
                PeriodicWorkRequestBuilder<TrabajoDeRecordatorio>(HORAS_ENTRE_COMPROBACIONES, TimeUnit.HOURS).build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.KEEP, peticion)
        }
    }
}
