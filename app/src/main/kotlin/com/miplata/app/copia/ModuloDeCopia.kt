package com.miplata.app.copia

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import com.miplata.core.backup.AlmacenDeLaCopiaPrevia
import com.miplata.core.backup.ArchivoDeBackup
import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.backup.RestauradorDeCopias
import com.miplata.core.data.database.MiPlataDatabase
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CierreRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.RepositorioDeRestauracion
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.feature.backup.AbridorDeDestino
import com.miplata.feature.backup.AbridorDeOrigen
import com.miplata.feature.backup.InformacionDeLaApp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Clock
import java.io.FileNotFoundException
import java.io.OutputStream

/**
 * El ensamblaje de la copia de seguridad.
 *
 * Aqui y no en el feature porque es donde se conocen a la vez Android, la base
 * de datos y el formato del backup. El feature pide interfaces; esto las
 * rellena (docs/04).
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDeCopia {
    @Provides
    fun proveerRecolector(
        cuentas: CuentaRepository,
        categorias: CategoriaRepository,
        transacciones: TransaccionRepository,
        planes: PlanRepository,
        ajustes: AjustesRepository,
        cierres: CierreRepository,
    ): RecolectorDeDatos = RecolectorDeDatos(cuentas, categorias, transacciones, planes, ajustes, cierres)

    @Provides
    fun proveerArchivo(): ArchivoDeBackup = ArchivoDeBackup()

    @Provides
    fun proveerReloj(): Clock = Clock.System

    @Provides
    fun proveerAbridor(
        @ApplicationContext context: Context,
    ): AbridorDeDestino = AbridorConContentResolver(context)

    @Provides
    fun proveerAbridorDeOrigen(
        @ApplicationContext context: Context,
    ): AbridorDeOrigen =
        AbridorDeOrigen { uri ->
            context.contentResolver.openInputStream(uri.toUri())
                ?: throw FileNotFoundException("El archivo elegido ya no esta disponible")
        }

    @Provides
    fun proveerCopiaPrevia(
        @ApplicationContext context: Context,
    ): AlmacenDeLaCopiaPrevia = CopiaPreviaEnArchivo(context)

    @Provides
    fun proveerRestaurador(
        recolector: RecolectorDeDatos,
        archivo: ArchivoDeBackup,
        repositorio: RepositorioDeRestauracion,
        ajustes: AjustesRepository,
        copiaPrevia: AlmacenDeLaCopiaPrevia,
    ): RestauradorDeCopias = RestauradorDeCopias(recolector, archivo, repositorio, ajustes, copiaPrevia)

    @Provides
    fun proveerInformacion(
        @ApplicationContext context: Context,
    ): InformacionDeLaApp = InformacionDelDispositivo(context)
}

/**
 * Abre el documento elegido en el selector del sistema.
 *
 * El modo es **`"wt"`** y no `"w"`, y la diferencia importa mas de lo que
 * parece. Si el usuario elige sobrescribir una copia antigua mas grande que la
 * nueva, con `"w"` algunos proveedores escriben encima **sin vaciar** el
 * archivo: los bytes del final de la copia vieja se quedan detras de la nueva,
 * el ZIP resultante esta roto, y nadie se entera hasta el dia en que hace falta
 * restaurar. `"wt"` (write + truncate) lo vacia primero.
 */
private class AbridorConContentResolver(
    private val context: Context,
) : AbridorDeDestino {
    override fun abrir(uri: String): OutputStream =
        context.contentResolver.openOutputStream(uri.toUri(), "wt")
            // Pasa si el proveedor ya no tiene el documento; se trata como
            // cualquier otro fallo de escritura y la pantalla lo cuenta.
            ?: throw FileNotFoundException("El destino elegido ya no esta disponible")
}

private class InformacionDelDispositivo(
    private val context: Context,
) : InformacionDeLaApp {
    override val dispositivo: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override val versionDeLaApp: String
        get() =
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()

    override val versionDelEsquema: Int = MiPlataDatabase.VERSION
}
