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

## Restauración

Es la operación más peligrosa de la app. El flujo:

1. Seleccionar archivo (`ACTION_OPEN_DOCUMENT`).
2. Pedir la frase, descifrar **en memoria**. Nunca escribir el claro en disco.
3. Validar checksum y `formatVersion`.
   - ¿Backup más nuevo que la app? → "Actualiza la app para restaurar esta copia." Abortar.
   - ¿Backup más viejo? → aplicar las migraciones de formato en cadena.
4. Mostrar un **resumen previo**: "Se restaurarán 4 cuentas, 312 transacciones,
   6 meses de planes, del 2026-03-15."
5. Elegir estrategia:
   - **Reemplazar todo** (caso "celular nuevo") — el de tu escenario.
   - **Fusionar** (v0.3, requiere IDs estables y resolución de conflictos).
6. **Backup de seguridad automático del estado actual antes de escribir nada.**
7. Importar en **una sola transacción de Room**. Si algo falla, rollback total.
   Nunca un estado a medias.

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
