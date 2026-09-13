import com.android.build.api.dsl.ApplicationExtension
import com.miplata.convention.configureDetekt
import com.miplata.convention.configureKotlinAndroid
import com.miplata.convention.libs
import com.miplata.convention.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

/** Convention plugin del módulo de aplicación (`:app`). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("io.gitlab.arturbosch.detekt")
            }

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                configurarFirmaDeRelease(this@with)

                defaultConfig {
                    targetSdk = libs.version("targetSdk").toInt()
                    versionCode = 1
                    versionName = "0.1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildTypes {
                    debug {
                        applicationIdSuffix = ".debug"
                        versionNameSuffix = "-debug"
                        isMinifyEnabled = false
                    }
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = signingConfigs.findByName("release")
                    }
                }
            }

            configureDetekt()
        }
}

/**
 * Firma del APK de release con un keystore local.
 *
 * La app no se publica en ninguna tienda: se instala como APK firmado a mano
 * (docs/06). Por eso la clave vive en un `keystore.properties` de la raiz, fuera
 * del control de versiones, y no en secretos de CI: automatizar con una clave
 * que se usa tres veces al ano seria anadir un secreto que proteger a cambio de
 * nada.
 *
 * **Si el archivo no existe, el build de release sigue funcionando** y sale sin
 * firmar. Eso es lo que permite que CI compile la variante de release sin tener
 * acceso a ninguna clave: un build que exigiera el keystore convertiria la firma
 * en un requisito para cualquiera que clone el repositorio.
 */
private fun ApplicationExtension.configurarFirmaDeRelease(project: Project) {
    val propiedades = project.rootProject.file("keystore.properties")
    if (!propiedades.exists()) return

    val credenciales = Properties().apply { propiedades.inputStream().use(::load) }
    val archivo = credenciales.getProperty("storeFile")?.takeIf { it.isNotBlank() } ?: return

    signingConfigs.create("release") {
        storeFile = project.file(archivo)
        storePassword = credenciales.getProperty("storePassword")
        keyAlias = credenciales.getProperty("keyAlias")
        keyPassword = credenciales.getProperty("keyPassword")
    }
}
