# 05 — Copia de seguridad y restauración

Requisito del usuario: *"cada cierto tiempo sacar una copia de seguridad, subirla
a Drive o OneDrive, y al cambiar de celular importarla — sin servicios pagados."*

## Decisión: Storage Access Framework (SAF), no las APIs de Drive/OneDrive

La app **no** integra el SDK de Google Drive ni el de OneDrive. En su lugar
escribe un archivo y deja que el usuario elija dónde, usando el selector del
sistema (`ACTION_CREATE_DOCUMENT`).

**Por qué esto es mejor, y no una salida fácil:**

| | SDK de Drive/OneDrive | **SAF** ← elegida |
|---|---|---|
| OAuth, consent screen, verificación de Google | Sí, y la verificación tarda semanas | No |
| Permisos sensibles que Play revisa | Sí (`drive.file` / `drive.appdata`) | No |
| Funciona con Dropbox, Mega, pCloud, USB, WhatsApp | No | **Sí, todos** |
| Código de red, tokens, refresh, manejo de expiración | Sí | **Cero** |
| Costo | Gratis pero con cuotas de API | Gratis |
| Superficie de ataque | Tokens OAuth en el dispositivo | Ninguna |

Con SAF, Drive y OneDrive aparecen en el selector como cualquier otro proveedor
porque sus apps se registran como `DocumentsProvider`. El usuario obtiene
exactamente lo que pidió, la app no toca la red **ni una sola vez**, y no
necesita ningún permiso peligroso.

> Consecuencia: `mi-plata-app` puede declarar que **no requiere permiso de
> INTERNET**. Es un argumento de privacidad muy fuerte para una app de finanzas,
> y verificable por cualquiera en el manifest.

## Formato del archivo

`miplata-backup-2026-03-15-1042.mpb` — un ZIP con:

```
manifest.json      en claro: versiones, fecha, dispositivo, recuento, checksum
                   y los parámetros de cifrado (salt, IV, iteraciones)
data.enc           las entidades serializadas con kotlinx.serialization,
                   cifradas con AES-256-GCM
attachments/       (futuro) fotos de recibos
```

Decisiones:

- **JSON, no un dump binario de SQLite.** El dump es más fácil de generar pero
  ata el backup al esquema exacto de Room; restaurar en una versión distinta se
  vuelve imposible. JSON permite migrar el backup al importarlo.
- **`formatVersion` separado de `schemaVersion`.** El formato del backup es un
  contrato público con el usuario y evoluciona más lento que la base de datos.
- **Checksum SHA-256 del contenido *cifrado*** en el manifest → detectar
  corrupción antes de tocar nada, y **sin necesitar la frase**. Sobre el cifrado y
  no sobre el claro: así un archivo dañado se distingue de una frase mal escrita,
  y no queda al lado del cifrado un hash del claro que permitiría confirmar un
  contenido adivinado.
- Un `.zip` renombrado, para que el usuario no lo abra por accidente ni una app
  de mensajería lo recomprima.

## Cifrado

El backup contiene todas las finanzas del usuario y va a terminar en una nube de
terceros. **Se cifra siempre**, no es opcional.

- El usuario define una **frase de respaldo** al configurar el primer backup.
  Mínimo **12 caracteres**; la interfaz recomienda tres o cuatro palabras.
- Derivación de clave: **PBKDF2-HMAC-SHA256 con 600.000 iteraciones** (la cifra
  de OWASP). Se eligió sobre Argon2id porque viene en la biblioteca estándar de
  Java: `:core:backup` sigue siendo Kotlin puro y se prueba en la JVM sin
  dependencias nativas. Las iteraciones **se guardan en el archivo**, así que se
  pueden subir en el futuro sin dejar de abrir los backups antiguos.
- Salt (16 bytes) e IV (12 bytes) **aleatorios en cada backup**. Dos copias con la
  misma frase dan claves distintas: nunca se reutiliza un IV con la misma clave.
- Cifrado: **AES-256-GCM** (autenticado: detecta manipulación, no solo corrupción).
- Los parámetros y la versión del formato se autentican como **datos asociados**
  de GCM: van en claro, pero cambiarlos impide abrir el archivo.
- Un tope de iteraciones al leer evita que un archivo malicioso cuelgue la app
  derivando una clave durante minutos.
- La frase **no se guarda en ningún lado**. Si se pierde, el backup se pierde. La
  UI debe decirlo con todas sus letras, al menos dos veces, antes de continuar.
- El contenido en claro vive solo en memoria y se sobrescribe al terminar. La
  frase se guarda como `CharArray` para poder borrarla. La JVM no garantiza que
  el borrado alcance todas las copias, pero no dejarla entera en un `String`
  inmutable es lo mínimo razonable.

### Qué dice la app cuando algo falla

El orden de las comprobaciones al leer es lo que hace útiles los mensajes:

| Comprobación | Si falla | ¿Necesita la frase? |
|---|---|---|
| ¿Hay manifiesto? | "Esto no parece un backup de mi-plata" | No |
| ¿Versión legible? | "Actualiza la app para restaurarlo" | No |
| ¿Checksum del cifrado? | "El backup está dañado. No se ha modificado nada." | No |
| ¿Descifra? | **Frase incorrecta** → se vuelve a pedir | Sí |

GCM no distingue "frase equivocada" de "datos manipulados". Pero como el checksum
se comprueba antes, si se llega al último paso el archivo está entero y lo casi
seguro es la frase. Es un tipo de error propio (`FraseIncorrecta`) porque la
interfaz reacciona distinto: ante un archivo dañado aborta, ante una frase mal
tecleada la vuelve a pedir.

## Exportación

Se entra desde **Cuentas → Copia de seguridad**. Cuentas no conoce el feature de
la copia (docs/04): solo avisa de que se pidió, y `:app` decide a dónde se va.

1. El usuario escribe la frase **dos veces** y marca *"Entiendo que si pierdo la
   frase, pierdo la copia"*. El aviso en rojo y la casilla son las dos veces que
   exige este documento.
2. `ACTION_CREATE_DOCUMENT` con el nombre `miplata-backup-AAAA-MM-DD-HHMM.mpb`.
   Año-mes-día para que ordenar por nombre sea ordenar por fecha.
3. Se reúne, se cifra y se escribe fuera del hilo principal (`Dispatchers.IO`:
   derivar la clave tiene un hilo bloqueado un par de segundos).
4. Solo si salió bien se anota la fecha en `Ajustes.ultimoBackupEnMillis`.
   Anotarla antes haría que un fallo dejara al usuario creyendo que tiene una
   copia reciente que no existe.

Detalles que no se ven y que importan:

- **Modo `"wt"` al abrir el documento, no `"w"`.** Al sobrescribir una copia
  antigua más grande, con `"w"` algunos proveedores escriben encima sin vaciar
  el archivo: el final de la copia vieja queda detrás de la nueva, el ZIP sale
  roto, y nadie se entera hasta el día en que hace falta restaurar.
- **La frase nunca pasa por `rememberSaveable` ni por el estado del ViewModel.**
  Eso se escribe en disco cuando el sistema mata la app en segundo plano. Vive
  en un `remember` de la pantalla y se entrega al ViewModel al exportar, que la
  borra en cuanto la ha copiado.
- **Teclado de tipo contraseña y sin autocorrección**: un teclado normal
  aprendería la frase y la guardaría en su diccionario de sugerencias.

## Restauración

Es la operación más peligrosa de la app. Se entra desde **Cuentas → Copia de
seguridad → Restaurar una copia**. El flujo:

1. Seleccionar archivo (`ACTION_OPEN_DOCUMENT`, cualquier tipo: Drive guarda el
   `.mpb` como "binario desconocido" y un filtro por MIME lo escondería).
2. Leer **solo el manifiesto**, sin pedir la frase. Si el formato es más nuevo
   que la app: "actualiza la app", y se para aquí.
3. **Resumen previo**, comparando lo que trae la copia con lo que hay ahora:
   *"Trae 1 cuenta · 312 movimientos… / Ahora tienes 5 cuentas · 900
   movimientos…"*. Es lo que evita el error más fácil: restaurar una copia vieja
   por equivocación.
4. Pedir la frase y descifrar **en memoria**. Frase incorrecta → se vuelve a
   pedir, sin tener que elegir el archivo otra vez.
5. **Confirmación explícita** ("¿Sustituir todos tus datos?").
6. **Copia de seguridad automática de lo que hay ahora.** Si no se puede
   guardar, se para: no se toca nada sin poder deshacerlo.
7. Sustituir los datos en **una sola transacción de Room**. Si algo falla,
   SQLite deshace la transacción entera. Nunca un estado a medias.
8. Los ajustes, aparte (viven en DataStore, no pueden entrar en la
   transacción). Si fallan, los datos ya entraron: se avisa y se puede deshacer.

Estrategia: solo **reemplazar todo** (el caso "celular nuevo"). **Fusionar**
queda para v0.3: requiere resolver conflictos.

### La transacción

`RoomRepositorioDeRestauracion` borra y vuelve a insertar dentro de
`withTransaction`. Detalles que no se ven:

- **Se borra de verdad**, no se marca como eliminado: un registro marcado que
  sobreviviera podría chocar con uno de la copia con el mismo id.
- **`@Insert` con `ABORT`, no `@Upsert`**: un id repetido dentro de la copia
  tiene que hacer fallar la restauración, no sobrescribirse en silencio.
- Las claves foráneas trabajan a favor: una copia incoherente (un movimiento
  que apunta a una cuenta que no trae) no entra, en vez de entrar a medias.
- **Las subcategorías se borran antes que las categorías raíz.** Su clave
  foránea es `RESTRICT`, y SQLite la comprueba fila a fila en el momento (ni
  siquiera se puede aplazar): un `DELETE FROM categorias` a secas falla en
  cuanto borra una madre antes que alguna de sus hijas. Y al insertar, las raíz
  van primero.

### La copia previa y volver atrás

- Vive en `filesDir` (privado, no sale del teléfono). Es para deshacer aquí,
  no para llevarla a otro móvil.
- Va cifrada con una frase **derivada** de la clave de la base
  (HMAC-SHA256 con una etiqueta propia): igual de protegida que la base, sin un
  secreto nuevo que guardar, y sin reutilizar la clave tal cual para dos usos.
- Se escribe de forma atómica (`AtomicFile`): si la app muere a mitad, queda la
  anterior.
- **Volver atrás también guarda antes lo que hay.** Sin esto, quien restaura,
  apunta una semana de gastos y vuelve atrás, perdería esa semana sin remedio.
  Va en dos fases: lo de ahora se escribe aparte, se sustituyen los datos, y
  solo si sale bien lo escrito aparte pasa a ser la copia previa. Como volver
  atrás es reversible, no pide confirmación.
- El botón sigue disponible al volver a entrar: se puede deshacer aunque la
  restauración fuera hace una semana.

### Un contrato que importa: `escribir` no cierra el stream

`ArchivoDeBackup.escribir` termina el ZIP con `finish()` pero no cierra el
stream que recibe. Quien abre un stream es quien lo cierra, y quien llama
necesita hacer `fsync` antes de dar la copia por buena. Cerrándolo, guardar la
copia previa fallaba con *"sync failed"*, y `AtomicFile` se tragaba el mismo
error: la copia se daba por guardada sin garantía de haber llegado al disco.

## Automatización del recordatorio

- `WorkManager` con `PeriodicWorkRequest` (semanal o mensual, configurable).
- Como la app no puede escribir sola en una URI de SAF cuya permanencia no está
  garantizada, el comportamiento por defecto es **notificar**: *"Tu último
  backup fue hace 34 días."*
- Mejora posible: si el usuario concede una URI de carpeta persistente
  (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`), el worker
  **sí** puede escribir el archivo automáticamente ahí, y si esa carpeta está
  sincronizada con Drive/OneDrive, la subida la hace la app de la nube. Backup
  automático real, sin una línea de código de red.

## Lo que hay que desactivar

`android:allowBackup="false"` y `dataExtractionRules` restrictivas en el
manifest. El backup automático de Android subiría la base cifrada a Google sin
la clave del Keystore (que no es exportable) → restauración corrupta silenciosa.
El backup de la app es explícito o no es.

## Tests obligatorios

- Round-trip: exportar → importar → el estado es **idéntico** (comparación de dominio).
- Frase incorrecta → error claro, base de datos **intacta**.
- Archivo truncado / bit cambiado → GCM lo detecta, se aborta.
- Backup de `formatVersion` N−1 → migra y restaura bien. Un test por versión,
  con un archivo de ejemplo commiteado en `src/test/resources/backups/`.
