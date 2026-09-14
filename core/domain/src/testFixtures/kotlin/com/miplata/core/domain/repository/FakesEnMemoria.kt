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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// Implementaciones en memoria de los repositorios, para tests.
//
// Fakes escritos a mano en lugar de mocks (docs/02). Un mock con ocho `every {}`
// describe llamadas; un fake describe COMPORTAMIENTO, y es el comportamiento lo
// que los tests necesitan: guardar algo y que la consulta lo devuelva, que el
// Flow emita al cambiar.
//
// Guardan el estado en un MutableStateFlow, asi que emiten de verdad ante cada
// cambio igual que hara Room. Un fake que no emitiera dejaria pasar tests que
// luego fallan con la implementacion real.

class FakeCuentaRepository(
    iniciales: List<Cuenta> = emptyList(),
) : CuentaRepository {
    private val estado = MutableStateFlow(iniciales.associateBy { it.id })

    override fun observarTodas(): Flow<List<Cuenta>> = estado.map { it.values.toList() }

    override suspend fun obtener(id: CuentaId): Cuenta? = estado.value[id]

    override suspend fun guardar(cuenta: Cuenta) {
        estado.update { it + (cuenta.id to cuenta) }
    }

    override suspend fun eliminar(id: CuentaId) {
        estado.update { it - id }
    }
}

class FakeCategoriaRepository(
    iniciales: List<Categoria> = emptyList(),
) : CategoriaRepository {
    private val estado = MutableStateFlow(iniciales.associateBy { it.id })

    override fun observarTodas(): Flow<List<Categoria>> = estado.map { it.values.toList() }

    override suspend fun obtener(id: CategoriaId): Categoria? = estado.value[id]

    override suspend fun guardar(categoria: Categoria) {
        estado.update { it + (categoria.id to categoria) }
    }

    override suspend fun cuantasHay(): Int = estado.value.size

    override suspend fun eliminar(id: CategoriaId) {
        estado.update { it - id }
    }
}

class FakeTransaccionRepository(
    iniciales: List<Transaccion> = emptyList(),
) : TransaccionRepository {
    private val estado = MutableStateFlow(iniciales.associateBy { it.id })

    override fun observarDelPeriodo(periodo: PeriodoMensual): Flow<List<Transaccion>> =
        estado.map { transacciones ->
            transacciones.values.filter { periodo.contiene(it.fecha) }.sortedBy { it.fecha }
        }

    override fun observarDeCuenta(cuentaId: CuentaId): Flow<List<Transaccion>> =
        estado.map { transacciones ->
            transacciones.values
                .filter { it.cuentaOrigenId == cuentaId || it.cuentaDestinoId == cuentaId }
                .sortedBy { it.fecha }
        }

    override suspend fun obtener(id: TransaccionId): Transaccion? = estado.value[id]

    override suspend fun guardar(transaccion: Transaccion) {
        estado.update { it + (transaccion.id to transaccion) }
    }

    override suspend fun eliminar(id: TransaccionId) {
        estado.update { it - id }
    }
}

class FakeAjustesRepository(
    inicial: Ajustes = Ajustes(),
) : AjustesRepository {
    private val estado = MutableStateFlow(inicial)

    override fun observar(): Flow<Ajustes> = estado

    override suspend fun obtener(): Ajustes = estado.value

    override suspend fun guardar(ajustes: Ajustes) {
        estado.value = ajustes
    }
}

class FakePlanRepository(
    iniciales: List<PlanMensual> = emptyList(),
) : PlanRepository {
    private val estado = MutableStateFlow(iniciales.associateBy { it.mes })

    override fun observarDe(mes: Mes): Flow<PlanMensual?> = estado.map { it[mes] }

    override suspend fun obtenerDe(mes: Mes): PlanMensual? = estado.value[mes]

    override suspend fun obtenerUltimoAnteriorA(mes: Mes): PlanMensual? {
        // Se captura el estado UNA vez. Leerlo dos veces -una para elegir el mes
        // y otra para recuperar el plan- permitiria que un guardar o eliminar
        // concurrente se colara entre ambas y devolviera null, o un plan que no
        // corresponde a ningun estado que haya existido de verdad.
        val planes = estado.value
        return planes.keys
            .filter { it < mes }
            .maxOrNull()
            ?.let { planes[it] }
    }

    override suspend fun guardar(plan: PlanMensual) {
        estado.update { it + (plan.mes to plan) }
    }

    override suspend fun eliminar(mes: Mes) {
        estado.update { it - mes }
    }
}
