package com.miplata.feature.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
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
    viewModel: ResumenViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaResumen(estado, viewModel::alEvento, modifier)
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
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Cabecera(estado, dinero, alEvento) }

        if (estado.sobregiroPorIngresosQueNoLlegaron) {
            item { Aviso(stringResource(R.string.resumen_falto_ingreso), MiPlataTheme.dinero.sobregiro) }
        }

        if (!estado.hayPlan && !estado.cargando) {
            item { Aviso(stringResource(R.string.resumen_sin_plan)) }
        }

        if (estado.hayPlan) {
            item { Comparativa(estado, dinero) }
            item { Ritmo(estado) }
            item { TituloDeSeccion(stringResource(R.string.resumen_desviaciones)) }

            if (estado.desviaciones.isEmpty()) {
                item { Aviso(stringResource(R.string.resumen_sin_desviaciones)) }
            }
            items(estado.desviaciones, key = { it.linea.id.valor }) { desviacion ->
                FilaDeDesviacion(desviacion, dinero)
            }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { alEvento(EventoDelResumen.MesAnterior) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.resumen_mes_anterior),
                )
            }
            Text(
                text = estado.mes.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(120.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { alEvento(EventoDelResumen.MesSiguiente) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.resumen_mes_siguiente),
                )
            }
        }

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
        Text(
            text = dinero.formatear(estado.disponibleReal.valorAbsoluto()),
            style = EstilosDeDinero.destacado,
            color =
                if (estado.enSobregiro) MiPlataTheme.dinero.sobregiro else MiPlataTheme.dinero.ingreso,
        )
    }
}

/**
 * Plan contra realidad.
 *
 * Es la comparacion que da sentido a la app: el numero grande de arriba dice
 * como vas, y esto dice por que.
 *
 * A partir de cierta escala de fuente la tabla de dos columnas deja de caber y
 * los importes se parten por la mitad -"$2,000.0" y un "0" debajo-, que en una
 * app de dinero se lee como otra cifra. Pasado ese umbral cada concepto se
 * apila. El umbral esta en la escala y no en el ancho del movil porque el
 * problema lo crea el tamano del texto, no el de la pantalla.
 */
@Composable
private fun Comparativa(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
) {
    val apilado = LocalDensity.current.fontScale >= ESCALA_QUE_NO_CABE

    Column(modifier = Modifier.padding(top = 16.dp)) {
        TituloDeSeccion(stringResource(R.string.resumen_comparativa))

        Concepto(
            etiqueta = stringResource(R.string.resumen_ingresos),
            planificado = dinero.formatear(estado.ingresosPlanificados),
            real = dinero.formatear(estado.ingresosReales),
            color = MiPlataTheme.dinero.ingreso,
            apilado = apilado,
            conCabecera = !apilado,
        )
        Concepto(
            etiqueta = stringResource(R.string.resumen_gastos),
            planificado = dinero.formatear(estado.gastosPlanificados),
            real = dinero.formatear(estado.gastosReales),
            color = MiPlataTheme.dinero.gasto,
            apilado = apilado,
        )
    }
}

@Composable
private fun Concepto(
    etiqueta: String,
    planificado: String,
    real: String,
    color: Color,
    apilado: Boolean,
    conCabecera: Boolean = false,
) {
    val plan = stringResource(R.string.resumen_columna_plan)
    val realidad = stringResource(R.string.resumen_columna_real)

    if (apilado) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.titleSmall)
            FilaDeValor(plan, planificado, color)
            FilaDeValor(realidad, real, color)
        }
        return
    }

    if (conCabecera) {
        FilaDeTabla(
            etiqueta = "",
            izquierda = plan,
            derecha = realidad,
            estilo = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FilaDeTabla(etiqueta, planificado, real, EstilosDeDinero.enLista, color)
}

@Composable
private fun FilaDeValor(
    etiqueta: String,
    valor: String,
    color: Color,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(valor, style = EstilosDeDinero.enLista, color = color)
    }
}

/**
 * El ritmo, no el total.
 *
 * A dia 10 con el 70% del presupuesto gastado el total todavia cuadra, pero el
 * mes no llega. Las dos barras juntas lo ensenan sin tener que explicarlo.
 */
@Composable
private fun Ritmo(estado: ResumenUiState) {
    val gasto = estado.progresoDelGasto ?: return

    Column(modifier = Modifier.padding(top = 16.dp)) {
        TituloDeSeccion(stringResource(R.string.resumen_ritmo))

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

        if (estado.gastaMasDeprisaQuePasaElMes) {
            Aviso(stringResource(R.string.resumen_ritmo_aviso), MiPlataTheme.dinero.sobregiro)
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                // La barra y sus dos textos son UNA sola cosa que leer. Sin esto
                // un lector de pantalla anuncia tres nodos sueltos y hay que
                // reconstruir la frase mentalmente.
                .clearAndSetSemantics { contentDescription = descripcion },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(texto, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.resumen_porcentaje, porcentaje),
                style = EstilosDeDinero.secundario,
            )
        }
        LinearProgressIndicator(
            // Pasarse del 100% es justo el caso interesante, pero una barra no
            // puede dibujar mas que llena: se recorta aqui y el porcentaje de
            // encima sigue diciendo la verdad.
            progress = { progreso.coerceIn(0.0, 1.0).toFloat() },
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FilaDeDesviacion(
    desviacion: DesviacionLinea,
    dinero: FormateadorDeDinero,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = desviacion.linea.nombre,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // Con signo: aqui el signo ES la informacion. "+120" dice de un
            // vistazo que te pasaste, y "-300" que el ingreso no llego.
            Text(
                text = dinero.formatearConSigno(desviacion.desviacion),
                style = EstilosDeDinero.enLista,
                color = MiPlataTheme.dinero.sobregiro,
            )
        }
        Text(
            text =
                stringResource(
                    R.string.resumen_desviacion_detalle,
                    dinero.formatear(desviacion.real),
                    dinero.formatear(desviacion.planificado),
                ),
            style = EstilosDeDinero.secundario,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilaDeTabla(
    etiqueta: String,
    izquierda: String,
    derecha: String,
    estilo: TextStyle,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(PESO_ETIQUETA),
        )
        // Con peso y no con un ancho en dp: las dos columnas de importes se
        // reparten lo que sobra, asi que crecen con la fuente en vez de
        // estrangular la cifra.
        Text(izquierda, style = estilo, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(derecha, style = estilo, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TituloDeSeccion(texto: String) {
    Column {
        HorizontalDivider()
        Text(
            text = texto,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

private const val CIEN = 100.0
private const val PESO_ETIQUETA = 0.9f
private const val ESCALA_QUE_NO_CABE = 1.5f

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
