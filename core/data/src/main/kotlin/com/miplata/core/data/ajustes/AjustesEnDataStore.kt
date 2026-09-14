package com.miplata.core.data.ajustes

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.repository.AjustesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Los ajustes tal y como se guardan en disco.
 *
 * Es un DTO con tipos primitivos, separado del modelo de dominio por la misma
 * razon que las entities de Room: el formato en disco puede cambiar sin arrastrar
 * a las reglas de negocio, y al reves.
 *
 * El tema se guarda por **nombre** y no por posicion, igual que el resto de
 * enums del proyecto: reordenarlo no puede cambiar lo que significa lo ya
 * guardado.
 */
@kotlinx.serialization.Serializable
data class AjustesGuardados(
    val moneda: String = "USD",
    val primerDiaDelMesFinanciero: Int = 1,
    val tema: String = Tema.SEGUN_EL_SISTEMA.name,
    val ultimoBackupEnMillis: Long? = null,
)

object SerializadorDeAjustes : Serializer<AjustesGuardados> {
    override val defaultValue = AjustesGuardados()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun readFrom(input: InputStream): AjustesGuardados {
        val contenido = input.readBytes().decodeToString()

        // Un archivo vacio no es JSON invalido: es un archivo que aun no se ha
        // escrito. Pasa si una escritura se interrumpe a medias, y tratarlo como
        // corrupcion haria que la app arrancara con un error en vez de con sus
        // valores por defecto.
        if (contenido.isBlank()) return defaultValue

        return try {
            json.decodeFromString(AjustesGuardados.serializer(), contenido)
        } catch (e: SerializationException) {
            // DataStore trata esto como corrupcion y aplica su politica. Aqui no
            // hay drama: son cuatro preferencias, se vuelve a los valores por
            // defecto. Lo que nunca se toca por esto es la base de datos.
            throw CorruptionException("Los ajustes guardados no se pueden leer", e)
        }
    }

    override suspend fun writeTo(
        t: AjustesGuardados,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(AjustesGuardados.serializer(), t).encodeToByteArray())
    }
}

/**
 * Ajustes guardados con DataStore.
 *
 * **Se usa kotlinx.serialization y no Proto DataStore**, desviandose de lo que
 * decia docs/02. El motivo que alli se daba para elegir Proto era el tipado
 * fuerte, y eso ya lo da un `data class` serializable: no hace falta anadir el
 * plugin de protobuf, ni protoc, ni un esquema `.proto`, ni codigo generado,
 * para cuatro preferencias. Ademas el proyecto ya usa kotlinx.serialization para
 * el backup (docs/05), asi que son uno y no dos mecanismos de serializacion.
 */
class AjustesEnDataStore(
    private val dataStore: DataStore<AjustesGuardados>,
) : AjustesRepository {
    override fun observar(): Flow<Ajustes> = dataStore.data.map { it.aDominio() }

    override suspend fun obtener(): Ajustes = observar().first()

    override suspend fun guardar(ajustes: Ajustes) {
        dataStore.updateData { ajustes.aGuardados() }
    }
}

fun AjustesGuardados.aDominio(): Ajustes =
    Ajustes(
        moneda = Moneda(moneda),
        primerDiaDelMesFinanciero = primerDiaDelMesFinanciero,
        tema = Tema.entries.firstOrNull { it.name == tema } ?: Tema.SEGUN_EL_SISTEMA,
        ultimoBackupEnMillis = ultimoBackupEnMillis,
    )

fun Ajustes.aGuardados(): AjustesGuardados =
    AjustesGuardados(
        moneda = moneda.codigo,
        primerDiaDelMesFinanciero = primerDiaDelMesFinanciero,
        tema = tema.name,
        ultimoBackupEnMillis = ultimoBackupEnMillis,
    )
