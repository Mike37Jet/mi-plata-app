package com.miplata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.repository.AjustesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Unica Activity de la app (single-activity + Navigation Compose).
 *
 * En la Etapa 3 su contenido pasa a ser el `NavHost`. Lo que hay ahora es un
 * marcador de posicion que ejerce el grafo de dependencias y el tema: si Hilt,
 * SQLCipher, DataStore o los colores estuvieran mal conectados, se veria.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // TODO(Etapa 3): lo pide un ViewModel, no la Activity.
    @Inject
    lateinit var ajustes: AjustesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            Muestrario(ajustes.observar())
        }
    }
}

@Composable
private fun Muestrario(
    ajustes: Flow<Ajustes>,
    modifier: Modifier = Modifier,
) {
    val configuracion by ajustes.collectAsStateWithLifecycle(initialValue = Ajustes())

    // El tema del usuario manda; si no ha elegido, el del sistema.
    val oscuro =
        when (configuracion.tema) {
            Tema.CLARO -> false
            Tema.OSCURO -> true
            Tema.SEGUN_EL_SISTEMA -> isSystemInDarkTheme()
        }

    MiPlataTheme(temaOscuro = oscuro) {
        val dinero = recordarFormateadorDeDinero(configuracion.moneda)

        Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.placeholder_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = dinero.formatear(Money.deCentavos(148_075)),
                    style = EstilosDeDinero.destacado,
                    color = MiPlataTheme.dinero.ingreso,
                )
                Text(
                    text = "ingreso  ${dinero.formatearConSigno(Money.deUnidades(2000))}",
                    style = EstilosDeDinero.enLista,
                    color = MiPlataTheme.dinero.ingreso,
                )
                Text(
                    text = "gasto  ${dinero.formatear(Money.deUnidades(-520))}",
                    style = EstilosDeDinero.enLista,
                    color = MiPlataTheme.dinero.gasto,
                )
                Text(
                    text = "ahorro  ${dinero.formatear(Money.deUnidades(-300))}",
                    style = EstilosDeDinero.enLista,
                    color = MiPlataTheme.dinero.ahorro,
                )
                Text(
                    text = "sobregiro  ${dinero.formatear(Money.deUnidades(-84))}",
                    style = EstilosDeDinero.enLista,
                    color = MiPlataTheme.dinero.sobregiro,
                )
                Text(
                    text = stringResource(R.string.placeholder_subtitle),
                    style = EstilosDeDinero.secundario,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Claro")
@Composable
private fun MuestrarioClaroPreview() {
    Muestrario(flowOf(Ajustes(tema = Tema.CLARO)))
}

@Preview(showBackground = true, name = "Oscuro")
@Composable
private fun MuestrarioOscuroPreview() {
    Muestrario(flowOf(Ajustes(tema = Tema.OSCURO)))
}
