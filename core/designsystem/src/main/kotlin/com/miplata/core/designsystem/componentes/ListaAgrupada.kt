package com.miplata.core.designsystem.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.accesibilidad.conLetraGrande
import com.miplata.core.designsystem.theme.Espacio

/**
 * Un grupo de filas sobre una sola superficie redondeada, como los de Ajustes
 * de iOS.
 *
 * Sustituye a la tarjeta por elemento. Una tarjeta por movimiento repite su
 * relleno, su borde y su esquina tantas veces como filas haya: el marco pesa
 * mas que el dato. Un grupo pone un solo marco y separa las filas con una
 * linea fina, y el ojo lee de un tiron lo que va junto.
 *
 * @param titulo encima del grupo, en pequeño: dice que hay dentro.
 * @param pie debajo, para una aclaracion que no merece fila propia.
 * @param alLadoDelTitulo a la derecha del titulo: un total del grupo, por ejemplo.
 * @param iconoDelTitulo delante del titulo, del mismo color: un candado en una
 *   cuenta intocable, por ejemplo. Lo describe el propio titulo.
 */
@Composable
fun GrupoDeLista(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    pie: String? = null,
    alLadoDelTitulo: (@Composable () -> Unit)? = null,
    iconoDelTitulo: ImageVector? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        if (titulo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m),
            ) {
                iconoDelTitulo?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Espacio.xs).size(ICONO_DEL_TITULO),
                    )
                }
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                alLadoDelTitulo?.invoke()
            }
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = contenido)
        }
        if (pie != null) {
            Text(
                text = pie,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Espacio.m),
            )
        }
    }
}

/**
 * Una fila de un [GrupoDeLista].
 *
 * Mide al menos 55dp de alto: el tamaño de toque comodo, y un paso de la escala
 * aurea. Con letra grande crece con el texto en vez de recortarlo.
 *
 * @param inicio un icono o una marca a la izquierda.
 * @param final lo que va a la derecha: casi siempre un importe.
 * @param alPulsar si la fila se puede tocar.
 * @param conChevron la pista de que la fila lleva a otra parte, que una fila
 *   plana no da. Por defecto, en toda fila que se puede tocar. Se quita donde
 *   todas las filas se tocan y es evidente, como en una lista de movimientos.
 * @param conSeparador la linea fina de arriba. Todas la llevan menos la
 *   primera de su grupo.
 */
@Composable
fun FilaDeLista(
    titulo: String,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    inicio: (@Composable () -> Unit)? = null,
    final: (@Composable RowScope.() -> Unit)? = null,
    alPulsar: (() -> Unit)? = null,
    conChevron: Boolean = alPulsar != null,
    conSeparador: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (alPulsar != null) Modifier.clickable(role = Role.Button, onClick = alPulsar) else Modifier),
    ) {
        if (conSeparador) {
            // Con sangria, como en iOS: la linea separa filas, no el grupo del
            // resto de la pantalla.
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = Espacio.m),
            )
        }
        val contenidoDeLaFila: @Composable RowScope.() -> Unit = {
            if (inicio != null) {
                Box(modifier = Modifier.padding(end = Espacio.s)) { inicio() }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.bodyLarge)
                if (detalle != null) {
                    Text(
                        text = detalle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (final != null && !conLetraGrande()) {
                Row(verticalAlignment = Alignment.CenterVertically, content = final)
            }
            if (conChevron) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    // Decorativo: el rol de boton ya dice que se puede pulsar.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Espacio.xl)
                    .padding(horizontal = Espacio.m, vertical = Espacio.s),
            content = contenidoDeLaFila,
        )
        // Con letra grande, el importe baja a su propia linea en vez de
        // aplastar el titulo (docs/09).
        if (final != null && conLetraGrande()) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Espacio.m, end = Espacio.m, bottom = Espacio.s),
                content = final,
            )
        }
    }
}

/**
 * Una opcion de un grupo en el que solo se elige una, como las de Ajustes de
 * iOS: el nombre a la izquierda y una marca a la derecha en la elegida.
 *
 * Se anuncia como boton de opcion y dice si esta elegida: sin eso, un lector
 * de pantalla leeria tres textos sueltos sin decir cual vale. Envuelve las
 * opciones en un [GrupoDeLista] con `Modifier.selectableGroup()`.
 */
@Composable
fun FilaDeOpcion(
    titulo: String,
    elegida: Boolean,
    alElegir: () -> Unit,
    modifier: Modifier = Modifier,
    conSeparador: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(selected = elegida, role = Role.RadioButton, onClick = alElegir),
    ) {
        if (conSeparador) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = Espacio.m),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Espacio.xl)
                    .padding(horizontal = Espacio.m, vertical = Espacio.s),
        ) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (elegida) {
                Icon(
                    Icons.Filled.Check,
                    // Decorativo: el estado ya lo anuncia `selectable`.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val ICONO_DEL_TITULO = 18.dp
