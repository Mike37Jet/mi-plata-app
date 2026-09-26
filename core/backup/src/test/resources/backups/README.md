# Archivos de referencia del backup

Copias de seguridad reales, una por cada versión del formato, generadas con la
app de su momento. Las usa `ArchivosDeReferenciaTest`.

**No se regeneran nunca.** Cada archivo representa lo que un usuario tiene
guardado en su Drive. Si un test deja de poder leerlo, el problema está en el
código, no en el archivo.

| Archivo          | Formato | Frase                            | Contenido                                     |
|------------------|---------|----------------------------------|-----------------------------------------------|
| `formato-1.mpb`  | 1       | `caballo correcto bateria grapa` | Está escrito en `DATOS_DEL_FORMATO_1` del test |

## Al subir `VERSION_DEL_FORMATO`

1. Escribe la migración `N-1 → N` en `MIGRACIONES_DE_FORMATO`.
2. Con la app nueva, genera una copia que use los campos nuevos y guárdala aquí
   como `formato-N.mpb`. Usa la misma frase.
3. Añade a `ArchivosDeReferenciaTest` un test que la abra y compruebe su
   contenido exacto.
4. No toques los archivos anteriores. Sus tests deben seguir pasando, porque
   ahora comprueban que la migración funciona.

Si falta alguno de estos pasos, un test guardián hace fallar el build.
