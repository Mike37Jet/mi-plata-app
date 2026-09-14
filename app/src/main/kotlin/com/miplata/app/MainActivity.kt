package com.miplata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.app.navigation.NavegacionPrincipal
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.repository.AjustesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Unica Activity de la app.
 *
 * Solo hace dos cosas: decidir el tema segun lo que el usuario haya elegido, y
 * ceder el resto al grafo de navegacion. Todo lo demas vive en los features.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // TODO(Etapa 3.3): el tema lo pedira un ViewModel, no la Activity.
    @Inject
    lateinit var ajustes: AjustesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AplicacionMiPlata(ajustes.observar())
        }
    }
}

@Composable
private fun AplicacionMiPlata(ajustes: Flow<Ajustes>) {
    val configuracion by ajustes.collectAsStateWithLifecycle(initialValue = Ajustes())

    // El tema del usuario manda; si no ha elegido, el del sistema.
    val oscuro =
        when (configuracion.tema) {
            Tema.CLARO -> false
            Tema.OSCURO -> true
            Tema.SEGUN_EL_SISTEMA -> isSystemInDarkTheme()
        }

    MiPlataTheme(temaOscuro = oscuro) {
        NavegacionPrincipal()
    }
}
