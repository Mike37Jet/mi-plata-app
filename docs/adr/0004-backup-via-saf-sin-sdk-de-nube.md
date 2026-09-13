# 0004 — El backup usa Storage Access Framework, no los SDK de Drive/OneDrive

- **Estado:** Aceptado
- **Fecha:** 2026-09-13

## Contexto

El usuario quiere sacar copias de seguridad periódicas, guardarlas en Drive u
OneDrive, y restaurarlas en un celular nuevo, sin pagar servicios.

## Decisión

La app genera un archivo cifrado y usa `ACTION_CREATE_DOCUMENT` /
`ACTION_OPEN_DOCUMENT` del sistema. El usuario elige el destino. La app no
integra ningún SDK de nube y **no declara el permiso de INTERNET**.

## Alternativas consideradas

| Opción | A favor | En contra | Veredicto |
|---|---|---|---|
| SDK de Google Drive | Subida automática | OAuth, consent screen, verificación de Google que tarda semanas, scope sensible revisado por Play, atado a un solo proveedor | Rechazado |
| SDK de Microsoft Graph | Igual para OneDrive | Mismos costos, y habría que mantener dos integraciones | Rechazado |
| **SAF** | Cero red, cero OAuth, cero permisos peligrosos, funciona con cualquier proveedor y con USB | La subida automática depende de que el usuario elija una carpeta sincronizada | **Aceptado** |

## Consecuencias

- La app puede afirmar de forma verificable que los datos no salen del
  dispositivo salvo cuando el usuario exporta a propósito.
- El backup automático real se logra concediendo una URI de carpeta persistente
  con `ACTION_OPEN_DOCUMENT_TREE`; si esa carpeta está sincronizada, la app de la
  nube hace la subida.
- Hay que desactivar `android:allowBackup`, porque el backup automático de
  Android subiría la base cifrada sin la clave del Keystore, que no es
  exportable → restauración corrupta silenciosa.
