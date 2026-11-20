# Architecture

This is how HelpMyCity is put together, and why. It is the main technical record for the
project. Most of the constraints in here were learned the hard way, so I wrote them down to
save the next person (probably me) from having to learn them again.

If you want to know what the app does, read the [README](./README.md). If you want to set up
your own city, read [CITY_CONFIG.md](./CITY_CONFIG.md).

**Contents:** [Design rules](#design-rules) · [Repository layout](#repository-layout) ·
[The two seams](#the-two-seams) · [Object graph](#object-graph) ·
[Data flow](#data-flow) · [Data model](#data-model) ·
[Access control, accounts and sessions](#access-control-accounts-and-sessions) ·
[Storage](#storage) · [Backend contract](#backend-contract) ·
[UI and navigation](#ui-and-navigation) · [Maps](#maps) ·
[Why the web target is JS](#why-the-web-target-is-js) ·
[Localization and accessibility](#localization-and-accessibility) ·
[Tests](#tests) · [Toolchain notes](#toolchain-notes) · [Known gaps](#known-gaps)

## Design rules

There are seven choices that pretty much everything else follows from.

1. **Common-first Compose Multiplatform** on Android, iOS, and the web (the web is Kotlin/JS,
   and [Why the web target is JS](#why-the-web-target-is-js) explains that). Wherever it is
   possible, common code wins over platform code. Everything in the package map below is
   `commonMain`, including the Room schema, DAOs, mappers, and `RoomLocalStore`. Each
   platform source set only holds two things: a `getDatabaseBuilder` (following the
   [AndroidX Room KMP guide](https://developer.android.com/kotlin/multiplatform/room)) and a
   single Koin binding for `LocalStore`. Android gets its `Context` from Koin's
   `androidContext()`, so the shared module never has to reach for a global.
2. **Backend-agnostic.** Nothing above `data/remote` knows what the backend is. It all sits
   behind small API interfaces, so you can drop in a new implementation or replace one, and
   picking one is a single argument at the entry point.
3. **City-agnostic.** `:shared` compiles and runs without any city configured. Nothing in the
   core is allowed to name a city, a department, a neighborhood, or a municipal website; all
   of that lives behind `CityProfile` in a module the core does not depend on. Oceanside and
   its Supabase project live in `:cityConfig`, and a fork edits or replaces that module and
   nothing else.
4. **Offline-first.** Room 3 is the local SQLite store on every target. Reads always come
   from it. Writes land locally as `PENDING_UPLOAD`, and the sync engine drains the queue when
   it sees changes. If there is no backend, the queue just keeps building up and nothing gets
   dropped.
5. **Portable data.** Everything has to be exportable in a portable format, so switching
   providers is always an option. Enums are stored as stable string keys (never ordinals),
   and `IssueCsvExporter` can dump the whole table without a backend. An admin runs it from
   the profile screen.
6. **Accessible and bilingual.** Modern accessibility practice everywhere, and every string a
   user can see is a localizable Compose resource (English and Spanish for now).
7. **Maps** use `org.maplibre.compose:maplibre-compose`, and geocoding uses
   `dev.jordond.compass`. Which geocoder to use is up to each deployment through
   `CityProfile.geocoder`, and it is off by default. Compass covers Android and iOS with the
   device's own geocoder (free, no key, no metering), but it has no web backend that works
   without an account. So `:shared` adds `NominatimPlatformGeocoder` for the browser, which
   uses OpenStreetMap data just like the basemap does.

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

Each platform app module is a composition root, and that includes `:iosApp`. It is a Gradle
module that lives next to the Xcode project, and it is what Xcode builds the `Shared`
framework from. `:shared` does not declare a framework binary of its own, because the entry
point is where the city and backend get picked and `:shared` is not in a position to make
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

All of the variation goes through two seams, and they both work the same way. There is one
binding in common code that reads from a holder, instead of a separate binding for every
variant.

- **`LocalStore`** (`data/local/LocalStore.kt`) handles platform differences.
  `di/DataModule.kt` unpacks it into the four data sources once, in common code, and each
  platform binds exactly one implementation.
- **`Deployment`** (`deployment/Deployment.kt`) handles fork differences. `initKoin` stores the
  chosen `CityProfile` and `BackendProvider` in the holder, and `di/AppModule.kt` binds
  everything they expose.

I know the holder looks like ceremony, but it is there for a reason. The Koin compiler plugin
validates the object graph inside `:shared` at compile time, so every binding has to be
_declared_ there. The values, though, have to come from `cityConfig/`, and `:shared` is not
allowed to depend on that.

`:cityConfig` has no Koin annotations and no Compose. It is plain Kotlin data plus some
interface implementations, and it depends on nothing but `api(project(":shared"))`. Keeping
the Koin compiler plugin out of it means a second compilation is not emitting hints for the
same bindings, and it keeps writing a new city's module down to filling in some data classes.

## Object graph

`di/KoinSetup.kt` holds the root. `initKoin(city, backend, configure)` runs once per platform
entry point (Android's `Application`, iOS's `MainViewController`, and the web's `main`). It
installs the two choices into `Deployment` and starts Koin. It is idempotent, so Android
activity recreation and hot reload do not blow it up.

- Repositories, view models, the sync engine, and the seeder are annotated (`@Single`,
  `@KoinViewModel`) and get picked up by `@ComponentScan`, so adding a class does not mean
  editing a module.
- `AppModule` binds the fork seam by hand: `CityProfile`, the external request gateway, the
  geocoder, the three backend APIs, and `AuthService`, each one read out of `Deployment`.
- `applicationScope` is a `CoroutineScope` that lives as long as the process does, for the
  singletons that need to keep watching something (`DefaultUserSession` and `SyncEngine`).
- Both `initKoin` arguments default to something that runs with zero configuration:
  `UnconfiguredCityProfile` and `LocalOnlyBackend`. Even a clone with the city module deleted
  still builds and runs.

## Data flow

Data only goes one direction, with the local database in the middle:

```
screen ──> view model ──> repository ──┬──> LocalStore (Room)  ──> screen re-renders
                                       └──> row marked PENDING_UPLOAD
                                                     │
                                            SyncEngine drains the outbox
                                                     │
                                            BackendApi (push, then pull)
```

`DefaultSyncEngine` (`data/sync/`) is an outbox drain. On `start` it syncs once, because a
fresh install has nothing queued and would otherwise sit on an empty list until the owner
filed something. After that it watches the local store and syncs whenever the pending count
changes. That is debounced by 1.5 s, so a manager editing a form causes one sync and not a
whole burst of them. Each run pushes pending issues, pushes pending status changes, uploads
pending photos, and then pulls issues and photos that changed since the last successful sync.
Photos go after issues in both directions; a backend can refuse a photo for an issue it has
never seen, and a pulled photo for an issue this device cannot read just gets skipped.

`RemoteResult` has three cases: `Success`, `Failure(retryable)`, and `NotConfigured`. That
third one is the important one. `NotConfigured` means "there is no backend here", so the
engine reports `SyncStatus.NoBackend` and leaves the queue alone instead of treating it as an
error or throwing writes away.

Pushes are upserts keyed on the row id, because the outbox replays. If a push goes through
but the acknowledgement gets lost, sending it a second time has to be harmless. That means
any backend's row-level security has to allow the _second_ attempt (which is an update), and
not only the insert. The audit table is the exception. `issue_status_changes` is append-only,
so its push is an insert that ignores duplicates. Photo rows work the same way, since a photo
never changes once it has been taken.

## Data model

- **Photos are their own aggregate**, not a field on `Issue`, for the same reason status
  history is not a field. The list screen reads every issue row on every render, and putting
  image bytes in that row would mean reading megabytes just to scroll. `issue_photos` holds a
  BLOB per photo and only `observePhotos(issueId)` reads it.
- **The bytes are the source of truth for a photo**, not a file path. On Android, a picked
  `content://` URI stops being readable once the picker's grant runs out, so a row that
  pointed at one would eventually go blank. The bytes are also exactly what
  `PhotoBackendApi.uploadPhoto` needs anyway. Readers use `remoteUrl` when there is one and
  fall back to `bytes` when there isn't, which lets Coil cache the uploaded copy. `ByteArray`
  is a first-class Coil model, so the fallback does not need any per-platform decoding. The
  device that filed the report keeps its bytes after upload; every other device only holds
  the URL.
- **Review sits next to status.** A report is not public until a manager approves it. Until
  then its status is In Review. A rejected report is Rejected, and an approved one moves
  between Open, In Progress, and Complete. The decision itself lives in `IssueReview`,
  because a status has no way to say who decided or why. `observeIssues` leaves out Rejected
  unless you ask for it by name, which keeps rejected reports off the list, board, and map
  without every screen having to remember to filter them. The repository will not change the
  status of an unapproved issue, set In Review or Rejected by hand, or complete an issue
  without a resolution, and `schema.sql` has matching CHECK constraints. `IssueReview`'s
  constructor will not accept a rejection with no reason, so nobody calling it can get around
  the "a manager has to say why" rule. If the entity mapper finds a bad row, it reads it back
  as _pending_ instead of throwing over one malformed record.
- **A manager's scope is four independent sets** (citywide, districts, neighborhoods, and
  departments), because cities delegate in all four of those ways, and matching any one of
  them is enough. The council district gets worked out from the neighborhood when the report
  is submitted, not on the form, so every path that creates an issue (the form, the seeder, or
  some future import) still routes it to the right district manager.
- **`users` is the only table without sync columns.** In a real deployment, roles and scopes
  belong to the identity provider, not this app, so there is nothing to queue for upload. The
  table only exists because the mock provider has nowhere else to read them from.
- **Enums are stored as explicit `storageKey` strings**, not names or ordinals, so rule 5
  survives refactors and CSV exports stay stable.
- **Timestamps are epoch milliseconds** (`Long`) everywhere: in the domain, in SQLite, and over
  the wire. That way no two layers ever have to agree on a date format.

## Access control, accounts and sessions

- **Visibility rules are applied in the repository, not the screens.**
  `domain/access/IssueAccess.kt` has them as pure functions, and `DefaultIssueRepository`
  combines every read with the current user. A screen that forgets to ask can't leak an
  untriaged report, and signing in re-runs every query. It is also why the repository (and
  not the view model) checks `manages()` before approving, rejecting, or editing. The hidden
  button is just UI; the check is the actual rule.
- **An `Account` is not a `User`.** An account is the login, meaning an identity the provider
  vouched for, and that is all `AuthService` deals with. A user is the person this app works
  with: a role, and the areas they are responsible for. `UserSession` joins the two, and it is
  what every screen and repository injects. Nothing above the data layer reads `AuthService`,
  because an account has no way of answering "are they allowed to approve this?". Splitting
  them is what lets an admin change someone's role without the next sign-in undoing it; a
  provider's `claimedRole` and `claimedScope` seed a new `User` and then never get read again.
  Registration matches on the email address as well as the id, so a provider that hands out a
  new id every run still ends up on the same person.
- **The join is a subscription, not a lookup**, so if someone gets a new role or area while
  they are signed in, it reaches them without signing out.
- **The assignment rules live in `UserRepository`, not the screens.** Both write paths take
  the acting `User` as an argument instead of reading it from the session, because
  `UserSession` is built from this repository and injecting it here would create a cycle.
  Assigning will not change the caller's own role, so the last admin can't lock the whole
  deployment out of the screen that would fix it.
- **Where the rules get enforced depends on the backend.** With the data on the device,
  `domain/access` is the definition of the boundary, not the boundary itself. A hosted
  deployment has to restate the same rules where the data actually lives, and
  `supabase/schema.sql` is the worked example of that, using row-level security policies.

## Storage

- **Room 3** is `androidx.room3:room3-*` (package `androidx.room3`, plugin id
  `androidx.room3`, extension `room3 { }`). It is _not_ just a version bump of `androidx.room`.
  It is KSP-only, and the KSP processor has to be added for each target, not once (see
  `shared/build.gradle.kts`).
- **Room 3 supports js/wasmJs**, so the schema, DAOs, `@Database`, the mappers, and
  `RoomLocalStore` all live in `commonMain` and compile for every target. Each platform source
  set only contributes a `getDatabaseBuilder` and the concrete `SQLiteDriver`.
- **Web persistence is real**, through the `:sqliteWasmWorker` module.
  `WebWorkerSQLiteDriver` needs an OPFS worker that implements its message protocol, and
  androidx does not publish one. Ours is adapted from the reference sample at
  https://github.com/danysantiago/room-web-demo and packaged as a local npm module, so webpack
  can resolve `new URL("sqlite-wasm-worker/worker.js", import.meta.url)`. Two things to
  remember because of that: the worker module has to use `useEsModules()`, and the page has
  to be cross-origin isolated (COOP/COEP) or OPFS refuses to open the database.
- **Filtering happens in Kotlin, not SQL.** `IssueFilter` is applied above the data source.
  The dataset is a few thousand issues for one city, and keeping query translation out of the
  storage layer makes it easy to drop in a different store.
- **Schemas are exported** to `shared/schemas/`. Bump `HelpMyCityDatabase`'s version on any
  schema change, and write a `Migration` for it once there are installs out there whose data
  has to survive the upgrade.

## Backend contract

A backend is four interfaces bundled up into a `BackendProvider`:

| Interface | Responsibility |
| --- | --- |
| `IssueBackendApi` | issues and status changes: incremental fetch, upsert push, delete |
| `ReferenceDataBackendApi` | departments and neighborhoods, when the backend owns them |
| `PhotoBackendApi` | upload one photo and return a durable URL; list photos uploaded since a time |
| `AuthService` | sign in, sign up, continue as guest, and the current account |

`LocalOnlyBackend` is the default, and it returns `NotConfigured` from everything, so the
offline-first path runs end to end with no server at all. The `supabase` package in
`:cityConfig` is the worked example: Postgres through postgrest, Supabase Auth, and a public
Storage bucket for photos. It reads its project URL and publishable key from the build
(`cityConfig/build.gradle.kts` generates `SupabaseSecrets` from the `supabase.*` settings).
If both of those are blank it reports itself as unconfigured and falls back to
`MockAuthService`, so the same source can produce either a local-only build or a hosted one.

Any implementation inherits two rules, and both come from the sync engine:

1. **Writes have to be idempotent**, because the outbox replays them.
2. **`NotConfigured` is not an error.** Return it when there is nothing to talk to, so the
   queue gets kept instead of failed.

Roles and manager scopes come from the provider's own claim on the account. In the Supabase
example that is `app_metadata`, which only a trusted server can write. Using `user_metadata`
would let anyone make themselves an admin, which is...not great.

## UI and navigation

- **Navigation 3** (`androidx.navigation3:navigation3-runtime` plus JetBrains'
  `navigation3-ui`) with a typed back stack in `ui/navigation/Routes.kt`. Outside of Android,
  the back stack is restored through `SavedStateConfiguration`, so every `NavKey` needs a
  polymorphic serializer registered there or restoring it fails.
- **View models are scoped to their back stack entry** by
  `rememberViewModelStoreNavEntryDecorator`, so popping a detail screen clears its state.
- **On the web, the back stack is mirrored into browser history**
  (`ui/navigation/BrowserHistory.js.kt`). That way the browser's back and forward buttons work
  and every screen has its own address. Addresses are hash URLs (`/#/issues/<id>`,
  `/#/profile`) mapped in `ui/navigation/RoutePaths.kt`. A hash does not need a rewrite rule on
  the host, and it keeps the build's relative asset paths working. If you add a route, it
  needs an address there too. The browser tab uses the same screen title the header shows,
  followed by the city's name.
- **There is one breakpoint, at 900 dp.** Below that the app is a phone app: a bottom bar, one
  screen at a time, and a floating report button. At or above it, the tabs and the report
  button move up into the header, and the list and map sit side by side in
  `IssueWorkspaceScreen`. Pushed screens are capped at a readable width instead of stretching
  across a whole monitor.
- **The header replaces `TopAppBar`** (`ui/components/CityHeader.kt`). A Material top bar is a
  title and two icon slots at a fixed height, which is great on a phone and makes it look like
  a phone app everywhere else. The header draws the city's photo, its logo, the tabs, and the
  primary action, all from `CityBranding`.
- **Status gets its own line on a card**, a colored dot and a label above the title, instead
  of being one more chip next to the priority and category tags. Its color comes from
  `ui/theme/StatusColors.kt`, which the map markers share too, so a status is the same color
  wherever you see it.

## Maps

- **The map is common code** in `ui/map/`, because the web target is Kotlin/JS. See
  [Why the web target is JS](#why-the-web-target-is-js); this is honestly the whole reason for
  that choice, and undoing one means undoing the other.
- **A city only has to tell the map two things** that city-agnostic code can't know: where the
  camera starts, and whose basemap tiles to draw. Both live in `MapSettings` on `CityProfile`,
  next to departments, for the same reason departments are there. The default is
  OpenFreeMap, which has real OpenStreetMap street detail with no API key and no signup, so a
  fresh fork gets a usable map right away. It is funded by donations, though, so a deployment
  that relies on it should donate or self-host. The cheapest long-term option is a Protomaps
  `.pmtiles` extract on object storage behind a CDN.
- **Geocoding is off by default**, because every geocoder is running on somebody's metered or
  donated capacity. `CityProfile.geocoder` is a Compass `Geocoder`.
  `deviceGeocoder() orElse NominatimPlatformGeocoder(...)` is the combo that needs no signup
  on any target, and `NominatimPlatformGeocoder` sticks to Nominatim's one-request-per-second
  policy and identifies the deployment.
- **The web build needs a little bit of setup.** `installMaplibreCompose()` has to run inside
  `onWasmReady` _before_ `ComposeViewport`, because that is when it can reach Compose's
  graphics context. It is the first thing `webApp/src/jsMain/.../main.kt` does after
  `initKoin`. The library's default worker is a `blob:` module worker, and WebKit will not
  start one of those on a cross-origin-isolated page (so iOS Safari just drew no tiles). To
  get around that, `webApp/webpack.config.d/maplibreWorker.js` copies the worker that ships
  inside the `maplibre-compose` npm package next to the bundle as `maplibre-gl-worker.mjs`,
  and `main.kt` passes that as the `workerUrl`. It also means a civic app is not depending on
  a third-party CDN for its map.

## Why the web target is JS

The web target is Kotlin/JS, _not_ Kotlin/Wasm, and maps are the only reason.

`org.maplibre.compose:maplibre-compose` publishes `android`, `iosArm64`,
`iosSimulatorArm64`, `jvm`, and `js`, but no `wasmJs`. A `commonMain` dependency has to
resolve for _every_ target its module declares. So as long as `:shared` declared `wasmJs`, the
map could not be written in common code at all; it would have needed a seam, a fourth
implementation, and a placeholder for the web. Dropping `wasmJs` gets us one map screen in
`commonMain` that all three platforms run.

A few things follow from that choice, and they are worth knowing before anyone tries to undo
it:

- **Kotlin/JS runs on browsers that Kotlin/Wasm can't.** Wasm needs WasmGC, which means
  Chrome 119+, Firefox 120+, or Safari 18.2+. For an app where the whole point is that _any_
  resident can file a report, supporting more browsers is worth more than the frame rate.
  Wasm is noticeably faster at Compose rendering, but neither target is faster or slower at
  drawing the map, because MapLibre GL JS does that in the browser's own code either way.
- **`useEsModules()` is required.** GL JS 6 is ESM-only (its package `exports` map `.` under
  `import` and nothing else), and CommonJS output fails on it with
  `"." is not exported under the conditions [...]`. Every module with a `js` target sets it
  (`:shared`, `:webApp`, `:cityConfig`, and `:sqliteWasmWorker`, which needed it for
  `import.meta.url` anyway), and `index.html` loads the bundle with `type="module"`.
- **Moving to Wasm later** should be cheap, on purpose, and upstream is heading that way. The
  tracking issue is
  [maplibre-compose#209](https://github.com/maplibre/maplibre-compose/issues/209). The
  maintainer's plan is to commonize `jsMain` into a `webMain` that is shared with `wasmJs`,
  so one API for both. Once that lands, the migration here is: add `wasmJs { browser() }` to
  `:shared`, `:webApp`, `:cityConfig`, and `:sqliteWasmWorker`, rename each `src/jsMain` to
  `src/webMain`, add a `kspWasmJs` Room processor, and bump the version. No map code should
  have to change.

## Localization and accessibility

Every string a user can see is a Compose resource in
`shared/src/commonMain/composeResources/values/strings.xml`, with a Spanish translation next to
it in `values-es/`. The two files have the same keys, and `ui/Labels.kt` maps every enum
(status, priority, category, and role) to a resource, so a `storageKey` can never leak onto
the screen. Adding a language is just a new `values-<code>/strings.xml`, with no code changes.

Accessibility is ordinary Material 3 practice. Chips announce their category along with their
value ("Priority: High", not just "High"), decorative icons have null content descriptions,
form fields have labels and error text instead of relying on color, and the header's controls
are real buttons with descriptions. Color is never the only signal; the map's markers differ
by color, so the screen also shows a labeled legend next to them.

## Tests

`shared/src/commonTest/` covers the parts where a rule can be wrong without _looking_ wrong:
the repositories (visibility filtering, review outcomes, and edit diffing), the sync engine
(outbox drain and `NotConfigured` handling), the session join, the CSV exporter, the mock auth
service, and the location field's geocoding state machine. They run against
`InMemoryLocalStore` and some fakes in the same package, so none of the tests need a database
or a network.

```bash
./gradlew :shared:testAndroidHostTest
```

That task runs the whole `commonTest` suite on the JVM, and it is the quickest way to get a
green check. `:shared:jsTest` needs a Chrome binary for ChromeHeadless, and
`:shared:iosSimulatorArm64Test` needs a full Xcode toolchain.

## Toolchain notes

- **Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.4.1.** Android `minSdk` is 30, and
  `compileSdk`/`targetSdk` are 37.
- **Koin** uses the compiler plugin (`io.insert-koin.compiler.plugin`), not KSP (the KSP
  processor is deprecated upstream). The plugin emits one hint declaration per DSL binding
  _per compilation_, so declaring the same `single<T>` in both `commonMain` and a platform
  source set fails KLIB serialization with a signature clash. That is exactly what the
  `LocalStore` seam is for. If you add a new dependency that varies by platform, do it the
  same way, with one common binding that reads from a holder the platform binds, instead of
  repeating a `single<T>` in every source set.
- **kotlinx-datetime is pinned with `strictly = "0.6.2"`, and that pin really matters.**
  supabase-kt is compiled against 0.6.x and references `InstantIso8601Serializer`, which 0.7.0
  removed when `Instant` moved to `kotlin.time`. Kotlin/JS partial linkage turns that missing
  symbol into a stub that only throws when it actually runs, so a newer version still
  _builds_ and then falls over at sign-in. Raise it once supabase-kt targets 0.7+.
- **A city module** only needs `api(project(":shared"))`. `:shared` exposes coroutines,
  kotlinx-serialization, Koin annotations, and Compass as `api` for exactly this reason;
  `AuthService` returns a `StateFlow`, the domain models are `@Serializable`, and a profile
  supplies a `Geocoder`.
- **Yarn pins `memfs` to 4.68.0** in the root build file. webpack-dev-server 6 lets it float,
  and 4.78.0 declares a sub-package that never got published to npm.

## Known gaps

- **Cross-origin isolation is required on the web.** SQLite's WASM build keeps the database in
  OPFS, which only works on a cross-origin-isolated page. The dev server sets the headers in
  `webApp/webpack.config.d/webpack.config.js`, but whatever hosts the built app has to send
  `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp`
  too.
- **People and roles is only as complete as its source.** The screen lists everyone whose
  account this deployment has seen. With a real identity provider that is the deployment's
  whole list. With the mock, it is just whoever has signed in on this device, because the
  mock has no list to pull from and this app intentionally does not create accounts for other
  people.
- **No dashboard charts yet.** Status, priority, and department breakdowns are missing, and
  the other geographic dimensions (census tract and police precinct) are modelled on
  `IssueLocation` but nothing fills them in. Neighborhood boundaries get drawn on the map, but
  they are not used to tag an issue from its pin yet.
