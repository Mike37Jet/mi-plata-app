## Por que

<!--
El diff ya dice QUE cambiaste. Esto es para el POR QUE: el problema que
resuelves y por que asi y no de otra forma.

Trabajando solo, este campo es lo mas valioso del PR: tu yo de dentro de seis
meses lo va a agradecer mas que cualquier comentario en el codigo.
-->

Closes #

## Como se ha verificado

<!-- Que ejecutaste o probaste, no "deberia funcionar". -->

- [ ] `CI=true ./gradlew spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug`
- [ ] Tests nuevos para el comportamiento nuevo
- [ ] Probado a mano en un dispositivo o emulador

## Comprobaciones

- [ ] Los commits siguen Conventional Commits
- [ ] Si hay decision arquitectonica, lleva su ADR en `docs/adr/`
- [ ] Si cambia el esquema de Room, lleva migracion **y** test de migracion
- [ ] Si cambia el formato del backup, se sube `formatVersion` y hay test de
      compatibilidad hacia atras
- [ ] Los textos visibles al usuario estan en `strings.xml`
- [ ] Nada de dinero representado con `Double` (ver `docs/adr/0002`)
