package com.miplata.core.data.database

import android.content.Context
import androidx.room.Room
import com.miplata.core.data.database.seguridad.ProveedorDeClaveDeBaseDeDatos
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Construye la base de datos cifrada.
 *
 * El cifrado se aplica aqui y no en la propia [MiPlataDatabase] para que los
 * tests puedan abrir la misma base sin cifrar: SQLCipher necesita librerias
 * nativas que la JVM no carga, asi que atar el cifrado a la definicion de la
 * base dejaria los 22 tests de DAO sin poder ejecutarse fuera de un emulador.
 * Lo que se prueba en la JVM es el SQL, que es lo que puede romperse al editarlo;
 * que el archivo quede cifrado se prueba con un test instrumentado.
 */
object FabricaDeBaseDeDatos {
    fun crear(context: Context): MiPlataDatabase {
        System.loadLibrary("sqlcipher")

        val frase = ProveedorDeClaveDeBaseDeDatos(context, MiPlataDatabase.NOMBRE).obtenerFrase()

        return Room
            .databaseBuilder(context, MiPlataDatabase::class.java, MiPlataDatabase.NOMBRE)
            .openHelperFactory(SupportOpenHelperFactory(frase))
            // Sin fallbackToDestructiveMigration, ni ahora ni nunca: esa opcion
            // borra la base del usuario cuando falta una migracion.
            .build()
    }
}
