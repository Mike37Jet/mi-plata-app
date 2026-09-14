package com.miplata.core.backup

import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Transaccion

// Del dominio al formato del archivo.
//
// Los enums viajan por NOMBRE y no por posicion: reordenar un enum en el dominio
// -algo que parece inocuo- cambiaria el significado de todos los archivos ya
// guardados si se serializara el ordinal.

internal fun Ajustes.aDto() =
    AjustesDto(
        moneda = moneda.codigo,
        primerDiaDelMesFinanciero = primerDiaDelMesFinanciero,
        tema = tema.name,
        ultimoBackupEnMillis = ultimoBackupEnMillis,
    )

internal fun Cuenta.aDto() =
    CuentaDto(
        id = id.valor,
        nombre = nombre,
        tipo = tipo.name,
        saldoInicialEnCentavos = saldoInicial.centavos,
        moneda = moneda.codigo,
        incluirEnTotal = incluirEnTotal,
        archivada = archivada,
    )

internal fun Categoria.aDto() =
    CategoriaDto(
        id = id.valor,
        nombre = nombre,
        padreId = padreId?.valor,
        icono = icono,
        color = color,
    )

internal fun Transaccion.aDto() =
    TransaccionDto(
        id = id.valor,
        fecha = fecha.toString(),
        montoEnCentavos = monto.centavos,
        tipo = tipo.name,
        cuentaOrigenId = cuentaOrigenId.valor,
        cuentaDestinoId = cuentaDestinoId?.valor,
        categoriaId = categoriaId?.valor,
        lineaDePlanId = lineaDePlanId?.valor,
        nota = nota,
    )

internal fun PlanMensual.aDto() =
    PlanDto(
        id = id.valor,
        mes = mes.toString(),
        lineas = lineas.map { it.aDto() },
    )

internal fun LineaDePlan.aDto() =
    LineaDePlanDto(
        id = id.valor,
        nombre = nombre,
        tipo = tipo.name,
        montoPlanificadoEnCentavos = montoPlanificado.centavos,
        categoriaId = categoriaId?.valor,
        cuentaId = cuentaId?.valor,
        diaDelMes = diaDelMes,
        activa = activa,
    )
