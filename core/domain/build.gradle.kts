plugins {
    alias(libs.plugins.miplata.jvm.library)
    alias(libs.plugins.kover)
    // Los fakes de repositorio se publican como test fixtures para que tambien
    // los usen los tests de los modulos :feature:*. Duplicarlos en cada modulo
    // seria garantizar que se desincronicen del contrato real.
    `java-test-fixtures`
}

// Sin `android { }`, sin namespace, sin nada de AndroidX.
//
// Si algún día este archivo necesita el plugin de Android, algo se rompió en la
// arquitectura: revisar docs/01 antes de añadirlo.

// La cobertura se exige SOLO aquí, y es deliberado.
//
// Este es el modulo donde vive la logica financiera: los saldos, los sobregiros,
// el redondeo. Es Kotlin puro, se testea sin emulador en menos de un segundo, y
// no tiene una sola excusa para estar sin cubrir. En los modulos de UI, en
// cambio, perseguir un porcentaje alto empuja a escribir tests de humo que no
// verifican nada.
//
// El 90% es un SUELO, no un objetivo. Sirve para que una regresion de cobertura
// se note; no para que nadie escriba tests de relleno persiguiendo el numero.
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

dependencies {
    // Los tests del propio modulo consumen sus fixtures como cualquier otro
    // modulo, para que el contrato se pruebe tal y como lo veran los demas.
    testImplementation(testFixtures(project(":core:domain")))
}
