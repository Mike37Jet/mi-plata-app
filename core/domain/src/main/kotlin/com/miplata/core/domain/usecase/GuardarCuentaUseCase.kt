package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.RolDeCuenta
import com.miplata.core.domain.repository.CuentaRepository
import kotlinx.coroutines.flow.first

/**
 * Guarda una cuenta cuidando que **solo haya una principal**.
 *
 * Con dos principales no se sabria a cual le llega el ingreso ni desde cual se
 * reparte. Elegir una nueva no pregunta: la anterior pasa a ser independiente,
 * igual que al elegir otra opcion en un grupo de botones de radio.
 */
class GuardarCuentaUseCase(
    private val cuentas: CuentaRepository,
) {
    suspend operator fun invoke(cuenta: Cuenta) {
        if (cuenta.esPrincipal) {
            cuentas
                .observarTodas()
                .first()
                .filter { it.esPrincipal && it.id != cuenta.id }
                .forEach { cuentas.guardar(it.copy(rol = RolDeCuenta.Independiente)) }
        }
        cuentas.guardar(cuenta)
    }
}
