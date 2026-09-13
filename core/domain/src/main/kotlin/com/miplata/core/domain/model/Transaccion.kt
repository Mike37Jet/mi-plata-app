package com.miplata.core.domain.model

import kotlinx.datetime.LocalDate

enum class TipoDeTransaccion {
    INGRESO,
    GASTO,
    TRANSFERENCIA,
}

/**
 * Un movimiento que de verdad ocurrio. La mitad "realidad" del modelo; la otra
 * mitad es el [PlanMensual] (docs/00).
 *
 * El [monto] es **siempre positivo** y el signo lo pone el [tipo]. Con montos
 * con signo, perder un menos es un bug silencioso que descuadra el saldo y no
 * salta en ningun sitio; con un `enum`, olvidar un caso al sumar es un `when`
 * incompleto, y eso no compila.
 *
 * Una TRANSFERENCIA entre cuentas propias **no es ingreso ni gasto**: el dinero
 * cambia de sitio pero el patrimonio no se mueve. Contarla como gasto es el
 * error clasico que infla los informes de cualquier app de finanzas.
 *
 * Sobre el `@Suppress`: detekt avisa de nueve parametros, y el aviso es correcto
 * para una **funcion** -nueve argumentos posicionales en una llamada son
 * ilegibles-. Aqui es un contenedor de datos con valores por defecto que se
 * construye con argumentos con nombre. Los nueve campos son los que fija
 * `docs/03` para una transaccion, y agruparlos artificialmente solo moveria el
 * problema de sitio.
 */
@Suppress("LongParameterList")
data class Transaccion(
    val id: TransaccionId,
    val fecha: LocalDate,
    /** Siempre positivo. El signo lo determina [tipo]. */
    val monto: Money,
    val tipo: TipoDeTransaccion,
    val cuentaOrigenId: CuentaId,
    /** Solo en una TRANSFERENCIA; nulo en el resto. */
    val cuentaDestinoId: CuentaId? = null,
    val categoriaId: CategoriaId? = null,
    /**
     * El puente entre el plan y la realidad.
     *
     * Permite decir "planificaste 400 de comida, llevas gastados 520". Es nulo
     * cuando el movimiento no corresponde a ninguna linea del plan, que es el
     * caso de cualquier gasto imprevisto.
     */
    val lineaDePlanId: LineaId? = null,
    val nota: String? = null,
) {
    val esTransferencia: Boolean get() = tipo == TipoDeTransaccion.TRANSFERENCIA

    init {
        require(monto.esPositivo) {
            "El monto de una transaccion es siempre positivo; el signo lo pone el tipo. Recibido: $monto"
        }
        if (esTransferencia) {
            requireNotNull(cuentaDestinoId) { "Una transferencia necesita cuenta de destino" }
            require(cuentaDestinoId != cuentaOrigenId) {
                "Una transferencia necesita dos cuentas distintas"
            }
        } else {
            require(cuentaDestinoId == null) {
                "Solo una transferencia tiene cuenta de destino; $tipo no"
            }
        }
    }
}
