package com.miplata.core.data.mapper

import com.miplata.core.data.database.dao.PlanConLineas
import com.miplata.core.data.database.entity.CategoriaEntity
import com.miplata.core.data.database.entity.CuentaEntity
import com.miplata.core.data.database.entity.LineaDePlanEntity
import com.miplata.core.data.database.entity.PlanEntity
import com.miplata.core.data.database.entity.TransaccionEntity
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
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import kotlinx.datetime.LocalDate

// Traduccion entre las filas de Room y los modelos del dominio.
//
// Existe porque las entities y los modelos estan separados (docs/02). El precio
// es este archivo; a cambio, un cambio de esquema no se filtra a las reglas de
// negocio y el dominio no sabe que Room existe.
//
// Los enums se guardan por NOMBRE y no por posicion: reordenar un enum en Kotlin
// es un refactor inocente que con posiciones reinterpretaria en silencio todos
// los datos ya guardados -tus gastos pasarian a ser ingresos-.

private inline fun <reified T : Enum<T>> aEnum(
    valor: String,
    campo: String,
): T =
    enumValues<T>().firstOrNull { it.name == valor }
        ?: throw DatoGuardadoInvalido("'$valor' no es un $campo valido")

// --- Cuenta ---

fun CuentaEntity.aDominio(): Cuenta =
    Cuenta(
        id = CuentaId(id),
        nombre = nombre,
        tipo = aEnum<TipoDeCuenta>(tipo, "tipo de cuenta"),
        saldoInicial = Money.deCentavos(saldoInicialCentavos),
        moneda = Moneda(moneda),
        incluirEnTotal = incluirEnTotal,
        archivada = archivada,
    )

fun Cuenta.aEntidad(
    creadaEn: Long,
    actualizadaEn: Long,
): CuentaEntity =
    CuentaEntity(
        id = id.valor,
        nombre = nombre,
        tipo = tipo.name,
        saldoInicialCentavos = saldoInicial.centavos,
        moneda = moneda.codigo,
        incluirEnTotal = incluirEnTotal,
        archivada = archivada,
        creadaEn = creadaEn,
        actualizadaEn = actualizadaEn,
    )

// --- Categoria ---

fun CategoriaEntity.aDominio(): Categoria =
    Categoria(
        id = CategoriaId(id),
        nombre = nombre,
        padreId = padreId?.let(::CategoriaId),
        icono = icono,
        color = color,
    )

fun Categoria.aEntidad(
    creadaEn: Long,
    actualizadaEn: Long,
): CategoriaEntity =
    CategoriaEntity(
        id = id.valor,
        nombre = nombre,
        padreId = padreId?.valor,
        icono = icono,
        color = color,
        creadaEn = creadaEn,
        actualizadaEn = actualizadaEn,
    )

// --- Transaccion ---

fun TransaccionEntity.aDominio(): Transaccion =
    Transaccion(
        id = TransaccionId(id),
        fecha = aFecha(fecha),
        monto = Money.deCentavos(montoCentavos),
        tipo = aEnum<TipoDeTransaccion>(tipo, "tipo de transaccion"),
        cuentaOrigenId = CuentaId(cuentaOrigenId),
        cuentaDestinoId = cuentaDestinoId?.let(::CuentaId),
        categoriaId = categoriaId?.let(::CategoriaId),
        lineaDePlanId = lineaDePlanId?.let(::LineaId),
        nota = nota,
    )

fun Transaccion.aEntidad(
    creadaEn: Long,
    actualizadaEn: Long,
): TransaccionEntity =
    TransaccionEntity(
        id = id.valor,
        fecha = fecha.toString(),
        montoCentavos = monto.centavos,
        tipo = tipo.name,
        cuentaOrigenId = cuentaOrigenId.valor,
        cuentaDestinoId = cuentaDestinoId?.valor,
        categoriaId = categoriaId?.valor,
        lineaDePlanId = lineaDePlanId?.valor,
        nota = nota,
        creadaEn = creadaEn,
        actualizadaEn = actualizadaEn,
    )

private fun aFecha(texto: String): LocalDate =
    try {
        LocalDate.parse(texto)
    } catch (e: IllegalArgumentException) {
        throw DatoGuardadoInvalido("'$texto' no es una fecha valida", e)
    }

// --- Plan ---

fun PlanConLineas.aDominio(): PlanMensual =
    PlanMensual(
        id = PlanId(plan.id),
        mes = aMes(plan.mes),
        // lineasOrdenadas y no lineas: @Relation no garantiza ningun orden, y el
        // orden del plan es del usuario.
        lineas = lineasOrdenadas.map { it.aDominio() },
    )

fun LineaDePlanEntity.aDominio(): LineaDePlan =
    LineaDePlan(
        id = LineaId(id),
        nombre = nombre,
        tipo = aEnum<TipoDeLinea>(tipo, "tipo de linea"),
        montoPlanificado = Money.deCentavos(montoPlanificadoCentavos),
        categoriaId = categoriaId?.let(::CategoriaId),
        cuentaId = cuentaId?.let(::CuentaId),
        diaDelMes = diaDelMes,
        activa = activa,
    )

fun PlanMensual.aEntidad(
    creadoEn: Long,
    actualizadoEn: Long,
): PlanEntity =
    PlanEntity(
        mes = mes.toString(),
        id = id.valor,
        creadoEn = creadoEn,
        actualizadoEn = actualizadoEn,
    )

/**
 * Las lineas del plan, numeradas por su posicion en la lista.
 *
 * El `orden` sale del indice y no de un campo del dominio: en el dominio el
 * orden **es** el de la lista, y guardarlo aparte abriria la puerta a que los
 * dos dejaran de coincidir.
 */
fun PlanMensual.lineasAEntidades(
    creadaEn: Long,
    actualizadaEn: Long,
): List<LineaDePlanEntity> =
    lineas.mapIndexed { posicion, linea ->
        LineaDePlanEntity(
            id = linea.id.valor,
            planMes = mes.toString(),
            nombre = linea.nombre,
            tipo = linea.tipo.name,
            montoPlanificadoCentavos = linea.montoPlanificado.centavos,
            categoriaId = linea.categoriaId?.valor,
            cuentaId = linea.cuentaId?.valor,
            diaDelMes = linea.diaDelMes,
            activa = linea.activa,
            orden = posicion,
            creadaEn = creadaEn,
            actualizadaEn = actualizadaEn,
        )
    }

private fun aMes(texto: String): Mes =
    try {
        Mes.de(texto)
    } catch (e: IllegalArgumentException) {
        throw DatoGuardadoInvalido("'$texto' no es un mes valido", e)
    }
