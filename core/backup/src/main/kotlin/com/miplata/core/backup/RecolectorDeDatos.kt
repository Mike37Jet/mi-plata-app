package com.miplata.core.backup

import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import kotlinx.coroutines.flow.first

/**
 * Reune todo lo que el usuario tiene guardado, listo para meter en un backup.
 *
 * Habla con los **repositorios del dominio**, no con Room: el backup no sabe si
 * los datos vienen de SQLite, de un fichero o de una prueba en memoria. Eso es
 * lo que permite probar el round-trip entero sin base de datos.
 *
 * Cada repositorio expone un "dame todo" y aqui se usa tal cual. La alternativa
 * -deducir que meses tienen plan a partir de las fechas de los movimientos- era
 * adivinar, y lo primero que habria perdido es el plan del mes que acabas de
 * armar y todavia no has ejecutado.
 */
class RecolectorDeDatos(
    private val cuentas: CuentaRepository,
    private val categorias: CategoriaRepository,
    private val transacciones: TransaccionRepository,
    private val planes: PlanRepository,
    private val ajustes: AjustesRepository,
) {
    suspend fun recolectar(): DatosDelBackup =
        DatosDelBackup(
            ajustes = ajustes.obtener().aDto(),
            cuentas = cuentas.observarTodas().first().map { it.aDto() },
            categorias = categorias.observarTodas().first().map { it.aDto() },
            transacciones = transacciones.observarTodas().first().map { it.aDto() },
            planes = planes.observarTodos().first().map { it.aDto() },
        )
}
