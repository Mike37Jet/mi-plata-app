package com.miplata.core.backup

import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import kotlinx.datetime.LocalDate

// Del formato del archivo al dominio.
//
// Es la direccion peligrosa: aqui entran datos que pueden venir de un archivo
// editado a mano, truncado o de otra version. Cada conversion decide de forma
// explicita si un valor raro se tolera o aborta la restauracion.

internal fun AjustesDto.aDominio() =
    Ajustes(
        moneda = Moneda(moneda),
        primerDiaDelMesFinanciero = primerDiaDelMesFinanciero,
        // Un tema desconocido -archivo de una version futura- no es motivo para
        // perder el backup entero: son preferencias, no datos financieros.
        tema = Tema.entries.firstOrNull { it.name == tema } ?: Tema.SEGUN_EL_SISTEMA,
        ultimoBackupEnMillis = ultimoBackupEnMillis,
    )

internal fun CuentaDto.aDominio() =
    Cuenta(
        id = CuentaId(id),
        nombre = nombre,
        tipo = enumDe(tipo, "tipo de cuenta"),
        saldoInicial = Money.deCentavos(saldoInicialEnCentavos),
        moneda = Moneda(moneda),
        incluirEnTotal = incluirEnTotal,
        archivada = archivada,
    )

internal fun CategoriaDto.aDominio() =
    Categoria(
        id = CategoriaId(id),
        nombre = nombre,
        padreId = padreId?.let(::CategoriaId),
        icono = icono,
        color = color,
    )

internal fun TransaccionDto.aDominio() =
    Transaccion(
        id = TransaccionId(id),
        fecha = fechaDe(fecha),
        monto = Money.deCentavos(montoEnCentavos),
        tipo = enumDe<TipoDeTransaccion>(tipo, "tipo de transaccion"),
        cuentaOrigenId = CuentaId(cuentaOrigenId),
        cuentaDestinoId = cuentaDestinoId?.let(::CuentaId),
        categoriaId = categoriaId?.let(::CategoriaId),
        lineaDePlanId = lineaDePlanId?.let(::LineaId),
        nota = nota,
    )

internal fun PlanDto.aDominio() =
    PlanMensual(
        id = PlanId(id),
        mes = mesDe(mes),
        lineas = lineas.map { it.aDominio() },
    )

internal fun LineaDePlanDto.aDominio() =
    LineaDePlan(
        id = LineaId(id),
        nombre = nombre,
        tipo = enumDe<TipoDeLinea>(tipo, "tipo de linea"),
        montoPlanificado = Money.deCentavos(montoPlanificadoEnCentavos),
        categoriaId = categoriaId?.let(::CategoriaId),
        cuentaId = cuentaId?.let(::CuentaId),
        diaDelMes = diaDelMes,
        activa = activa,
    )

/**
 * Convierte el nombre guardado al valor del enum, o falla explicando cual.
 *
 * Aqui **si** se falla, al reves que con el tema: un tipo de cuenta o de
 * transaccion desconocido cambia lo que significan las cifras. Restaurar
 * "algo parecido" en una app de dinero es peor que no restaurar.
 */
private inline fun <reified T : Enum<T>> enumDe(
    nombre: String,
    que: String,
): T =
    enumValues<T>().firstOrNull { it.name == nombre }
        ?: throw BackupInvalido("$que desconocido en el backup: '$nombre'")

private fun fechaDe(texto: String): LocalDate =
    try {
        LocalDate.parse(texto)
    } catch (e: IllegalArgumentException) {
        throw BackupInvalido("Fecha invalida en el backup: '$texto'", e)
    }

private fun mesDe(texto: String): Mes =
    try {
        Mes.de(texto)
    } catch (e: IllegalArgumentException) {
        throw BackupInvalido("Mes invalido en el backup: '$texto'", e)
    }
