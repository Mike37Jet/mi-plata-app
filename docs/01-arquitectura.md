# 01 — Arquitectura

## Decisión: Clean Architecture (3 capas) + MVVM con flujo de datos unidireccional

No es "Clean Code o MVVM": son cosas distintas y se usan juntas.

- **Clean Architecture** → cómo se organizan las **dependencias** entre capas.
- **MVVM + UDF** → cómo se organiza la **capa de presentación**.
- **Clean Code / SOLID** → cómo se escribe cada archivo. Transversal, no es una arquitectura.

## Las tres capas

```
┌──────────────────────────────────────────────────────────┐
│  PRESENTATION   Compose UI · ViewModel · UiState · Event  │
│                 Conoce: domain                            │
├──────────────────────────────────────────────────────────┤
│  DOMAIN         Modelos · UseCases · Interfaces de repo    │
│                 Conoce: NADA. Kotlin puro, sin Android.    │
├──────────────────────────────────────────────────────────┤
│  DATA           RepositoryImpl · Room DAO · DataStore ·    │
│                 Mappers                                    │
│                 Conoce: domain (implementa sus interfaces) │
└──────────────────────────────────────────────────────────┘
```

**Regla de dependencia (la única regla innegociable):** las flechas apuntan
siempre hacia adentro. `domain` no importa `androidx.*`, no importa Room, no
importa Compose. Si `domain` compila como módulo Kotlin/JVM puro, la
arquitectura está sana. Si deja de compilar, alguien rompió la regla.

Esto se hace cumplir por el **sistema de build**, no por disciplina: el módulo
`:core:domain` es un `kotlin("jvm")`, no un `com.android.library`. Es
físicamente imposible meterle un `Context`.

### Qué va en cada capa

**Domain** — el corazón, y lo único que realmente es "tuyo".
- Modelos: `Money`, `Cuenta`, `Transaccion`, `PlanMensual`, `LineaDePlan`…
- `UseCase`s: una clase, una operación, un `operator fun invoke()`.
  Ej. `CalcularResumenMensualUseCase`, `RegistrarTransaccionUseCase`.
- Interfaces de repositorio: `interface TransaccionRepository`.
- Errores de dominio como tipos, no excepciones: `sealed interface DomainError`.

**Data** — detalles reemplazables.
- `RoomTransaccionRepository : TransaccionRepository`
- Entities de Room, **separadas** de los modelos de dominio, con mappers explícitos.
  Sí, duplica algunas clases. Ese es el precio de poder cambiar Room sin tocar
  reglas de negocio, y de que un cambio de esquema no se filtre a la UI.
- Fuente de verdad única: Room. Todo se expone como `Flow<T>` → la UI reacciona sola.

**Presentation**
- Un `ViewModel` por pantalla, expone **un solo** `StateFlow<XxxUiState>`.
- La UI manda `Events` hacia arriba; nunca llama repositorios directamente.
- Los `Composable` son funciones puras de `UiState` → no conocen ViewModel
  (el ViewModel se inyecta solo en el composable "route" de nivel superior).

## Flujo de datos unidireccional (UDF)

```
   Usuario
      │ Event (onGastoEditado)
      ▼
  ViewModel ──▶ UseCase ──▶ Repository ──▶ Room
      ▲                                      │
      └────────── StateFlow<UiState> ◀────── Flow
```

Un solo sentido. Sin `LiveData` mutable expuesto, sin estado duplicado entre
ViewModel y UI, sin callbacks cruzados.

## Modelo de UiState

```kotlin
// Forma preferida: un solo tipo, siempre renderizable.
data class ResumenUiState(
    val cargando: Boolean = true,
    val mes: YearMonth,
    val ingresos: Money = Money.ZERO,
    val gastosFijos: Money = Money.ZERO,
    val disponible: Money = Money.ZERO,
    val sobregiro: Money? = null,
    val error: MensajeUi? = null,
)
```

Se prefiere `data class` con flags sobre `sealed class Loading/Success/Error`
cuando la pantalla muestra datos parciales mientras carga (que es casi siempre
en esta app). El `sealed interface` se reserva para pantallas donde los estados
son **mutuamente excluyentes de verdad**.

## Dónde vive el cálculo de saldos y sobregiros

En `domain`, en use cases puros, **no** en SQL y **no** en el ViewModel.

Motivo: es la lógica más valiosa y la que más va a cambiar. En Kotlin puro se
testea en milisegundos sin emulador, sin Room, sin Robolectric. Un test de
`CalcularResumenMensualUseCase` con 40 casos borde corre en menos de un segundo.

Room hace lo que Room hace bien: traer filas y emitir cambios.

## Manejo de errores

`domain` no lanza excepciones para casos esperados. Devuelve un tipo:

```kotlin
sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>
    data class Error(val causa: DomainError) : Resultado<Nothing>
}
```

Las excepciones se reservan para bugs de programación (fallar rápido y ruidoso).
Un backup corrupto, un monto negativo o un mes inexistente son `DomainError`,
no `Exception`.

## Concurrencia

- Coroutines + Flow en todas partes. Nada de RxJava, nada de callbacks.
- Los `Dispatchers` se **inyectan** (`@IoDispatcher`), nunca se hardcodean.
  Sin esto, los tests no son deterministas.
- Room ya conmuta a IO internamente; no envolver DAOs en `withContext(IO)`.
- `stateIn(viewModelScope, WhileSubscribed(5_000), inicial)` como patrón estándar
  en ViewModels — sobrevive a rotación sin recargar.

## Qué NO se va a hacer (anti-overengineering)

Esta app es para una persona. Se rechaza explícitamente:

- **Un módulo por capa por feature** desde el día 1 (30 módulos, builds lentos,
  cero beneficio). Ver `04-modularizacion.md` para el plan gradual.
- **UseCases que solo reenvían al repositorio.** Si `GetCuentasUseCase` es una
  línea que llama `repo.getCuentas()`, el ViewModel llama al repo y ya. El
  UseCase se introduce cuando **hay lógica** o cuando compone varios repos.
- **MVI con reducers y middleware.** MVVM + UDF cubre el 100% de este caso.
- **Interfaces con una sola implementación** fuera del borde domain↔data.
