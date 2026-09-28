# Currency Converter

Android app for viewing and converting exchange rates. Offline-first: the last downloaded rates are always
shown, fresh ones are loaded in the background.

* Kotlin 2.4, Jetpack Compose, Material 3, MVVM, Coroutines/Flow
* Hilt, Room, DataStore, WorkManager, Retrofit + OkHttp, kotlinx.serialization
* `minSdk 26`, `compileSdk`/`targetSdk 37`, Gradle 9.7 + AGP 9.4, JDK 17+
* UI languages: English, Russian

## Build and run

1. Open the `CurrencyConverter` folder in a recent Android Studio (Otter or newer) and let Gradle sync,
   or use the command line:

   ```bash
   ./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
   ./gradlew :app:testDebugUnitTest    # 187 unit, ViewModel, Compose UI and end-to-end tests (JVM, no device needed)
   ./gradlew :app:lintDebug
   ```

2. Android Studio creates `local.properties` with `sdk.dir` automatically. On the command line create it yourself,
   see `local.properties.example`.

## Rates provider and API key

The default provider is [ExchangeRate-API](https://www.exchangerate-api.com) (`open.er-api.com`), free and
**needs no API key**. It quotes ~160 currencies including RUB; the ECB-sourced Frankfurter API this project
used earlier does not publish RUB (ECB stopped after 2022), so it could not cover this catalog. Rates update
about once a day.

Everything provider-related lives in `local.properties` (git-ignored) and is compiled into `BuildConfig`:

```properties
rates.baseUrl=https://open.er-api.com/
rates.apiKey=                 # leave empty for ExchangeRate-API
rates.apiKeyHeader=Authorization
```

If you switch to a provider that needs a key, put it in `rates.apiKey`: `ApiKeyInterceptor` then sends it in the header
named by `rates.apiKeyHeader`. Keys never appear in source files. Note that any secret shipped inside an APK can
be extracted by a determined user; for a valuable key put a small proxy server in front of the provider.

To replace the provider completely implement `RatesRemoteDataSource` (one method) and change the single
`@Binds` in `di/NetworkModule.kt` (`RemoteModule`). Nothing else in the app knows which service is used.

## Architecture

```
presentation  (Compose screens, ViewModels, UI state)
      |
   domain     (models, use cases, repository interfaces, BigDecimal math, formatting)
      |
    data      (Room, Retrofit, DataStore, WorkManager, repository implementations)
```

* Room is the single source of truth. The UI only observes `Flow`s from the database; a refresh downloads a
  snapshot and atomically replaces the stored rows, which pushes the update to every observer.
* One snapshot (all currencies against EUR) is stored. Any pair is derived by triangulation
  (`ExchangeRates.rate`), so adding a currency never requires new requests or tables.
* All money math uses `BigDecimal` (34 significant digits). Rates are parsed from the JSON text, never through
  `Double`, and rounding happens only when text is produced (`AmountFormatter`).
* Requests are protected: cached data younger than the chosen update interval is not refreshed, requests that reached
  the server are rate-limited to one per 15 s, concurrent refreshes are serialised, and there is no polling. WorkManager
  refreshes periodically (interval from Settings, network required).

## Currency catalog

`CurrencyCatalog.ALL` (`domain/currency/CurrencyCatalog.kt`) lists 100 currencies, ordered roughly from the
largest to the smallest economy (this is also the picker's default order). It covers the G7, every BRICS+
member (including the Russian ruble), every ASEAN member, and the rest of the world's largest economies.

To add a currency, add one line:

```kotlin
Currency("XYZ", "XY", "symbol", fractionDigits = 2),   // ISO code, country for the flag emoji, optional symbol/digits
```

The display name is localised by the JDK, the flag is an emoji built from the country code (no images, no
network). The provider must quote the currency — ExchangeRate-API supports ~160 of them.

## Tests

`app/src/test` runs on the JVM (Robolectric where Android classes are needed):

| Area | Tests |
| --- | --- |
| Conversion, inverse rate, triangulation | `ExchangeRatesTest`, `ConvertCurrencyUseCaseTest` |
| Formatting and input parsing | `AmountFormatterTest`, `AmountInputTest` |
| Repository, cache (real in-memory Room) | `RatesRepositoryImplTest`, `FavoritesRepositoryImplTest` |
| API errors (HTTP, timeout, broken JSON, ...) | `ExchangeRateApiRemoteDataSourceTest` (MockWebServer) |
| ViewModels | `HomeViewModelTest`, `FavoritesAndSettingsViewModelTest` |
| Compose UI, accessibility (48 dp targets, 2x font, dark theme) | `HomeScreenTest`, `FavoritesPickerSettingsTest` |
| Whole app: Hilt graph + Room + navigation | `AppFlowTest` |

Robolectric on JDK 21+ needs the `--add-exports/--add-opens` flags that are already set in `app/build.gradle.kts`.
`robolectric.properties` pins the emulated SDK to 36 because Robolectric does not support 37 yet.

## Release build

`assembleRelease` produces an R8-minified, **unsigned** APK. Configure your own signing config before publishing.
