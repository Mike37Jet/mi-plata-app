package com.miplata.feature.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.componentes.BotonDeAjustes
import com.miplata.core.designsystem.componentes.CifraPrincipal
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.componentes.SelectorDeMes
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.DesviacionLinea
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea
import kotlin.math.roundToInt

@Composable
fun PantallaResumen(
    modifier: Modifier = Modifier,
    alAbrirAjustes: () -> Unit = {},
    alAbrirCierre: (Mes) -> Unit = {},
    alAbrirMovimientos: () -> Unit = {},
    alAbrirMesesCerrados: () -> Unit = {},
    viewModel: ResumenViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaResumen(
        estado = estado,
        alEvento = viewModel::alEvento,
        modifier = modifier,
        alAbrirAjustes = alAbrirAjustes,
        alAbrirCierre = alAbrirCierre,
        alAbrirMovimientos = alAbrirMovimientos,
        alAbrirMesesCerrados = alAbrirMesesCerrados,
    )
}

/**
 * La pantalla como funcion pura de su estado, igual que la del plan.
 *
 * Todo lo que dibuja sale de `CalcularResumenMensualUseCase`; aqui no se suma ni
 * se compara nada. Si una cifra esta mal, esta mal en el dominio, donde hay
 * tests que la cubren (docs/01).
 */
@Composable
internal fun PantallaResumen(
    estado: ResumenUiState,
    alEvento: (EventoDelResumen) -> Unit,
    modifier: Modifier = Modifier,
    alAbrirAjustes: () -> Unit = {},
    alAbrirCierre: (Mes) -> Unit = {},
    alAbrirMovimientos: () -> Unit = {},
    alAbrirMesesCerrados: () -> Unit = {},
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    PantallaConTituloGrande(
        titulo = stringResource(R.string.resumen_titulo),
        modifier = modifier,
        acciones = { BotonDeAjustes(alAbrirAjustes) },
    ) { relleno ->
        ContenidoDelResumen(
            estado = estado,
            dinero = dinero,
            alEvento = alEvento,
            relleno = relleno,
            accesos = Accesos(alAbrirCierre = { alAbrirCierre(estado.mes) }, alAbrirMovimientos, alAbrirMesesCerrados),
        )
    }
}

@Composable
private fun ContenidoDelResumen(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelResumen) -> Unit,
    relleno: PaddingValues,
    accesos: Accesos,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = relleno,
        verticalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        item { Cabecera(estado, dinero, alEvento) }

        if (estado.sobregiroPorIngresosQueNoLlegaron) {
            item { Aviso(stringResource(R.string.resumen_falto_ingreso), MiPlataTheme.dinero.sobregiro) }
        }

        if (!estado.hayPlan && !estado.cargando) {
            item { Aviso(stringResource(R.string.resumen_sin_plan)) }
        }

        item { BloqueDeAccesos(estado, accesos) }

        if (estado.hayPlan && estado.porCuenta.isNotEmpty()) {
            item { PorCuenta(estado, dinero) }
        }

        if (estado.hayPlan) {
            item { PlanYRealidad(estado, dinero) }
            if (estado.progresoDelGasto != null) item { Ritmo(estado, estado.progresoDelGasto) }
            item { Desviaciones(estado, dinero) }
        }
    }
}

@Composable
private fun Cabecera(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelResumen) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SelectorDeMes(
            mes = estado.mes,
            alAnterior = { alEvento(EventoDelResumen.MesAnterior) },
            alSiguiente = { alEvento(EventoDelResumen.MesSiguiente) },
        )

        Text(
            text =
                stringResource(
                    if (estado.enSobregiro) R.string.resumen_sobregiro else R.string.resumen_disponible,
                ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Siempre en valor absoluto: el signo lo dice la etiqueta de arriba.
        // "Te faltan -120" se lee como una doble negacion y confunde.
        CifraPrincipal(
            texto = dinero.formatear(estado.disponibleReal.valorAbsoluto()),
            color =
                if (estado.enSobregiro) MiPlataTheme.dinero.sobregiro else MiPlataTheme.dinero.ingreso,
        )
        // La referencia de la cifra: "te quedan 1.600" no dice si es mucho o
        // poco hasta que sabes que planeabas quedarte con 2.000.
        if (estado.hayPlan) {
            Text(
                text = stringResource(R.string.resumen_de_planeados, dinero.formatear(estado.disponiblePlanificado)),
                style = EstilosDeDinero.secundario,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Plan contra realidad: el numero grande dice como vas, y esto dice por que.
 *
 * Una fila por concepto con lo real a la derecha, lo planeado debajo y una
 * barra de cuanto se lleva. Antes era una tabla de dos columnas que, con letra
 * grande, partia los importes por la mitad -"$2,000.0" y un "0" debajo- y
 * tenia que reorganizarse aparte. La fila de lista ya se adapta sola.
 */
@Composable
private fun PlanYRealidad(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
) {
    GrupoDeLista(titulo = stringResource(R.string.resumen_comparativa)) {
        Concepto(
            etiqueta = stringResource(R.string.resumen_ingresos),
            real = estado.ingresosReales,
            planificado = estado.ingresosPlanificados,
            color = MiPlataTheme.dinero.ingreso,
            dinero = dinero,
        )
        Concepto(
            etiqueta = stringResource(R.string.resumen_gastos),
            real = estado.gastosReales,
            planificado = estado.gastosPlanificados,
            color = MiPlataTheme.dinero.gasto,
            dinero = dinero,
            conSeparador = true,
        )
    }
}

@Composable
private fun Concepto(
    etiqueta: String,
    real: Money,
    planificado: Money,
    color: Color,
    dinero: FormateadorDeDinero,
    conSeparador: Boolean = false,
) {
    Column {
        FilaDeLista(
            titulo = etiqueta,
            detalle = stringResource(R.string.resumen_de_planeados, dinero.formatear(planificado)),
            final = { Text(dinero.formatear(real), style = EstilosDeDinero.enLista, color = color) },
            conSeparador = conSeparador,
        )
        if (!planificado.esCero) {
            BarraDeProgreso(
                progreso = real.centavos.toDouble() / planificado.centavos,
                color = color,
                modifier = Modifier.padding(start = Espacio.m, end = Espacio.m, bottom = Espacio.s),
            )
        }
    }
}

/**
 * El ritmo, no el total.
 *
 * A dia 10 con el 70% del presupuesto gastado el total todavia cuadra, pero el
 * mes no llega. Las dos barras juntas lo enseñan sin tener que explicarlo.
 */
@Composable
private fun Ritmo(
    estado: ResumenUiState,
    gasto: Double,
) {
    GrupoDeLista(
        titulo = stringResource(R.string.resumen_ritmo),
        pie = if (estado.gastaMasDeprisaQuePasaElMes) stringResource(R.string.resumen_ritmo_aviso) else null,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
            modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.s),
        ) {
            Barra(stringResource(R.string.resumen_ritmo_mes), estado.progresoDelMes, MaterialTheme.colorScheme.primary)
            Barra(
                texto = stringResource(R.string.resumen_ritmo_gasto),
                progreso = gasto,
                color =
                    if (estado.gastaMasDeprisaQuePasaElMes) {
                        MiPlataTheme.dinero.sobregiro
                    } else {
                        MiPlataTheme.dinero.gasto
                    },
            )
        }
    }
}

@Composable
private fun Barra(
    texto: String,
    progreso: Double,
    color: Color,
) {
    val porcentaje = (progreso * CIEN).roundToInt()
    val descripcion = "$texto: ${stringResource(R.string.resumen_porcentaje, porcentaje)}"

    Column(
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        modifier =
            Modifier
                .fillMaxWidth()
                // La barra y sus dos textos son UNA sola cosa que leer. Sin esto
                // un lector de pantalla anuncia tres nodos sueltos y hay que
                // reconstruir la frase mentalmente.
                .clearAndSetSemantics { contentDescription = descripcion },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.resumen_porcentaje, porcentaje), style = EstilosDeDinero.secundario)
        }
        BarraDeProgreso(progreso, color)
    }
}

/**
 * Una barra fina de progreso.
 *
 * Pasarse del 100% es justo el caso interesante, pero una barra no puede
 * dibujar mas que llena: se recorta aqui, y la cifra de al lado sigue diciendo
 * la verdad.
 */
@Composable
private fun BarraDeProgreso(
    progreso: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progreso.coerceIn(0.0, 1.0).toFloat() },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
        modifier = modifier.fillMaxWidth(),
    )
}

/** Las lineas que se fueron del plan, de la peor a la menos mala. */
@Composable
private fun Desviaciones(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
) {
    if (estado.desviaciones.isEmpty()) {
        GrupoDeLista(
            titulo = stringResource(R.string.resumen_desviaciones),
            pie = stringResource(R.string.resumen_sin_desviaciones),
        ) {}
        return
    }
    GrupoDeLista(titulo = stringResource(R.string.resumen_desviaciones)) {
        estado.desviaciones.forEachIndexed { i, desviacion ->
            FilaDeLista(
                titulo = desviacion.linea.nombre.ifBlank { stringResource(R.string.resumen_sin_nombre) },
                detalle =
                    stringResource(
                        R.string.resumen_desviacion_detalle,
                        dinero.formatear(desviacion.real),
                        dinero.formatear(desviacion.planificado),
                    ),
                // Con signo: aqui el signo ES la informacion. "+120" dice de un
                // vistazo que te pasaste, y "-300" que el ingreso no llego.
                final = {
                    Text(
                        text = dinero.formatearConSigno(desviacion.desviacion),
                        style = EstilosDeDinero.enLista,
                        color = MiPlataTheme.dinero.sobregiro,
                    )
                },
                conSeparador = i > 0,
            )
        }
    }
}

@Composable
private fun Aviso(
    texto: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Espacio.xs),
    )
}

private const val CIEN = 100.0

@Preview(showBackground = true)
@Composable
private fun PantallaResumenPreview() {
    MiPlataTheme {
        PantallaResumen(
            estado =
                ResumenUiState(
                    mes = Mes.de(2026, 3),
                    cargando = false,
                    hayPlan = true,
                    ingresosPlanificados = Money.deUnidades(2000),
                    ingresosReales = Money.deUnidades(2000),
                    gastosPlanificados = Money.deUnidades(1500),
                    gastosReales = Money.deUnidades(1180),
                    disponiblePlanificado = Money.deUnidades(500),
                    disponibleReal = Money.deUnidades(820),
                    progresoDelMes = 0.33,
                    progresoDelGasto = 0.79,
                    desviaciones =
                        listOf(
                            DesviacionLinea(
                                linea =
                                    LineaDePlan(
                                        id = LineaId("comida"),
                                        nombre = "Comida",
                                        tipo = TipoDeLinea.GASTO_VARIABLE,
                                        montoPlanificado = Money.deUnidades(400),
                                    ),
                                planificado = Money.deUnidades(400),
                                real = Money.deUnidades(520),
                            ),
                        ),
                ),
            alEvento = {},
        )
    }
}
