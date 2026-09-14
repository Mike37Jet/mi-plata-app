package com.miplata.core.data.repository

import com.miplata.core.data.database.dao.CategoriaDao
import com.miplata.core.data.database.dao.CuentaDao
import com.miplata.core.data.database.dao.PlanDao
import com.miplata.core.data.database.dao.TransaccionDao
import com.miplata.core.data.mapper.aDominio
import com.miplata.core.data.mapper.aEntidad
import com.miplata.core.data.mapper.lineasAEntidades
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Implementaciones sobre Room de los contratos que define el dominio.
//
// No llevan withContext(IO): Room ya conmuta a su propio ejecutor para las
// consultas y las suspend (docs/02). Envolverlas seria un salto de hilo de mas
// que no aporta nada.
//
// Las marcas de tiempo salen de un Reloj inyectado, no de System.currentTimeMillis:
// asi los tests las fijan y pueden afirmar exactamente que se guardo.

class RoomCuentaRepository(
    private val dao: CuentaDao,
    private val reloj: Reloj = Reloj.DEL_SISTEMA,
) : CuentaRepository {
    override fun observarTodas(): Flow<List<Cuenta>> = dao.observarTodas().map { filas -> filas.map { it.aDominio() } }

    override suspend fun obtener(id: CuentaId): Cuenta? = dao.obtener(id.valor)?.aDominio()

    override suspend fun guardar(cuenta: Cuenta) {
        val ahora = reloj.ahoraEnMillis()
        // `creadaEn` conserva su valor original si la fila ya existia: es cuando
        // el usuario creo la cuenta, no cuando la edito por ultima vez.
        val creadaEn = dao.obtener(cuenta.id.valor)?.creadaEn ?: ahora
        dao.guardar(cuenta.aEntidad(creadaEn = creadaEn, actualizadaEn = ahora))
    }

    override suspend fun eliminar(id: CuentaId) {
        dao.marcarEliminada(id.valor, reloj.ahoraEnMillis())
    }
}

class RoomCategoriaRepository(
    private val dao: CategoriaDao,
    private val reloj: Reloj = Reloj.DEL_SISTEMA,
) : CategoriaRepository {
    override fun observarTodas(): Flow<List<Categoria>> =
        dao.observarTodas().map { filas -> filas.map { it.aDominio() } }

    override suspend fun obtener(id: CategoriaId): Categoria? = dao.obtener(id.valor)?.aDominio()

    override suspend fun guardar(categoria: Categoria) {
        val ahora = reloj.ahoraEnMillis()
        val creadaEn = dao.obtener(categoria.id.valor)?.creadaEn ?: ahora
        dao.guardar(categoria.aEntidad(creadaEn = creadaEn, actualizadaEn = ahora))
    }

    override suspend fun eliminar(id: CategoriaId) {
        dao.marcarEliminada(id.valor, reloj.ahoraEnMillis())
    }

    override suspend fun cuantasHay(): Int = dao.cuantasVigentesHay()
}

class RoomTransaccionRepository(
    private val dao: TransaccionDao,
    private val reloj: Reloj = Reloj.DEL_SISTEMA,
) : TransaccionRepository {
    override fun observarDelPeriodo(periodo: PeriodoMensual): Flow<List<Transaccion>> =
        dao
            // El periodo sabe sus extremos, incluido el caso del mes que empieza
            // el 25 y termina en el mes siguiente.
            .observarEntreFechas(periodo.inicio.toString(), periodo.fin.toString())
            .map { filas -> filas.map { it.aDominio() } }

    override fun observarDeCuenta(cuentaId: CuentaId): Flow<List<Transaccion>> =
        dao.observarDeCuenta(cuentaId.valor).map { filas -> filas.map { it.aDominio() } }

    override fun observarTodas(): Flow<List<Transaccion>> =
        dao.observarTodas().map { filas -> filas.map { it.aDominio() } }

    override suspend fun obtener(id: TransaccionId): Transaccion? = dao.obtener(id.valor)?.aDominio()

    override suspend fun guardar(transaccion: Transaccion) {
        val ahora = reloj.ahoraEnMillis()
        val creadaEn = dao.obtener(transaccion.id.valor)?.creadaEn ?: ahora
        dao.guardar(transaccion.aEntidad(creadaEn = creadaEn, actualizadaEn = ahora))
    }

    override suspend fun eliminar(id: TransaccionId) {
        dao.marcarEliminada(id.valor, reloj.ahoraEnMillis())
    }
}

class RoomPlanRepository(
    private val dao: PlanDao,
    private val reloj: Reloj = Reloj.DEL_SISTEMA,
) : PlanRepository {
    override fun observarDe(mes: Mes): Flow<PlanMensual?> = dao.observarPorMes(mes.toString()).map { it?.aDominio() }

    override suspend fun obtenerDe(mes: Mes): PlanMensual? = dao.obtenerPorMes(mes.toString())?.aDominio()

    override fun observarTodos(): Flow<List<PlanMensual>> =
        dao.observarTodos().map { filas -> filas.map { it.aDominio() } }

    override suspend fun obtenerUltimoAnteriorA(mes: Mes): PlanMensual? =
        dao.obtenerUltimoAnteriorA(mes.toString())?.aDominio()

    override suspend fun guardar(plan: PlanMensual) {
        val ahora = reloj.ahoraEnMillis()
        val creadoEn = dao.obtenerPorMes(plan.mes.toString())?.plan?.creadoEn ?: ahora
        dao.guardarPlanConLineas(
            plan = plan.aEntidad(creadoEn = creadoEn, actualizadoEn = ahora),
            lineas = plan.lineasAEntidades(creadaEn = ahora, actualizadaEn = ahora),
        )
    }

    override suspend fun eliminar(mes: Mes) {
        dao.marcarEliminado(mes.toString(), reloj.ahoraEnMillis())
    }
}
