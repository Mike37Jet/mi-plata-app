package com.miplata.feature.bienvenida

import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import java.util.Currency
import java.util.Locale

/** Los pasos de la bienvenida, en el orden en que se recorren. */
enum class PasoDeBienvenida {
    /** Que es la app, y la salida para quien ya tiene una copia. */
    BIENVENIDA,

    /** Moneda y dia en que empieza el mes: las dos reglas que lo cambian todo. */
    TU_MES,

    PRIMERA_CUENTA,

    /** Si separa su dinero en sobres, cuales (docs/adr/0007). Ninguno es valido. */
    SOBRES,

    /** Lo unico que se puede saltar. */
    PRIMER_INGRESO,
}

/**
 * Los sobres que se proponen, los del metodo con el que se penso la app. El
 * nombre lo pone la pantalla; aqui solo esta que es cada uno.
 */
enum class SobreSugerido(
    val intocable: Boolean = false,
) {
    LIBERTAD_FINANCIERA(intocable = true),
    AHORROS,
    DIVERSION,
    ENTRENAMIENTO,
}

data class BienvenidaUiState(
    val paso: PasoDeBienvenida = PasoDeBienvenida.BIENVENIDA,
    val moneda: Moneda,
    val monedasSugeridas: List<Moneda>,
    val primerDiaDelMes: Int = 1,
    val nombreDeLaCuenta: String = "",
    val tipoDeCuenta: TipoDeCuenta = TipoDeCuenta.BANCARIA,
    val saldoActual: Money = Money.ZERO,
    val ingresoMensual: Money = Money.ZERO,
    /** Los sobres elegidos, con lo que tiene cada uno hoy. */
    val sobres: Map<SobreSugerido, Money> = emptyMap(),
    /** Mientras se guarda: los botones se desactivan para no guardar dos veces. */
    val guardando: Boolean = false,
    /**
     * Por que fallo el ultimo intento de guardar, o `null` si no ha fallado.
     * Puede ser un texto vacio: fallo, pero el sistema no dijo por que.
     */
    val errorAlGuardar: String? = null,
) {
    /**
     * Si se puede pasar al siguiente paso.
     *
     * Solo la cuenta pone una condicion: sin nombre no se puede elegir de una
     * lista, y el dominio la rechazaria (docs/03). Se enseña antes de pulsar en
     * vez de fallar despues.
     */
    val puedeSeguir: Boolean
        get() = !guardando && (paso != PasoDeBienvenida.PRIMERA_CUENTA || nombreDeLaCuenta.isNotBlank())
}

sealed interface EventoDeBienvenida {
    data object Siguiente : EventoDeBienvenida

    data object Atras : EventoDeBienvenida

    data class ElegirMoneda(
        val moneda: Moneda,
    ) : EventoDeBienvenida

    data class CambiarPrimerDia(
        val dia: Int,
    ) : EventoDeBienvenida

    data class CambiarNombreDeLaCuenta(
        val nombre: String,
    ) : EventoDeBienvenida

    data class ElegirTipoDeCuenta(
        val tipo: TipoDeCuenta,
    ) : EventoDeBienvenida

    data class CambiarSaldo(
        val saldo: Money,
    ) : EventoDeBienvenida

    data class CambiarIngreso(
        val ingreso: Money,
    ) : EventoDeBienvenida

    data class AlternarSobre(
        val sobre: SobreSugerido,
    ) : EventoDeBienvenida

    data class CambiarSaldoDeSobre(
        val sobre: SobreSugerido,
        val saldo: Money,
    ) : EventoDeBienvenida

    /**
     * El ultimo paso.
     *
     * @param conIngreso `false` si el usuario eligio dejar el ingreso para
     *   despues: lo que haya tecleado no se guarda.
     * @param nombreDelIngreso ya traducido; el ViewModel no conoce los recursos.
     * @param nombresDeLosSobres tambien traducidos, por lo mismo.
     */
    data class Terminar(
        val conIngreso: Boolean,
        val nombreDelIngreso: String,
        val nombresDeLosSobres: Map<SobreSugerido, String> = emptyMap(),
    ) : EventoDeBienvenida
}

/**
 * Las monedas que se ofrecen, con la del telefono primero.
 *
 * Son las de la region para la que se hizo la app, mas dolar y euro. No se
 * ofrece la lista entera de ISO 4217: casi doscientas opciones para elegir una
 * que el telefono ya sabe. Si la del telefono no esta entre ellas, se añade.
 */
internal fun monedasSugeridas(locale: Locale): List<Moneda> {
    val delTelefono = monedaDe(locale)
    return (listOfNotNull(delTelefono) + MONEDAS_DE_LA_REGION.map(::Moneda)).distinct()
}

/** La moneda del pais del telefono, o `null` si no tiene pais o no es una moneda real. */
internal fun monedaDe(locale: Locale): Moneda? =
    runCatching { Moneda(Currency.getInstance(locale).currencyCode) }.getOrNull()

private val MONEDAS_DE_LA_REGION = listOf("USD", "EUR", "MXN", "COP", "PEN", "CLP", "ARS")
