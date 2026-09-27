package com.miplata.core.data.mapper

import com.miplata.core.data.database.dao.CierreConSaldos
import com.miplata.core.data.database.entity.CierreEntity
import com.miplata.core.data.database.entity.SaldoDeCierreEntity
import com.miplata.core.domain.model.CierreDeMes
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.SaldoDeCierre

// Traduccion de los cierres de mes (docs/adr/0007), aparte del resto de
// mappers para que cada archivo trate un tema.

fun CierreConSaldos.aDominio(): CierreDeMes =
    CierreDeMes(
        mes = aMes(cierre.mes),
        saldos =
            saldos.map {
                SaldoDeCierre(
                    cuentaId = CuentaId(it.cuentaId),
                    esperado = Money.deCentavos(it.esperadoCentavos),
                    real = Money.deCentavos(it.realCentavos),
                )
            },
    )

fun CierreDeMes.aEntidad(creadoEn: Long): CierreEntity = CierreEntity(mes = mes.toString(), creadoEn = creadoEn)

fun CierreDeMes.saldosAEntidades(): List<SaldoDeCierreEntity> =
    saldos.map {
        SaldoDeCierreEntity(
            cierreMes = mes.toString(),
            cuentaId = it.cuentaId.valor,
            esperadoCentavos = it.esperado.centavos,
            realCentavos = it.real.centavos,
        )
    }
