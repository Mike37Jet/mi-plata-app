package com.miplata.core.data.mapper

import com.miplata.core.data.database.entity.CuentaEntity
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.Reparto
import com.miplata.core.domain.model.RolDeCuenta

// El rol de una cuenta en el presupuesto por cuentas (docs/adr/0007), entre
// sus columnas y el tipo cerrado del dominio.

internal fun CuentaEntity.rolDeCuenta(): RolDeCuenta =
    when (rol) {
        CuentaEntity.ROL_INDEPENDIENTE -> RolDeCuenta.Independiente
        CuentaEntity.ROL_PRINCIPAL -> RolDeCuenta.Principal
        CuentaEntity.ROL_SOBRE -> RolDeCuenta.Sobre(repartoDelSobre(), intocable)
        else -> throw DatoGuardadoInvalido("'$rol' no es un rol de cuenta valido")
    }

private fun CuentaEntity.repartoDelSobre(): Reparto {
    val porcentaje = repartoPorcentaje
    val monto = repartoMontoCentavos
    return when {
        porcentaje != null && monto == null -> Reparto.Porcentaje(porcentaje)
        monto != null && porcentaje == null -> Reparto.Monto(Money.deCentavos(monto))
        else -> throw DatoGuardadoInvalido("El sobre $id tiene que tener un porcentaje o un monto, y solo uno")
    }
}

internal fun RolDeCuenta.aColumna(): String =
    when (this) {
        RolDeCuenta.Independiente -> CuentaEntity.ROL_INDEPENDIENTE
        RolDeCuenta.Principal -> CuentaEntity.ROL_PRINCIPAL
        is RolDeCuenta.Sobre -> CuentaEntity.ROL_SOBRE
    }
