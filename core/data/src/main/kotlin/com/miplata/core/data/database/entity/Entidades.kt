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
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LineaDePlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["lineaDePlanId"],
            // SET NULL y no RESTRICT: las lineas de un plan se reemplazan al
            // editarlo, y un movimiento que ya ocurrio no puede desaparecer
            // porque el usuario haya quitado esa linea de su plan. Se queda sin
            // linea asociada, que es exactamente lo que significa un gasto que
            // no pertenece a ninguna: sigue contando en los totales reales.
            onDelete = ForeignKey.SET_NULL,
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

/**
 * El plan de un mes.
 *
 * **La clave primaria es el mes, no el `id`**, y es deliberado: un mes tiene
 * exactamente un plan, porque el plan ES del mes (ADR 0003). Con el `id` como
 * clave y un indice unico sobre `mes`, borrar el plan de marzo dejaba el mes
 * bloqueado para siempre: la fila borrada seguia ocupando el indice unico y
 * crear un plan nuevo para marzo fallaba con UNIQUE, aunque todas las consultas
 * la ignorasen.
 *
 * Con el mes como clave, volver a crear el plan de un mes borrado es un upsert
 * sobre la misma fila: lo restaura. El `id` sigue existiendo porque es la
 * identidad que usa el dominio y la que viajara en el backup, pero no es la
 * identidad de la fila.
 */
@Entity(
    tableName = "planes",
    // El id sigue siendo unico: las lineas y los movimientos lo referencian.
    indices = [Index(value = ["id"], unique = true)],
)
data class PlanEntity(
    /** Mes ISO `AAAA-MM`. Ordena bien como texto. */
    @PrimaryKey val mes: String,
    val id: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val eliminadoEn: Long? = null,
)

/**
 * Una linea del plan de un mes.
 *
 * **Es la unica tabla sin borrado logico**, y la excepcion esta razonada: las
 * lineas son el cuerpo del plan, no datos independientes. Al guardar un plan se
 * reemplazan en bloque, asi que marcarlas como eliminadas dejaria una fila
 * muerta por cada edicion -y editar el plan es lo que el usuario hace todo el
 * rato-. La historia no se pierde por esto: cada mes tiene su propio snapshot,
 * y los meses pasados conservan sus lineas intactas.
 */
@Entity(
    tableName = "lineas_de_plan",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["mes"],
            childColumns = ["planMes"],
            // Aqui si hay pertenencia de verdad: una linea sin su plan no
            // significa nada.
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoriaEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CuentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["cuentaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("planMes"), Index("categoriaId"), Index("cuentaId")],
)
data class LineaDePlanEntity(
    @PrimaryKey val id: String,
    val planMes: String,
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
)
