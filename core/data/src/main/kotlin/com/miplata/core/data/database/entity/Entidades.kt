package com.miplata.core.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Entidades de Room.
//
// Estan separadas de los modelos de dominio a proposito (docs/02), y el precio
// es escribir mappers. A cambio, un cambio de esquema no se filtra a las reglas
// de negocio y el dominio no sabe que Room existe.
//
// Solo usan tipos primitivos: los identificadores son String y el dinero es un
// Long de centavos. Asi no hace falta un solo TypeConverter, y la traduccion
// -que es donde estan los errores interesantes- ocurre en mappers explicitos que
// se pueden leer y testear.
//
// Aqui viven los campos de auditoria que docs/03 saco del dominio: son metadatos
// de como se guarda algo, no reglas de negocio. `eliminadaEn` implementa el
// borrado logico: nada se borra de verdad, se marca. Un DELETE real en una app
// de finanzas es perdida de datos irreversible.

@Entity(tableName = "cuentas")
data class CuentaEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    /** Nombre de la constante de `TipoDeCuenta`. */
    val tipo: String,
    val saldoInicialCentavos: Long,
    /** Codigo ISO 4217. */
    val moneda: String,
    val incluirEnTotal: Boolean,
    val archivada: Boolean,
    val creadaEn: Long,
    val actualizadaEn: Long,
    /** Instante del borrado logico. `null` significa vigente. */
    val eliminadaEn: Long? = null,
)

@Entity(
    tableName = "categorias",
    foreignKeys = [
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["padreId"],
            // La jerarquia es de dos niveles: borrar una madre no puede dejar
            // subcategorias apuntando al vacio. Como el borrado es logico, en la
            // practica esto nunca dispara; esta para que no pueda existir una
            // referencia rota ni por accidente.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("padreId")],
)
data class CategoriaEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val padreId: String? = null,
    val icono: String = "",
    val color: Int = 0,
    val creadaEn: Long,
    val actualizadaEn: Long,
    val eliminadaEn: Long? = null,
)

@Entity(
    tableName = "transacciones",
    foreignKeys = [
        ForeignKey(
            entity = CuentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["cuentaOrigenId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CuentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["cuentaDestinoId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        // La consulta mas frecuente con diferencia es "los movimientos de este
        // periodo", que filtra por rango de fechas.
        Index("fecha"),
        Index("cuentaOrigenId"),
        Index("cuentaDestinoId"),
        Index("categoriaId"),
        Index("lineaDePlanId"),
    ],
)
data class TransaccionEntity(
    @PrimaryKey val id: String,
    /** Fecha ISO `AAAA-MM-DD`. Ordena bien como texto y se lee en la base. */
    val fecha: String,
    /** Siempre positivo; el signo lo determina [tipo]. */
    val montoCentavos: Long,
    /** Nombre de la constante de `TipoDeTransaccion`. */
    val tipo: String,
    val cuentaOrigenId: String,
    val cuentaDestinoId: String? = null,
    val categoriaId: String? = null,
    /** El puente con el plan. Nulo en los gastos imprevistos. */
    val lineaDePlanId: String? = null,
    val nota: String? = null,
    val creadaEn: Long,
    val actualizadaEn: Long,
    val eliminadaEn: Long? = null,
)

@Entity(
    tableName = "planes",
    // Un mes no puede tener dos planes: el plan ES del mes (ADR 0003).
    indices = [Index(value = ["mes"], unique = true)],
)
data class PlanEntity(
    @PrimaryKey val id: String,
    /** Mes ISO `AAAA-MM`. Ordena bien como texto. */
    val mes: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val eliminadoEn: Long? = null,
)

@Entity(
    tableName = "lineas_de_plan",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            // Aqui si hay pertenencia de verdad: una linea sin su plan no
            // significa nada.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId"), Index("categoriaId"), Index("cuentaId")],
)
data class LineaDePlanEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val nombre: String,
    /** Nombre de la constante de `TipoDeLinea`. */
    val tipo: String,
    /** Magnitud sin signo; el signo lo determina [tipo]. */
    val montoPlanificadoCentavos: Long,
    val categoriaId: String? = null,
    val cuentaId: String? = null,
    val diaDelMes: Int? = null,
    val activa: Boolean,
    /**
     * Posicion dentro del plan.
     *
     * Se guarda porque el orden es del usuario: al materializar el mes siguiente
     * las lineas se copian en el mismo orden, y sin esta columna dependerian del
     * capricho con que SQLite devuelva las filas.
     */
    val orden: Int,
    val creadaEn: Long,
    val actualizadaEn: Long,
    val eliminadaEn: Long? = null,
)
