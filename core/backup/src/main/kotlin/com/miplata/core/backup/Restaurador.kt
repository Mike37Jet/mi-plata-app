package com.miplata.core.backup

import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository

/**
 * Vuelca en la app los datos de un backup ya leido y validado.
 *
 * El orden importa y no es casual: primero las cuentas y las categorias, luego
 * los planes, y al final las transacciones. Una transaccion apunta a una cuenta
 * y puede apuntar a una categoria y a una linea de plan, asi que si se
 * escribieran antes, las claves foraneas de Room apuntarian al vacio.
 *
 * **Esto todavia no es el flujo de restauracion completo.** Falta lo que docs/05
 * exige antes de escribir nada: backup de seguridad del estado actual, resumen
 * previo al usuario, y que todo ocurra dentro de una sola transaccion de Room
 * para que un fallo a medias no deje la app en un estado imposible. Eso llega en
 * 4.4, que es donde estara el peligro de verdad.
 */
class Restaurador(
    private val cuentas: CuentaRepository,
    private val categorias: CategoriaRepository,
    private val transacciones: TransaccionRepository,
    private val planes: PlanRepository,
    private val ajustes: AjustesRepository,
) {
    suspend fun restaurar(datos: DatosDelBackup) {
        ajustes.guardar(datos.ajustes.aDominio())

        datos.cuentas.forEach { cuentas.guardar(it.aDominio()) }
        datos.categorias.forEach { categorias.guardar(it.aDominio()) }
        datos.planes.forEach { planes.guardar(it.aDominio()) }
        datos.transacciones.forEach { transacciones.guardar(it.aDominio()) }
    }
}
