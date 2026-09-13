package com.miplata.core.data.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.miplata.core.data.database.entity.CategoriaEntity
import com.miplata.core.data.database.entity.CuentaEntity
import com.miplata.core.data.database.entity.LineaDePlanEntity
import com.miplata.core.data.database.entity.PlanEntity
import com.miplata.core.data.database.entity.TransaccionEntity
import kotlinx.coroutines.flow.Flow

// Acceso a datos.
//
// Room verifica cada consulta EN TIEMPO DE COMPILACION: una columna mal escrita
// o un tipo que no encaja rompen el build, no la app en el telefono del usuario.
//
// Ninguna consulta devuelve filas marcadas como eliminadas. El borrado logico se
// resuelve aqui, asi que el dominio solo ve lo vigente y no tiene que acordarse
// de filtrar en cada sitio.
//
// Nada de @Delete: `marcarEliminada` pone la marca. Un DELETE de verdad en una
// app de finanzas es perdida de datos irreversible.

@Dao
interface CuentaDao {
    @Query("SELECT * FROM cuentas WHERE eliminadaEn IS NULL ORDER BY nombre")
    fun observarTodas(): Flow<List<CuentaEntity>>

    @Query("SELECT * FROM cuentas WHERE id = :id AND eliminadaEn IS NULL")
    suspend fun obtener(id: String): CuentaEntity?

    @Upsert
    suspend fun guardar(cuenta: CuentaEntity)

    @Query("UPDATE cuentas SET eliminadaEn = :instante, actualizadaEn = :instante WHERE id = :id")
    suspend fun marcarEliminada(
        id: String,
        instante: Long,
    )
}

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias WHERE eliminadaEn IS NULL ORDER BY nombre")
    fun observarTodas(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM categorias WHERE id = :id AND eliminadaEn IS NULL")
    suspend fun obtener(id: String): CategoriaEntity?

    @Upsert
    suspend fun guardar(categoria: CategoriaEntity)

    @Upsert
    suspend fun guardarTodas(categorias: List<CategoriaEntity>)

    @Query("UPDATE categorias SET eliminadaEn = :instante, actualizadaEn = :instante WHERE id = :id")
    suspend fun marcarEliminada(
        id: String,
        instante: Long,
    )

    /**
     * Cuantas categorias vigentes hay.
     *
     * Filtra las eliminadas como el resto del DAO. Sirve para decidir si hay que
     * sembrar las categorias por defecto (2.5): contando tambien las eliminadas,
     * un usuario que las borrara todas se quedaria sin ninguna y sin sembrado.
     */
    @Query("SELECT COUNT(*) FROM categorias WHERE eliminadaEn IS NULL")
    suspend fun cuantasVigentesHay(): Int
}

@Dao
interface TransaccionDao {
    /**
     * Movimientos de un rango de fechas, extremos incluidos.
     *
     * Se filtra por rango y no por mes porque el mes economico del usuario puede
     * empezar cualquier dia (`PeriodoMensual`). Las fechas van en ISO, asi que
     * comparar como texto ordena bien.
     */
    @Query(
        """
        SELECT * FROM transacciones
        WHERE eliminadaEn IS NULL AND fecha BETWEEN :desde AND :hasta
        ORDER BY fecha, id
        """,
    )
    fun observarEntreFechas(
        desde: String,
        hasta: String,
    ): Flow<List<TransaccionEntity>>

    /** Incluye las transferencias en las que la cuenta es el destino. */
    @Query(
        """
        SELECT * FROM transacciones
        WHERE eliminadaEn IS NULL AND (cuentaOrigenId = :cuentaId OR cuentaDestinoId = :cuentaId)
        ORDER BY fecha, id
        """,
    )
    fun observarDeCuenta(cuentaId: String): Flow<List<TransaccionEntity>>

    @Query("SELECT * FROM transacciones WHERE id = :id AND eliminadaEn IS NULL")
    suspend fun obtener(id: String): TransaccionEntity?

    @Upsert
    suspend fun guardar(transaccion: TransaccionEntity)

    @Query("UPDATE transacciones SET eliminadaEn = :instante, actualizadaEn = :instante WHERE id = :id")
    suspend fun marcarEliminada(
        id: String,
        instante: Long,
    )
}

/**
 * Un plan con sus lineas.
 *
 * Room resuelve la relacion en una sola operacion; hacerlo a mano con dos
 * consultas abriria una ventana en la que el plan y sus lineas podrian venir de
 * estados distintos.
 */
data class PlanConLineas(
    @Embedded val plan: PlanEntity,
    @Relation(parentColumn = "mes", entityColumn = "planMes")
    val lineas: List<LineaDePlanEntity>,
) {
    /**
     * Las lineas en el orden que puso el usuario.
     *
     * `@Relation` no admite `ORDER BY` y **no garantiza ningun orden**: devuelve
     * las filas como le venga a SQLite. Por eso existe la columna `orden` y por
     * eso se aplica aqui, pegado al dato, y no en cada sitio que lea un plan.
     * Usar [lineas] directamente funcionaria hoy y se desordenaria el dia menos
     * pensado.
     *
     * No hace falta filtrar eliminadas: las lineas son las unicas filas sin
     * borrado logico, porque son el cuerpo del plan y se reemplazan en bloque
     * (ver `LineaDePlanEntity`).
     */
    val lineasOrdenadas: List<LineaDePlanEntity> get() = lineas.sortedBy { it.orden }
}

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM planes WHERE mes = :mes AND eliminadoEn IS NULL")
    fun observarPorMes(mes: String): Flow<PlanConLineas?>

    @Transaction
    @Query("SELECT * FROM planes WHERE mes = :mes AND eliminadoEn IS NULL")
    suspend fun obtenerPorMes(mes: String): PlanConLineas?

    /**
     * El plan mas reciente anterior a [mes].
     *
     * Lo necesita la materializacion cuando hay huecos: si el usuario no abre la
     * app en tres meses, hay que copiar del ultimo plan que exista y no del mes
     * inmediatamente anterior, que no tiene ninguno.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM planes
        WHERE eliminadoEn IS NULL AND mes < :mes
        ORDER BY mes DESC
        LIMIT 1
        """,
    )
    suspend fun obtenerUltimoAnteriorA(mes: String): PlanConLineas?

    @Upsert
    suspend fun guardarPlan(plan: PlanEntity)

    @Upsert
    suspend fun guardarLineas(lineas: List<LineaDePlanEntity>)

    @Query("DELETE FROM lineas_de_plan WHERE planMes = :mes")
    suspend fun borrarLineasDe(mes: String)

    /**
     * Guarda el plan y reemplaza sus lineas de una sola vez.
     *
     * Es `@Transaction` porque borrar las lineas viejas y escribir las nuevas
     * tienen que ser una sola operacion: si fallara en medio, el usuario se
     * quedaria con un plan sin lineas, que es peor que no haber guardado nada.
     */
    @Transaction
    suspend fun guardarPlanConLineas(
        plan: PlanEntity,
        lineas: List<LineaDePlanEntity>,
    ) {
        guardarPlan(plan)
        borrarLineasDe(plan.mes)
        guardarLineas(lineas)
    }

    @Query("UPDATE planes SET eliminadoEn = :instante, actualizadoEn = :instante WHERE mes = :mes")
    suspend fun marcarEliminado(
        mes: String,
        instante: Long,
    )
}
