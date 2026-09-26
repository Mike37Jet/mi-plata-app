# 09 — Accesibilidad

Dos compromisos (roadmap 7.1):

1. **Nada se rompe al subir el tamaño de letra del sistema**, hasta el 200%
   que permite Android 14.
2. **Contraste suficiente en los dos temas.** Todo texto llega al menos a
   **4.5:1**, el mínimo WCAG AA para texto normal.

Los dos se comprueban con tests que corren en cada PR, no a ojo.

## Letra grande

### Las reglas

- **Todo el texto va en `sp`.** Así la letra crece con el ajuste del usuario.
- **Nada de anchos fijos alrededor de un texto.** Si hace falta alinear, se usa
  un mínimo (`widthIn(min = …)`), nunca un ancho exacto.
- **Con letra grande, lo que iba en fila se apila.** `conLetraGrande()`, en
  `:core:designsystem`, dice cuándo: a partir de una escala de 1.5. Lo usan las
  filas del plan, la comparativa del resumen y los editores de cuenta y de
  movimiento. La respuesta a la letra grande es darle sitio al texto, no
  encogerlo.
- **La única excepción es un sitio fijo que no se puede apilar**, como las
  pestañas de la barra de navegación. Ahí va `TextoQueCabe`: una línea que se
  reduce lo justo para caber. Partir "Movimi / entos" es peor que reducir la
  letra un poco. Nunca baja del tamaño que ese texto tiene a escala normal.

### Lo que se encontró al 200%

La revisión se hizo en un Pixel (1080×2424, Android 17), con la copia de
referencia del backup restaurada para tener datos reales.

| Pantalla | Qué pasaba | Arreglo |
|---|---|---|
| Plan | El importe de cada línea estaba en un campo de 130dp. De "2000.00" solo asomaba el primer dígito. | En letra grande, el nombre va en su propia fila y el importe usa el ancho que dejan los controles. |
| Barra de navegación | "Resumen" y "Movimientos" se partían a mitad de palabra. | `TextoQueCabe`. |
| Barra de estado | Con la app en oscuro y el móvil en claro, la hora y la batería salían negras sobre fondo negro. | Los iconos siguen el tema **de la app**, no el del sistema (`BarrasDelSistemaSegunElTema`). |
| Cabecera del mes | El mes tenía 120dp exactos. Cabía justo, sin margen. | `widthIn(min = 120.dp)`. |

Las pantallas de cuentas, movimientos, resumen, editores y copia de seguridad
ya se adaptaban bien.

### Cómo se prueba

Los tests de letra grande corren con Robolectric con **gráficos nativos**
(`@GraphicsMode(NATIVE)`) y `@Config(fontScale = 2.0f)` en un móvil de 360dp.
Los gráficos nativos importan: con los simulados, cada letra mide lo mismo y un
texto que no cabe es indistinguible de uno que sí.

- `LetraGrandeEnElPlanTest` compara lo que mide el texto del nombre y del
  importe con el sitio que tiene su campo. Con la fila antigua falla: el importe
  necesitaba 200px y tenía 98.
- `TextoQueCabeTest` comprueba que "Movimientos" cabe en una línea en el ancho
  de una pestaña. Comprueba también que un `Text` normal en ese sitio se parte y
  que una palabra que ya cabe no se encoge.

## Contraste

`ContrasteTest` calcula la relación de contraste WCAG de cada texto de la app
sobre cada fondo en el que puede aparecer, en los dos temas. Cubre:

- `onSurface`, `onSurfaceVariant`, `primary`, `error` y los cuatro colores de
  dinero sobre `surface`, `background` y los contenedores de las `Card`.
- Cada color `on*` sobre su contenedor.
- **Los colores de dinero sobre el color dinámico.** Las superficies dinámicas
  cambian con el fondo de pantalla, pero Material solo las saca de una franja
  de tonos fija: del 87 al 100 en claro y del 4 al 24 en oscuro. El test prueba
  el tono más desfavorable de cada franja, y con eso cubre cualquier fondo de
  pantalla. El resto de colores cambia junto con su fondo, y Material ya
  garantiza su contraste.

Hoy todo pasa. El par más justo es `onPrimaryContainer` sobre
`primaryContainer` en oscuro, con 4.56. Saboteado con un rojo de sobregiro más
claro (`#E57373`), el test falla con el par exacto:
`dinero.sobregiro sobre surface: 2,92`.

Dos tests comprueban la propia fórmula contra valores de referencia de WCAG.
Así, un error en la fórmula no puede dar todo por bueno.

## Lo que no cubre

- **TalkBack** tiene su cuidado desde la Etapa 3: descripciones en los iconos
  que no tienen texto al lado, y en los interruptores del plan. Pero no hay un
  test que recorra la app con él.
- **Texto deshabilitado**, como el botón "Guardar" antes de poder guardar.
  WCAG lo excluye a propósito: su bajo contraste es lo que dice que no se puede
  pulsar.
