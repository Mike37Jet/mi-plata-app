package com.miplata.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.app.navigation.NavegacionPrincipal
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.feature.backup.recordatorio.EXTRA_ABRIR_COPIA_DE_SEGURIDAD
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

        // Solo en un arranque nuevo: al girar la pantalla la Activity se recrea
        // con el mismo intent, y volveria a saltar a la copia.
        val abrirCopia =
            savedInstanceState == null && intent.getBooleanExtra(EXTRA_ABRIR_COPIA_DE_SEGURIDAD, false)

        setContent {
            AplicacionMiPlata(ajustes.observar(), abrirCopia)
        }
    }
}

@Composable
private fun AplicacionMiPlata(
    ajustes: Flow<Ajustes>,
    abrirCopia: Boolean,
) {
    val configuracion by ajustes.collectAsStateWithLifecycle(initialValue = Ajustes())

    // El tema del usuario manda; si no ha elegido, el del sistema.
    val oscuro =
        when (configuracion.tema) {
            Tema.CLARO -> false
            Tema.OSCURO -> true
            Tema.SEGUN_EL_SISTEMA -> isSystemInDarkTheme()
        }

    BarrasDelSistemaSegunElTema(oscuro)

    MiPlataTheme(temaOscuro = oscuro) {
        NavegacionPrincipal(abrirCopiaAlEmpezar = abrirCopia)
    }
}

/**
 * Los iconos de la barra de estado y de navegacion, del color que se lee sobre
 * el tema **de la app**.
 *
 * `enableEdgeToEdge()` sin argumentos los ajusta al tema del sistema. Si el
 * usuario elige el tema oscuro con el movil en claro, la hora y la bateria
 * salian en negro sobre el fondo negro de la app: invisibles. Se vuelve a
 * llamar cada vez que cambia el tema, con el que manda de verdad.
 */
@Composable
private fun BarrasDelSistemaSegunElTema(oscuro: Boolean) {
    val actividad = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(oscuro) {
        actividad.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro },
            navigationBarStyle = SystemBarStyle.auto(VELO_CLARO, VELO_OSCURO) { oscuro },
        )
        onDispose {}
    }
}

// Los mismos velos que pone `enableEdgeToEdge()` por defecto tras la barra de
// navegacion de tres botones. La libreria no los expone.
private val VELO_CLARO = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val VELO_OSCURO = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
