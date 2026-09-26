# 0006 — La bienvenida se deduce de los datos, no de una marca guardada

- **Estado:** Aceptado
- **Fecha:** 2026-09-26

## Contexto

La primera vez que se abre la app hay que guiar al usuario (roadmap 7.2). La
alternativa es una pantalla vacía. Hay que decidir cómo sabe la app que es la
primera vez.

Pesan tres casos:

- **Móvil nuevo.** Quien ya usaba la app restaura su copia desde la bienvenida.
  Después debe entrar con sus datos, sin volver a pasar por ella.
- **La app muere a mitad.** El sistema la cierra mientras se guardan los
  primeros pasos.
- **El usuario borra todas sus cuentas.**

## Decisión

Hay que dar la bienvenida **mientras no exista ninguna cuenta**
(`HayQueDarLaBienvenidaUseCase`). La app lo observa y cambia sola entre la
bienvenida y las pestañas.

La cuenta es el criterio porque es lo mínimo para usar la app: cada movimiento
necesita una. Las categorías no sirven, porque se siembran solas al arrancar.
Los planes tampoco: se puede planear sin haber creado ninguna cuenta.

`CompletarPrimerosPasosUseCase` guarda en este orden: ajustes → cuenta → plan.
**Cada estado intermedio es un estado válido de la app**, así que no hace falta
una transacción:

| Si la app muere después de… | Queda | Y al volver |
|---|---|---|
| los ajustes | moneda elegida, ninguna cuenta | la bienvenida sale otra vez y los vuelve a pedir |
| la cuenta | una cuenta, ningún plan | la app normal: el ingreso se añade en Plan, donde se aterriza |

El orden inverso, plan antes que cuenta, no es válido: la línea de ingreso
apuntaría a una cuenta que no existe, y la base la rechaza por su clave foránea.
Se descubrió así, en el emulador. Los repositorios en memoria de los tests no
comprueban claves foráneas. Ahora un test exige que, al guardar el plan, su
cuenta ya exista.

## Alternativas consideradas

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| Marca "bienvenida vista" en los ajustes | Explícita; no depende de qué datos haya | Hay que ponerla también al restaurar, y viajaría con el backup. Si se pone demasiado pronto, deja al usuario en una app sin cuentas | Rechazada |
| **Derivada: ninguna cuenta** | Restaurar saca de la bienvenida sin código extra. Un fallo a mitad se recupera solo | Quien borra todas sus cuentas vuelve a verla | **Aceptada** |
| Guardar todo en una transacción | Todo o nada | Pide un contrato transaccional nuevo en los repositorios para evitar estados que ya son válidos | Rechazada |

## Consecuencias

- Quien borra todas sus cuentas vuelve a la bienvenida. Es razonable: sin
  cuentas no puede anotar nada. Los primeros pasos **no pisan** el plan del mes
  si ya existe.
- La pantalla de bienvenida desaparece **mientras** se guarda: en cuanto existe
  la cuenta, la app cambia sola. Por eso:
  - el guardado corre en `NonCancellable`, o cancelar el ViewModel cortaría el
    plan, que va después;
  - `:app` recibe el aviso de terminar **antes** de guardar, para saber que debe
    abrir el Plan.
- La splash screen se mantiene hasta saber por dónde empezar. Así no se ve un
  parpadeo entre pantallas vacías y la bienvenida.
