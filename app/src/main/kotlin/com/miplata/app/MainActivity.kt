package com.miplata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CategoriaRepository
import com.miplata.core.domain.repository.CuentaRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Unica Activity de la app (single-activity + Navigation Compose).
 *
 * En la Etapa 3 su contenido pasa a ser el `NavHost`. Lo que hay ahora es un
 * marcador de posicion que ademas **ejerce el grafo de dependencias**: pide un
 * repositorio de cada capa, asi que si Hilt, SQLCipher o DataStore estuvieran
 * mal conectados, la app no arrancaria. Compilar no lo demuestra; solo
 * ejecutarlo lo demuestra.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // TODO(Etapa 3): lo pide un ViewModel, no la Activity.
    @Inject
    lateinit var ajustes: AjustesRepository

    @Inject
    lateinit var cuentas: CuentaRepository

    @Inject
    lateinit var categorias: CategoriaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // TODO(Etapa 0.4): reemplazar por MiPlataTheme de :core:designsystem.
            MaterialTheme {
                PantallaPlaceholder(
                    ajustes.observar(),
                    cuentas.observarTodas(),
                    categorias.observarTodas(),
                )
            }
        }
    }
}

@Composable
private fun PantallaPlaceholder(
    ajustes: Flow<Ajustes>,
    cuentas: Flow<List<Cuenta>>,
    categorias: Flow<List<Categoria>>,
    modifier: Modifier = Modifier,
) {
    val configuracion by ajustes.collectAsStateWithLifecycle(initialValue = null)
    val lasCuentas by cuentas.collectAsStateWithLifecycle(initialValue = emptyList())
    val lasCategorias by categorias.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.placeholder_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.placeholder_subtitle),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "moneda: ${configuracion?.moneda ?: "..."}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "cuentas guardadas: ${lasCuentas.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "categorias: ${lasCategorias.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = lasCategorias.filter { it.esRaiz }.take(4).joinToString { it.nombre },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PantallaPlaceholderPreview() {
    MaterialTheme {
        PantallaPlaceholder(
            ajustes = kotlinx.coroutines.flow.flowOf(Ajustes()),
            cuentas = kotlinx.coroutines.flow.flowOf(emptyList()),
            categorias = kotlinx.coroutines.flow.flowOf(emptyList()),
        )
    }
}
