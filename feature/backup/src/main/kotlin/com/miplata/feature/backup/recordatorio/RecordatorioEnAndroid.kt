package com.miplata.feature.backup.recordatorio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.miplata.feature.backup.R

/**
 * El extra con el que la notificacion abre la app directamente en la pantalla de
 * copia de seguridad. `:app` lo lee al arrancar; este feature no conoce la
 * Activity ni la navegacion (docs/04).
 */
const val EXTRA_ABRIR_COPIA_DE_SEGURIDAD = "com.miplata.ABRIR_COPIA_DE_SEGURIDAD"

/** Apunta el ultimo aviso en unas preferencias privadas de la app. */
class RegistroEnPreferencias(
    context: Context,
) : RegistroDeAvisos {
    private val preferencias = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    override fun ultimoAvisoEnMillis(): Long? = preferencias.getLong(CLAVE, NINGUNO).takeIf { it != NINGUNO }

    override fun anotarAviso(enMillis: Long) = preferencias.edit { putLong(CLAVE, enMillis) }

    private companion object {
        const val ARCHIVO = "mi-plata-recordatorio"
        const val CLAVE = "ultimo-aviso"
        const val NINGUNO = -1L
    }
}

/**
 * El aviso como notificacion.
 *
 * Tocarla abre la app en la pantalla de copia, que es lo unico que se puede
 * hacer con el aviso: una notificacion que obliga a buscar donde se hace la copia
 * se descarta y ya.
 */
class NotificadorEnAndroid(
    private val context: Context,
) : NotificadorDeRecordatorio {
    override fun avisar(diasSinCopia: Int?): Boolean {
        if (!sePuedenMostrar(context)) return false
        crearCanal()

        val texto =
            if (diasSinCopia == null) {
                context.getString(R.string.recordatorio_nunca)
            } else {
                context.resources.getQuantityString(R.plurals.recordatorio_dias, diasSinCopia, diasSinCopia)
            }

        val notificacion =
            NotificationCompat
                .Builder(context, CANAL)
                .setSmallIcon(R.drawable.ic_notificacion_copia)
                .setContentTitle(context.getString(R.string.recordatorio_titulo))
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setContentIntent(abrirLaCopia())
                .setAutoCancel(true)
                // Un recordatorio, no una alerta: sin sonido que interrumpa.
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        // El permiso ya se comprobo arriba; lint no sabe seguirlo a traves de la
        // funcion y pide comprobarlo aqui otra vez.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(ID_NOTIFICACION, notificacion)
        }
        return true
    }

    private fun crearCanal() {
        val canal =
            NotificationChannel(
                CANAL,
                context.getString(R.string.recordatorio_canal),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.recordatorio_canal_descripcion) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun abrirLaCopia(): PendingIntent {
        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                putExtra(EXTRA_ABRIR_COPIA_DE_SEGURIDAD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            } ?: Intent()
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val CANAL = "recordatorio-copia"
        private const val ID_NOTIFICACION = 1

        /**
         * Si la app puede enseñar notificaciones ahora mismo.
         *
         * Desde Android 13 hace falta un permiso que se pide en tiempo de
         * ejecucion, y en cualquier version el usuario puede apagarlas.
         */
        fun sePuedenMostrar(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
