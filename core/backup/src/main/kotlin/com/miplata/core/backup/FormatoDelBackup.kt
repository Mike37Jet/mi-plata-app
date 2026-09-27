package com.miplata.core.backup

import kotlinx.serialization.Serializable

/**
 * Version del **formato del archivo**, no del esquema de la base.
 *
 * Son dos cosas distintas y se versionan por separado a proposito (docs/05): el
 * esquema de Room cambia cada vez que se toca una tabla, mientras que el formato
 * del backup es un contrato con el usuario -que puede restaurar en un movil
 * nuevo un archivo de hace un ano- y evoluciona mucho mas despacio.
 *
 * Subirlo obliga a escribir la migracion de formato correspondiente y a dejar un
 * archivo de ejemplo en los tests.
 *
 * La version 1 **ya es la cifrada**. Hubo un paso intermedio sin cifrar mientras
 * se construia (4.1), pero nunca salio del repositorio: no hay un solo archivo
 * de ese formato en manos de nadie, asi que no merece un numero de version ni
 * una migracion que mantener para siempre.
 */
const val VERSION_DEL_FORMATO: Int = 1

/** Nombre de cada pieza dentro del archivo. */
internal object Piezas {
    const val MANIFIESTO = "manifest.json"

    /**
     * `data.json` cifrado. La extension no es decorativa: quien abra el ZIP a
     * mano ve de un vistazo que ahi no hay nada que leer.
     */
    const val DATOS = "data.enc"
}

/**
 * La ficha del backup: que es, de cuando, y como comprobar que esta entero.
 *
 * Va **sin cifrar**, aunque los datos si lo esten, porque hay que poder leerla
 * antes de pedirle la frase al usuario: si el archivo es de una version que esta
 * app no entiende, o esta dañado, lo suyo es decirlo en vez de hacerle teclear
 * una frase para nada.
 *
 * Lo que expone es deliberadamente poco: versiones, fecha, nombre del movil y
 * cuantos registros hay. Ni un importe, ni un nombre de cuenta.
 *
 * @param checksum SHA-256 del contenido **cifrado**, en hexadecimal. Sobre el
 *   cifrado y no sobre el claro por dos motivos: permite detectar un archivo
 *   dañado sin conocer la frase -y distinguirlo asi de una frase mal escrita-,
 *   y un hash del claro al lado del cifrado permitiria confirmar un contenido
 *   adivinado sin descifrar nada.
 * @param cifrado lo necesario para descifrar, salvo la frase.
 */
@Serializable
data class Manifiesto(
    val versionDelFormato: Int = VERSION_DEL_FORMATO,
    val versionDelEsquema: Int,
    val versionDeLaApp: String,
    /** Cuando se creo, en milisegundos desde el epoch. */
    val creadoEnMillis: Long,
    /** Para que el usuario reconozca de que movil salio. */
    val dispositivo: String,
    val checksum: String,
    val cifrado: ParametrosDeCifrado,
    /** Cuantos de cada cosa, para el resumen previo a restaurar (docs/05). */
    val contenido: Recuento,
)

/** Lo que trae el backup, sin abrirlo. */
@Serializable
data class Recuento(
    val cuentas: Int = 0,
    val categorias: Int = 0,
    val transacciones: Int = 0,
    val planes: Int = 0,
    val cierres: Int = 0,
)

/**
 * Todo el contenido del usuario, listo para serializar.
 *
 * Son DTOs y no los modelos del dominio, y la diferencia importa: el dominio
 * puede cambiar de forma cuando convenga -renombrar un campo, partir una clase-
 * sin romper los archivos que el usuario ya guardo. Si se serializaran los
 * modelos directamente, cada refactor del dominio seria un cambio de formato
 * silencioso, y el backup de hace seis meses dejaria de leerse.
 */
@Serializable
data class DatosDelBackup(
    val ajustes: AjustesDto,
    val cuentas: List<CuentaDto> = emptyList(),
    val categorias: List<CategoriaDto> = emptyList(),
    val transacciones: List<TransaccionDto> = emptyList(),
    val planes: List<PlanDto> = emptyList(),
    /** Desde el presupuesto por cuentas (ADR 0007). Las copias de antes no lo traen. */
    val cierres: List<CierreDto> = emptyList(),
)

@Serializable
data class AjustesDto(
    val moneda: String,
    val primerDiaDelMesFinanciero: Int,
    val tema: String,
    val ultimoBackupEnMillis: Long? = null,
    /**
     * Campo añadido despues de la version 1 del formato, con valor por defecto:
     * las copias hechas antes no lo traen y se siguen leyendo igual. Un campo
     * nuevo con valor por defecto no obliga a subir la version del formato.
     */
    val frecuenciaDeRecordatorio: String = "MENSUAL",
)

@Serializable
data class CuentaDto(
    val id: String,
    val nombre: String,
    val tipo: String,
    /** En centavos, igual que `Money`: un decimal en JSON perderia precision. */
    val saldoInicialEnCentavos: Long,
    val moneda: String,
    val incluirEnTotal: Boolean = true,
    val archivada: Boolean = false,
    /**
     * El rol en el presupuesto por cuentas (ADR 0007): `INDEPENDIENTE`,
     * `PRINCIPAL` o `SOBRE`. Con valores por defecto, como `frecuenciaDeRecordatorio`:
     * una copia de antes trae cuentas independientes, que es lo que eran.
     */
    val rol: String = "INDEPENDIENTE",
    /** Solo en un sobre, y solo uno de los dos. */
    val repartoPorcentaje: Int? = null,
    val repartoMontoEnCentavos: Long? = null,
    val intocable: Boolean = false,
)

@Serializable
data class CategoriaDto(
    val id: String,
    val nombre: String,
    val padreId: String? = null,
    val icono: String = "",
    val color: Int = 0,
)

@Serializable
data class TransaccionDto(
    val id: String,
    /** Fecha en ISO-8601 (`2026-03-15`), que es estable y legible a ojo. */
    val fecha: String,
    val montoEnCentavos: Long,
    val tipo: String,
    val cuentaOrigenId: String,
    val cuentaDestinoId: String? = null,
    val categoriaId: String? = null,
    val lineaDePlanId: String? = null,
    val nota: String? = null,
    /** El mes ISO del cierre que lo creo, o nulo si lo anoto el usuario. */
    val ajusteDeCierre: String? = null,
)

@Serializable
data class PlanDto(
    val id: String,
    /** El mes en ISO (`2026-03`). */
    val mes: String,
    val lineas: List<LineaDePlanDto> = emptyList(),
)

@Serializable
data class LineaDePlanDto(
    val id: String,
    val nombre: String,
    val tipo: String,
    val montoPlanificadoEnCentavos: Long,
    val categoriaId: String? = null,
    val cuentaId: String? = null,
    val diaDelMes: Int? = null,
    val activa: Boolean = true,
)

@Serializable
data class CierreDto(
    /** El mes en ISO (`2026-03`). */
    val mes: String,
    val saldos: List<SaldoDeCierreDto> = emptyList(),
)

@Serializable
data class SaldoDeCierreDto(
    val cuentaId: String,
    val esperadoEnCentavos: Long,
    val realEnCentavos: Long,
)
