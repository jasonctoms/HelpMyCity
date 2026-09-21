# Architecture

How HelpMyCity is put together, and why. This is the canonical technical record:
the constraints below were paid for once, and are written down so they do not
have to be rediscovered.

For what the app does, see the [README](./README.md). For setting up your own
city, see [CITY_CONFIG.md](./CITY_CONFIG.md).

**Contents** — [Design rules](#design-rules) · [Repository layout](#repository-layout) ·
[The two seams](#the-two-seams) · [Object graph](#object-graph) ·
[Data flow](#data-flow) · [Data model](#data-model) ·
[Access control, accounts and sessions](#access-control-accounts-and-sessions) ·
[Storage](#storage) · [Backend contract](#backend-contract) ·
[UI and navigation](#ui-and-navigation) · [Maps](#maps) ·
[Why the web target is JS](#why-the-web-target-is-js) ·
[Localization and accessibility](#localization-and-accessibility) ·
[Tests](#tests) · [Toolchain notes](#toolchain-notes) · [Known gaps](#known-gaps)

## Design rules

Seven choices drive everything else.

1. **Common-first Compose Multiplatform**, on Android, iOS and the web
   (Kotlin/JS — see [Why the web target is JS](#why-the-web-target-is-js)).
   Prefer common code to platform code wherever it is possible. Everything in
   the package map below is `commonMain`, including the Room schema, DAOs,
   mappers and `RoomLocalStore`. Each platform source set holds one thing: a
   `getDatabaseBuilder` (per the
   [AndroidX Room KMP guide](https://developer.android.com/kotlin/multiplatform/room))
   and a single Koin binding for `LocalStore`. Android gets its `Context` from
   Koin's `androidContext()`, so the shared module never reaches for a global.
2. **Backend-agnostic.** Nothing above `data/remote` knows what the backend is;
   it sits behind small API interfaces so an implementation can be dropped in or
   replaced. Choosing one is one argument at the entry point.
3. **City-agnostic.** `:shared` compiles and runs with no city configured.
   Nothing in the core may name a city, a department, a neighborhood or a
   municipal website; all of it lives behind `CityProfile` in a module the core
   does not depend on. Oceanside and its Supabase project live in `:cityConfig`,
   and a fork edits or replaces that module and nothing else.
4. **Offline-first.** Room 3 is the local SQLite store on every target. Reads
   always come from it; writes land locally as `PENDING_UPLOAD` and the sync
   engine drains the queue when changes appear. With no backend the queue simply
   builds — nothing is dropped.
5. **Portable data.** Everything must be exportable in a portable format, so
   switching providers stays possible. Enums persist as stable string keys, never
   ordinals, and `IssueCsvExporter` dumps the whole table without a backend —
   an admin runs it from the profile screen.
6. **Accessible and bilingual.** Modern accessibility practice throughout, and
   every user-visible string is a localizable Compose resource (English and
   Spanish today).
7. **Maps** use `org.maplibre.compose:maplibre-compose`, with
   `dev.jordond.compass` for geocoding. Which geocoder is a deployment's choice
   behind `CityProfile.geocoder`, defaulting to off.
   Compass covers Android and iOS with the device's own geocoder — free, keyless
   and unmetered — but has no web backend that works without an account, so
   `:shared` adds `NominatimPlatformGeocoder` for the browser, on OpenStreetMap
   data like the basemap.

## Repository layout

```
shared/            the forkable core. Knows nothing about any city or any cloud.
cityConfig/        Oceanside, CA + its Supabase project. Replace this with yours.
androidApp/        \
iosApp/             > composition roots. One call to initKoin() each; this is
webApp/            /  where a build picks its city and its backend.
sqliteWasmWorker/  the SQLite WASM / OPFS web worker that gives Room a driver
                   in the browser.
supabase/          SQL and an Edge Function for the worked backend example.
```

Each platform app module is the composition root, including `:iosApp` — a Gradle
module living beside the Xcode project, which is what Xcode builds the `Shared`
framework from. `:shared` declares no framework binary of its own, because the
entry point is where the city and backend get chosen and `:shared` cannot make
that choice.

Inside the core:

```
shared/src/commonMain/kotlin/dev/helpmycity/
  domain/          models, repository interfaces, id/time providers -- no framework types
  domain/access/   who may see and who may review an issue, as pure functions
  deployment/      CityProfile + BackendProvider: the two interfaces a fork implements
  data/
    local/         LocalStore seam, data source interfaces, in-memory fake (tests)
    local/database Room 3 entities, DAOs, database, mappers, RoomLocalStore
    remote/        backend interfaces, no-op default, external/ city hand-off seam,
                   geocoding/ (Compass wiring + Nominatim for the web)
    repository/    offline-first repository implementations
    sync/          outbox-drain sync engine
    auth/          AuthService + mock: accounts, and nothing about permissions
    session/       UserSession: joins the signed-in account to its User record
    seed/          writes the CityProfile's reference data at startup
    export/        CSV export (data portability)
  di/              Koin annotations module, dataModule, expect platformModule()
  ui/              theme, navigation, components, screens, view models
shared/src/androidMain/  getDatabaseBuilder(Context) + Room binding + device geocoder
shared/src/iosMain/      getDatabaseBuilder() + Room binding + device geocoder
shared/src/jsMain/       getDatabaseBuilder() + Room binding (OPFS) + no device geocoder
```

## The two seams

All of the variability runs through two seams, and both work the same way — one
binding in common code reading from a holder, rather than a binding per variant:

- **`LocalStore`** (`data/local/LocalStore.kt`) for platform differences:
  `di/DataModule.kt` unpacks it into the four data sources once, in common code,
  and each platform binds exactly one implementation.
- **`Deployment`** (`deployment/Deployment.kt`) for fork differences: `initKoin`
  stores the chosen `CityProfile` and `BackendProvider` in the holder, and
  `di/AppModule.kt` binds everything they expose.

The holder indirection is not ceremony. The Koin compiler plugin validates the
object graph inside `:shared` at compile time, so every binding has to be
*declared* there, while the values have to come from `cityConfig/` — which
`:shared` must not depend on.

`:cityConfig` carries no Koin annotations and no Compose. It is plain Kotlin data
plus interface implementations, depending on nothing but `api(project(":shared"))`.
Keeping the Koin compiler plugin out of it avoids a second compilation emitting
hints for the same bindings, and keeps the cost of writing a new city's module
down to filling in data classes.

## Object graph

`di/KoinSetup.kt` holds the root. `initKoin(city, backend, configure)` runs once
per platform entry point — Android's `Application`, iOS's `MainViewController`,
the web's `main` — installing the two choices into `Deployment` and starting
Koin. It is idempotent, so Android activity recreation and hot reload do not
blow up.

- Repositories, view models, the sync engine and the seeder are annotated
  (`@Single`, `@KoinViewModel`) and picked up by `@ComponentScan`, so adding a
  class does not mean editing a module.
- `AppModule` hand-binds the fork seam: `CityProfile`, the external request
  gateway, the geocoder, the three backend APIs and `AuthService`, each read out
  of `Deployment`.
- `applicationScope` is a process-lifetime `CoroutineScope`, for the singletons
  that have to keep watching something (`DefaultUserSession`, `SyncEngine`).
- Both `initKoin` arguments default to something that runs with no
  configuration: `UnconfiguredCityProfile` and `LocalOnlyBackend`. A clone with
  the city module deleted still builds and runs.

## Data flow

One direction, with the local database in the middle:

```
screen ──> view model ──> repository ──┬──> LocalStore (Room)  ──> screen re-renders
                                       └──> row marked PENDING_UPLOAD
                                                     │
                                            SyncEngine drains the outbox
                                                     │
                                            BackendApi (push, then pull)
```

`DefaultSyncEngine` (`data/sync/`) is an outbox drain. On `start` it syncs once
— a fresh install has nothing queued, so without that first pull it would sit on
an empty list until its owner filed something — then watches the local store and
syncs whenever the pending count changes, debounced by 1.5 s so a manager
editing a form produces one sync rather than a burst. A run pushes pending
issues, pushes pending status changes, uploads pending photos, then pulls
issues and photos changed since the last successful sync. Photos come after
issues both ways: a backend can refuse a photo for an issue it has not seen,
and a pulled photo whose issue this device cannot read is skipped.

`RemoteResult` has three cases, and the third is the load-bearing one:
`Success`, `Failure(retryable)`, and `NotConfigured`. `NotConfigured` means "no
backend here", and the engine reports `SyncStatus.NoBackend` and leaves the queue
intact rather than treating it as an error or discarding writes.

Pushes are upserts keyed on the row id, because the outbox replays: a push whose
acknowledgement was lost has to be harmless the second time. Any backend's
row-level security therefore has to allow the *second* attempt (an update), not
only the insert. The audit table is the exception — `issue_status_changes` is
append-only, so its push is an insert that ignores duplicates. Photo rows are
the same, since a photo never changes once taken.

## Data model

- **Photos are their own aggregate**, not a field on `Issue`, for the same reason
  status history is not one: the list screen reads every issue row on every
  render, and image bytes in that row would make scrolling read megabytes.
  `issue_photos` holds a BLOB per photo and is only read by
  `observePhotos(issueId)`.
- **The bytes are the source of truth for a photo**, not a file path. A picked
  `content://` URI on Android stops being readable once the picker's grant
  lapses, so a row pointing at one would go blank; the bytes are also exactly
  what `PhotoBackendApi.uploadPhoto` needs. Readers prefer `remoteUrl` when it
  exists and fall back to `bytes`, which lets Coil cache the uploaded copy —
  `ByteArray` is a first-class Coil model, so the fallback needs no per-platform
  decoding. The filing device keeps its bytes after upload; other devices hold
  only the URL.
- **Review sits beside status.** A report is not public until a manager
  approves it. Until then its status is In Review; a rejected report is
  Rejected; an approved one moves between Open, In Progress and Complete. The
  decision itself lives in `IssueReview`, because a status cannot carry who
  decided or why. `observeIssues` leaves Rejected out unless asked for it by
  name, which keeps rejected reports off the list, board and map without every
  screen remembering to filter them. The repository refuses to change the
  status of an unapproved issue, to set In Review or Rejected by hand, or to
  complete an issue without a resolution, and `schema.sql` has matching CHECK
  constraints. `IssueReview`'s
  constructor refuses a rejection with no reason, so the "a manager cannot reject
  without saying why" rule cannot be bypassed by a caller; the entity mapper
  reads a bad row back as *pending* rather than throwing on one malformed record.
- **A manager's scope is four independent sets** (citywide, districts,
  neighborhoods, departments) because cities delegate in all four ways, and any
  one match is enough. The council district is resolved from the neighborhood at
  submission rather than on the form, so every path that creates an issue —
  form, seeder, a future import — routes to the right district manager.
- **`users` is the one table with no sync columns.** Roles and scopes are
  the identity provider's data in a real deployment, not this app's, so there is
  nothing to queue for upload; the table exists because the mock provider has
  nothing to read them from.
- **Enums persist as explicit `storageKey` strings** rather than names or
  ordinals, so rule 5 survives refactors and CSV exports stay stable.
- **Timestamps are epoch milliseconds** (`Long`) everywhere, in the domain, in
  SQLite and over the wire, so no two layers have to agree on a date format.

## Access control, accounts and sessions

- **The visibility rules are applied in the repository, not the screens.**
  `domain/access/IssueAccess.kt` holds them as pure functions and
  `DefaultIssueRepository` combines every read with the current user, so a screen
  that forgets to ask cannot leak an untriaged report, and signing in re-runs
  every query. That is also why the repository — not the view model — checks
  `manages()` before approving, rejecting or editing: the hidden button is UI,
  the check is the rule.
- **An `Account` is not a `User`.** An account is the login — an identity the
  provider vouched for, and all `AuthService` deals in. A user is the person this
  app works with: a role, and the areas they answer for. `UserSession` joins the
  two, and it is what every screen and repository injects; nothing above the data
  layer reads `AuthService`, because an account cannot answer "may they approve
  this". The split is what lets an admin change a role without the next sign-in
  undoing it: a provider's `claimedRole` and `claimedScope` seed a new `User` and
  are never read again. Registration matches on the address as well as the id, so
  a provider that mints a fresh id every run still lands on the same person.
- **The join is a subscription, not a lookup**, so a role or area assigned while
  someone is signed in reaches them without a sign-out.
- **The assignment rules live in `UserRepository`, not the screens.** Both write
  paths take the acting `User` as an argument rather than reading the session,
  because `UserSession` is built out of this repository and injecting it there
  would close a cycle. Assigning refuses to change the caller's own role, so the
  last admin cannot lock the deployment out of the screen that would fix it.
- **Where the rules are enforced depends on the backend.** With data on the
  device, `domain/access` is the definition of the boundary rather than the
  boundary itself. A hosted deployment has to restate the same rules where the
  data lives — `supabase/schema.sql` is the worked example, in row-level security
  policies.

## Storage

- **Room 3** is `androidx.room3:room3-*` (package `androidx.room3`, plugin id
  `androidx.room3`, extension `room3 { }`) — not a version bump of
  `androidx.room`. It is KSP-only, and the KSP processor must be added per
  target, not once (see `shared/build.gradle.kts`).
- **Room 3 supports js/wasmJs**, so the schema, DAOs, `@Database`, the mappers
  and `RoomLocalStore` all live in `commonMain` and compile for every target.
  Each platform source set contributes only a `getDatabaseBuilder` and the
  concrete `SQLiteDriver`.
- **Web persistence is real**, via the `:sqliteWasmWorker` module.
  `WebWorkerSQLiteDriver` needs an OPFS worker implementing its message
  protocol, which androidx does not publish; ours is adapted from the reference
  sample at https://github.com/danysantiago/room-web-demo and packaged as a local
  npm module so webpack resolves
  `new URL("sqlite-wasm-worker/worker.js", import.meta.url)`. Two consequences
  worth remembering: the worker module must use `useEsModules()`, and the page
  has to be cross-origin isolated (COOP/COEP) or OPFS refuses to open the
  database.
- **Filtering happens in Kotlin, not SQL.** `IssueFilter` is applied above the
  data source, because the dataset is a few thousand issues for one city and
  keeping query translation out of the storage layer lets another store be
  dropped in.
- **Schemas are exported** to `shared/schemas/`. Bump `HelpMyCityDatabase`'s
  version on any schema change, and write a `Migration` for it once there are
  installs whose data has to survive the upgrade.

## Backend contract

A backend is four interfaces gathered into a `BackendProvider`:

| Interface | Responsibility |
| --- | --- |
| `IssueBackendApi` | issues and status changes: incremental fetch, upsert push, delete |
| `ReferenceDataBackendApi` | departments and neighborhoods, when the backend owns them |
| `PhotoBackendApi` | upload one photo and return a durable URL; list photos uploaded since a time |
| `AuthService` | sign in, sign up, continue as guest, and the current account |

`LocalOnlyBackend` is the default and returns `NotConfigured` from everything, so
the offline-first path runs end to end with no server. `:cityConfig`'s `supabase`
package is the worked example: Postgres through postgrest, Supabase Auth, and a
public Storage bucket for photos. It reads its project URL and publishable key
from the build (`cityConfig/build.gradle.kts` generates `SupabaseSecrets` from
the `supabase.*` settings), and with both blank it reports itself unconfigured
and falls back to `MockAuthService` — so the same source produces a local-only
build and a hosted one.

Two rules any implementation inherits, both from the sync engine:

1. **Writes must be idempotent**, because the outbox replays them.
2. **`NotConfigured` is not an error.** Return it when there is nothing to talk
   to, so the queue is preserved instead of failed.

Roles and manager scopes are read from the provider's own claim on the account —
in the Supabase example, `app_metadata`, which only a trusted server can write.
`user_metadata` would let anyone make themselves an admin.

## UI and navigation

- **Navigation 3** (`androidx.navigation3:navigation3-runtime` plus JetBrains'
  `navigation3-ui`) with a typed back stack in `ui/navigation/Routes.kt`. Off
  Android the back stack is restored through `SavedStateConfiguration`, so every
  `NavKey` needs a polymorphic serializer registration there or restore fails.
- **View models are scoped to their back stack entry** by
  `rememberViewModelStoreNavEntryDecorator`, so popping a detail screen clears
  its state.
- **One breakpoint, at 900 dp.** Below it the app is a phone: bottom bar, one
  screen at a time, a floating report button. At or above it the tabs and the
  report button move into the header and the list and the map sit side by side
  in `IssueWorkspaceScreen`. Pushed screens are capped to a readable width
  instead of filling a monitor.
- **The header replaces `TopAppBar`** (`ui/components/CityHeader.kt`) because a
  Material top bar is a title and two icon slots at a fixed height: right on a
  phone, and a phone app everywhere else. It draws the city's photograph, its
  mark, the tabs and the primary action, all from `CityBranding`.
- **Status is its own line on a card**, a colored dot and a label above the
  title, not one more chip among the priority and category tags. Its color comes
  from `ui/theme/StatusColors.kt`, which the map markers share, so a status means
  the same color wherever it appears.

## Maps

- **The map is common code**, in `ui/map/`, because the web target is Kotlin/JS.
  See [Why the web target is JS](#why-the-web-target-is-js) — that is the whole
  reason for the target choice, and reversing one means reversing both.
- **What a city has to say about its map** is two things city-agnostic code
  cannot know: where the camera opens and whose basemap tiles to draw. Both live
  in `MapSettings` on `CityProfile`, next to departments, for the same reason.
  The default is **OpenFreeMap** — real OpenStreetMap street detail, no API key,
  no signup — so a fresh fork sees a usable map. It is donation-funded, so a
  deployment leaning on it should donate or self-host; the cheapest durable
  answer is a Protomaps `.pmtiles` extract on object storage behind a CDN.
- **Geocoding is off by default**, because every geocoder is somebody's metered
  or donated capacity. `CityProfile.geocoder` is a Compass `Geocoder`;
  `deviceGeocoder() orElse NominatimPlatformGeocoder(...)` is the pairing that
  needs no signup on any target, and `NominatimPlatformGeocoder` honors
  Nominatim's one-request-a-second policy and identifies the deployment.
- **The web build needs one line of setup.** `installMapLibreCompose()` must run
  inside `onWasmReady` **before** `ComposeViewport`, because that is when it
  reaches Compose's graphics context; it is the first thing
  `webApp/src/jsMain/.../main.kt` does after `initKoin`. Its `workerUrl`
  defaults to MapLibre GL JS's worker on jsDelivr, which works on this app's
  cross-origin-isolated pages because jsDelivr sends
  `Cross-Origin-Resource-Policy: cross-origin`; passing a same-origin URL is the
  durable answer for a civic app that should not depend on a third-party CDN.

## Why the web target is JS

The web target is **Kotlin/JS, not Kotlin/Wasm**, and maps are the only reason.

`org.maplibre.compose:maplibre-compose` publishes `android`, `iosArm64`,
`iosSimulatorArm64`, `jvm` and `js` — and no `wasmJs`. A `commonMain` dependency
has to resolve for *every* target its module declares, so as long as `:shared`
declared `wasmJs`, the map could not be written in common code at all: it would
have needed a seam, a fourth implementation and a placeholder for the web.
Dropping `wasmJs` buys one map screen in `commonMain` that all three platforms
run.

Three things follow from that choice, and they are worth knowing before anyone
reverses it:

- **Kotlin/JS runs on browsers Kotlin/Wasm cannot.** Wasm needs WasmGC — Chrome
  119+, Firefox 120+, Safari 18.2+. For an app whose whole point is that any
  resident can file a report, the wider floor is worth more than the frame rate.
  Wasm is meaningfully faster at Compose rendering; neither target is fast or
  slow at drawing a map, because MapLibre GL JS does that in the browser's own
  code either way.
- **`useEsModules()` is required.** maplibre-compose bundles maplibre-gl 6, which
  is ESM-only — its package `exports` map `.` under `import` and nothing else —
  and a CommonJS output fails on it with `"." is not exported under the
  conditions [...]`. Every module with a `js` target sets it (`:shared`,
  `:webApp`, `:cityConfig`, and `:sqliteWasmWorker`, which already did for
  `import.meta.url`), and `index.html` loads the bundle with `type="module"`.
- **Migrating to Wasm later** is cheap by design, and upstream is heading that
  way: the tracking issue is
  [maplibre-compose#209](https://github.com/maplibre/maplibre-compose/issues/209).
  The maintainer's stated plan is to commonize `jsMain` into a `webMain` shared
  with `wasmJs`, i.e. a single API for both. When that lands the migration here
  is: add `wasmJs { browser() }` to `:shared`, `:webApp`, `:cityConfig` and
  `:sqliteWasmWorker`, rename each `src/jsMain` to `src/webMain`, add a
  `kspWasmJs` Room processor, and bump the version. No map code changes.

## Localization and accessibility

Every user-visible string is a Compose resource in
`shared/src/commonMain/composeResources/values/strings.xml`, with a Spanish
translation beside it in `values-es/`. The two files hold the same keys, and
`ui/Labels.kt` routes every enum — status, priority, category, role — to
a resource, so nothing can leak a `storageKey` onto the screen. Adding a language
is a new `values-<code>/strings.xml` and no code change.

Accessibility is ordinary Material 3 practice: chips announce their category
along with their value ("Priority: High", not "High"), decorative icons carry
null content descriptions, form fields have labels and error text rather than
color alone, and the header's controls are real buttons with descriptions. Color
is never the only signal — the map's markers differ by color, so the screen
prints a labeled legend beside them.

## Tests

`shared/src/commonTest/` covers the parts where a rule can be wrong without
looking wrong: the repositories (visibility filtering, review outcomes, edit
diffing), the sync engine (outbox drain, `NotConfigured` handling), the session
join, the CSV exporter, the mock auth service and the location field's geocoding
state machine. They run against `InMemoryLocalStore` and fakes in the same
package, so no test needs a database or a network.

```bash
./gradlew :shared:testAndroidHostTest
```

That task runs the whole `commonTest` suite on the JVM and is the quickest green
check. `:shared:jsTest` needs a Chrome binary for ChromeHeadless;
`:shared:iosSimulatorArm64Test` needs a full Xcode toolchain.

## Toolchain notes

- **Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.4.1.** Android
  `minSdk` 30, `compileSdk`/`targetSdk` 37.
- **Koin** uses the compiler plugin (`io.insert-koin.compiler.plugin`), not KSP
  (the KSP processor is deprecated upstream). The plugin emits one hint
  declaration per DSL binding *per compilation*, so declaring the same
  `single<T>` in both `commonMain` and a platform source set fails KLIB
  serialization with a signature clash. That is what the `LocalStore` seam is
  for. Add a new platform-varying dependency the same way — one common binding
  that reads from a platform-bound holder — rather than repeating a `single<T>`
  per source set.
- **kotlinx-datetime is pinned with `strictly = "0.6.2"`, and the pin is
  load-bearing.** supabase-kt is compiled against 0.6.x and references
  `InstantIso8601Serializer`, which 0.7.0 removed when `Instant` moved to
  `kotlin.time`. Kotlin/JS partial linkage turns that missing symbol into a stub
  that throws only when it runs, so a newer version still *builds* and then
  fails at sign-in. Raise it when supabase-kt targets 0.7+.
- **A city module** needs only `api(project(":shared"))`. `:shared` exposes
  coroutines, kotlinx-serialization, Koin annotations and Compass as `api` for
  exactly this reason — `AuthService` returns a `StateFlow`, the domain models
  are `@Serializable`, and a profile supplies a `Geocoder`.
- **Yarn pins `memfs` to 4.68.0** in the root build file: webpack-dev-server 6
  floats it, and 4.78.0 declares a sub-package that was never published to npm.

## Known gaps

- **Cross-origin isolation is required on the web.** SQLite's WASM build stores
  the database in OPFS, which only works on a cross-origin-isolated page. The
  dev server sets the headers via `webApp/webpack.config.d/webpack.config.js`;
  whatever hosts the built app must send
  `Cross-Origin-Opener-Policy: same-origin` and
  `Cross-Origin-Embedder-Policy: require-corp` too.
- **People and roles is only as full as its source.** The screen lists everyone
  whose account this deployment has seen. With a real identity provider that is
  the deployment's list; with the mock it is whoever has signed in on this
  device, because the mock has no list to pull from and this app deliberately
  does not create accounts for other people.
- **No dashboard charts.** Status, priority and department breakdowns, and the
  remaining geographic dimensions (census tract, police precinct) are modelled
  on `IssueLocation` with nothing filling them in. Neighborhood boundaries are
  drawn on the map but not used to tag an issue from its pin.
