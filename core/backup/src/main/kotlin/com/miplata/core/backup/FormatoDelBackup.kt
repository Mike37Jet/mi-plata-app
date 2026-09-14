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
 */
const val VERSION_DEL_FORMATO: Int = 1

/** Nombre de cada pieza dentro del archivo. */
internal object Piezas {
    const val MANIFIESTO = "manifest.json"
    const val DATOS = "data.json"
}

/**
 * La ficha del backup: que es, de cuando, y como comprobar que esta entero.
 *
 * Va sin cifrar dentro del archivo aunque los datos si lo esten (4.2), porque
 * hay que poder leer la version del formato **antes** de pedirle la frase al
 * usuario: si el archivo es de una version que esta app no entiende, lo suyo es
 * decirlo en vez de hacerle teclear una frase para nada.
 *
 * @param checksum SHA-256 de `data.json` en hexadecimal. Detecta corrupcion
 *   antes de tocar la base de datos; es lo que permite abortar a tiempo.
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
)

@Serializable
data class AjustesDto(
    val moneda: String,
    val primerDiaDelMesFinanciero: Int,
    val tema: String,
    val ultimoBackupEnMillis: Long? = null,
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
