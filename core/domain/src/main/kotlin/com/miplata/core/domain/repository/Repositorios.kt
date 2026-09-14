package com.miplata.core.domain.repository

import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import kotlinx.coroutines.flow.Flow

// Contratos de acceso a datos.
//
// Viven en el dominio y los implementa la capa de datos, no al reves (docs/01).
// Asi el dominio decide que necesita y Room se limita a cumplirlo; si manana
// Room se cambia por otra cosa, estos contratos no se enteran.
//
// Las consultas devuelven Flow: la fuente de verdad es la base de datos y la UI
// reacciona sola a lo que cambie. Las escrituras son `suspend` porque son
// puntuales.
//
// No hay metodos de borrado logico ni marcas de tiempo: el repositorio filtra lo
// eliminado y el dominio solo ve lo vigente (docs/03).

interface CuentaRepository {
    /** Todas las cuentas vigentes, archivadas incluidas. */
    fun observarTodas(): Flow<List<Cuenta>>

    suspend fun obtener(id: CuentaId): Cuenta?

    suspend fun guardar(cuenta: Cuenta)

    suspend fun eliminar(id: CuentaId)
}

interface CategoriaRepository {
    fun observarTodas(): Flow<List<Categoria>>

    suspend fun obtener(id: CategoriaId): Categoria?

    suspend fun guardar(categoria: Categoria)

    suspend fun eliminar(id: CategoriaId)

    /**
     * Cuantas categorias vigentes hay.
     *
     * Existe para decidir si hay que sembrar las por defecto sin tener que
     * traerse todas las filas solo para contarlas.
     */
    suspend fun cuantasHay(): Int
}

interface TransaccionRepository {
    /**
     * Los movimientos de un periodo.
     *
     * Se filtra por periodo y no por mes natural porque el mes economico del
     * usuario puede empezar cualquier dia (ver `PeriodoMensual`).
     */
    fun observarDelPeriodo(periodo: PeriodoMensual): Flow<List<Transaccion>>

    fun observarDeCuenta(cuentaId: CuentaId): Flow<List<Transaccion>>

    /**
     * Todos los movimientos, desde el principio.
     *
     * Hace falta para el saldo de una cuenta, que es el saldo inicial mas TODO
     * lo que ha pasado desde entonces: cortar por un periodo daria un saldo
     * distinto segun el mes que estuvieras mirando, que no es un saldo.
     *
     * Se trae todo a memoria a proposito. Con los movimientos de una persona
     * son unos pocos miles de filas, y a cambio la suma vive en el dominio,
     * donde esta probada, en vez de en una consulta SQL (docs/01). Si algun dia
     * deja de ser barato, el sitio donde arreglarlo es este metodo.
     */
    fun observarTodas(): Flow<List<Transaccion>>

    suspend fun obtener(id: TransaccionId): Transaccion?

    suspend fun guardar(transaccion: Transaccion)

    suspend fun eliminar(id: TransaccionId)
}

interface AjustesRepository {
    fun observar(): Flow<Ajustes>

    suspend fun obtener(): Ajustes

    suspend fun guardar(ajustes: Ajustes)
}

interface PlanRepository {
    fun observarDe(mes: Mes): Flow<PlanMensual?>

    /**
     * Todos los planes guardados, del mes mas antiguo al mas reciente.
     *
     * Hace falta para el backup: un backup que solo se lleve los meses que
     * alguien se acuerde de pedir no es un backup. Deducir que meses existen a
     * partir de los movimientos seria adivinar, y perderia justo el plan del
     * mes que acabas de armar y aun no has ejecutado.
     */
    fun observarTodos(): Flow<List<PlanMensual>>

    suspend fun obtenerDe(mes: Mes): PlanMensual?

    /**
     * El plan mas reciente anterior a [mes], o `null` si no hay ninguno.
     *
     * Existe para materializar: si el usuario no abre la app en tres meses, hay
     * que copiar del ultimo plan que exista y no del mes inmediatamente
     * anterior, que no tiene ninguno.
     */
    suspend fun obtenerUltimoAnteriorA(mes: Mes): PlanMensual?

    suspend fun guardar(plan: PlanMensual)

    suspend fun eliminar(mes: Mes)
}
