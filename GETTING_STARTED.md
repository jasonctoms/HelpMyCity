# Getting started

A walkthrough for taking this repository and running it for your own city, from
a fork to a hosted app.

Everything that makes this Oceanside's app lives in one module,
[`cityConfig/`](./cityConfig). Nothing outside it names a city — not `shared/`,
not the three composition roots, not the tests. If you find yourself editing
anything else to run in your own town, that is a bug in the seam and worth
reporting.

For what the app does, see the [README](./README.md). For how it is built, see
[ARCHITECTURE.md](./ARCHITECTURE.md).

**Contents** — [Before you start](#before-you-start) · [1. Fork and run it](#1-fork-and-run-it) ·
[2. Describe your city](#2-describe-your-city) ·
[3. Point it at the city's request system](#3-point-it-at-the-citys-request-system) ·
[4. Remove the demo sign-ins](#4-remove-the-demo-sign-ins) ·
[5. Choose a backend](#5-choose-a-backend) · [6. Rename the app](#6-rename-the-app) ·
[7. Host the web build](#7-host-the-web-build) ·
[Adding a language](#adding-a-language) · [Checklist](#checklist)

## Before you start

You need:

- **JDK 17 or newer.**
- **The Android SDK**, for the Android build. `local.properties` needs
  `sdk.dir=/path/to/Android/sdk`; Android Studio writes it for you.
- **Xcode**, for the iOS build, with `xcode-select -p` pointing at
  `/Applications/Xcode.app/Contents/Developer` rather than at
  `CommandLineTools`.
- Nothing at all for the web build.

No backend account is needed to start. A fresh clone runs local-only: every
write stays in the device's database, the sync queue builds without draining,
and sign-in is a mock that accepts any well-formed credential.

## 1. Fork and run it

```bash
git clone https://github.com/<you>/<your-fork>.git
cd <your-fork>
./gradlew :webApp:jsBrowserDevelopmentRun
```

The browser is the fastest loop, and the web target is a first-class one here —
Room persists to OPFS and the map draws. The other two:

```bash
./gradlew :androidApp:assembleDebug
```

For iOS, open [iosApp/](./iosApp) in Xcode and run.

Confirm the tests are green before you change anything:

```bash
./gradlew :shared:testAndroidHostTest
```

## 2. Describe your city

Edit
[`cityConfig/src/commonMain/kotlin/.../cityconfig/ConfiguredCityProfile.kt`](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/ConfiguredCityProfile.kt)
in place. Every name the module exports is generic — `ConfiguredCityProfile`,
`ConfiguredBackend`, `CityRequestGateway` — so the rest of the repo keeps
compiling while you replace the contents.

`CityProfile` is the whole interface, and only `displayName`, `departments` and
`neighborhoods` are required:

| Field | What to put there |
| --- | --- |
| `displayName` | your city, as residents would name it |
| `departments` | who issues get routed to, each with the categories it handles and an optional contact address |
| `neighborhoods` | the areas issues are tagged with, each carrying its council district |
| `branding` | a header photograph, your logo, and a tagline |
| `map` | where the camera opens, how far in, and whose basemap tiles |
| `geocoder` | address lookup for the report form, or nothing |
| `externalRequestSystem` | the city's own request portal — see [step 3](#3-point-it-at-the-citys-request-system) |
| `demoLogins` | shared sign-ins for a public demo — see [step 4](#4-remove-the-demo-sign-ins) |

Four things to get right the first time:

**Ids are permanent.** Department and neighborhood ids are stored on every issue
and merged against on a later backend pull. Pick stable, readable ones
(`dept-public-works`, `nbhd-libby-lake`) and do not renumber them afterwards.

**Council districts come from neighborhoods.** There is no separate list: each
`Neighborhood` names its `councilDistrict`, and an issue's district is derived
from its neighborhood at submission. A manager can then be scoped to a district
without anyone tagging reports with one.

**Branding images are fetched over the network, from script.** The web build
loads them with `fetch`, not with an `<img>` tag, so the host has to send CORS
headers. A city's own CMS usually does not — which is why the sample points the
logo somewhere else. Host copies you have the rights to, somewhere that allows
cross-origin reads, and they will work on all three platforms. Set
`logoIsSingleColor = true` only if your mark is one flat color on transparency;
a multi-color seal needs the default, which puts it on a light plate.

**Geocoding is off unless you turn it on.** The default leaves the pin picker as
the only way to place an issue. The sample's pairing —
`deviceGeocoder() orElse NominatimPlatformGeocoder(...)` — is the one that needs
no account on any target: the phone's own geocoder on Android and iOS, and
OpenStreetMap's public Nominatim in a browser. If you use Nominatim, read the
KDoc on `NominatimPlatformGeocoder` first: the public instance is donated
capacity with a usage policy, the `userAgent` and `contactEmail` you pass are how
an admin reaches you, and a city with real traffic should run its own instance or
use one of Compass's keyed backends instead. Bound it with a `viewBox` of your
city limits so "Main St" resolves to yours.

The map's default basemap is [OpenFreeMap](https://openfreemap.org/): real
OpenStreetMap detail, no key, no signup. It is donation-funded, so a deployment
that comes to depend on it should donate or self-host — a
[Protomaps](https://protomaps.com/) `.pmtiles` extract on object storage behind
a CDN is the cheapest durable answer. `MapSettings` also carries two other
ready-made style constants.

## 3. Point it at the city's request system

Most cities run a request-a-service portal, and almost none expose an API for it.
[`CityRequestGateway.kt`](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/CityRequestGateway.kt)
is the sample: a `ManualExternalRequestGateway` with a stable id, a display name
and the URL a manager should open. That is enough for a manual hand-off — the
manager files the request on the city's site and records the reference number in
the app.

- The `systemId` is persisted on every `ExternalReference`, so it must never
  change. The display name can.
- If your city does offer an API, implement `ExternalRequestGateway` directly
  instead of extending the manual base. Nothing above `data/remote` changes.
- If there is no city system at all, leave `externalRequestSystem` unset. The
  hand-off section disappears from the issue screen.

## 4. Remove the demo sign-ins

`demoLogins` is what puts shared, published accounts on the sign-in screen, with
a notice explaining them. It exists for the hosted demo. Unless your deployment
is itself a public demo, **delete the override** — the notice goes with it, and
the sign-in screen becomes an ordinary one.

## 5. Choose a backend

A backend is one object: a `BackendProvider` exposing four interfaces, passed to
`initKoin` at each entry point. What you do here depends on how far you are.

### Option A — local-only, for now

Do nothing. `ConfiguredBackend` reports itself unconfigured while its project
details are blank, so every API returns `NotConfigured`, sign-in falls back to
the mock, and the app runs end to end on the device. This is a genuine mode, not
a broken one: it is worth demoing the app to a city this way before standing
anything up.

### Option B — Supabase, the worked example

[`supabase/README.md`](./supabase/README.md) walks through creating a project,
running the schema, seeding data, creating accounts and deploying the nightly
reset function, all in the dashboard. It is written for **this repository's own
demo project**, so read it as a sample: the demo-specific parts — the three
published accounts, the seed dataset, the daily reset function and its cron job
— exist to keep a public demo honest, and a real city wants the schema and the
policies without any of them.

Two parts of it are not demo-specific. [`schema.sql`](./supabase/schema.sql)
creates the tables, the row-level security policies and the photo bucket — that
is the backend. And two settings connect the app to the project; put them in
`local.properties`, which is gitignored:

```properties
supabase.url=https://YOUR-PROJECT-REF.supabase.co
supabase.publishableKey=sb_publishable_YOUR_KEY
```

The build reads them through `cityConfig/build.gradle.kts` — environment
variable, then `-P`, then `local.properties` — so CI sets `SUPABASE_URL` and
`SUPABASE_PUBLISHABLE_KEY` as secrets. Neither value is a secret in itself: the
publishable key is a public identifier, and what it may read and write is decided
by the policies in `schema.sql`. Never ship a `service_role` key; it bypasses
every policy.

Roles come from each account's `app_metadata`, which only a trusted server can
write. `user_metadata` would let anyone make themselves an admin.

### Option C — something else

Implement `BackendProvider` and its four interfaces in your own module —
`IssueBackendApi`, `ReferenceDataBackendApi`, `PhotoBackendApi` and
`AuthService` — and pass it as `initKoin(backend = ...)`. Nothing above
`data/remote` changes, and the
[`supabase` package](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/supabase)
is about 600 lines end to end, including row mapping.

What is worth knowing before you start, because it is inherited from the sync
engine rather than from any provider:

- **Every write must be idempotent.** The outbox replays, so a push whose
  acknowledgement was lost has to be harmless the second time. Issue pushes are
  upserts keyed on the id; whatever enforces permissions on your backend has to
  allow that *second* attempt, an update, not only the insert.
- **The status-change table is append-only.** Rewriting history is the one thing
  an audit trail must not allow, so that push is an insert that ignores
  duplicates.
- **Return `NotConfigured`, not a failure,** when there is nothing to talk to.
  The engine treats it as "nothing to do" and preserves the queue.
- **Acknowledge what the backend stored,** not what you sent, if a trigger or a
  policy can adjust a row on the way in.
- **Authentication is yours to shape.** `AuthService` deals only in accounts —
  sign in, sign up, continue as guest, and a `StateFlow` of the current one. A
  role and manager scope may arrive as a claim on the account, which seeds a new
  user record and is never read again; if your provider has no such claim, leave
  both null and let an admin assign roles in the app.
- **Restate the access rules where the data lives.**
  [`domain/access/IssueAccess.kt`](./shared/src/commonMain/kotlin/dev/helpmycity/domain/access/IssueAccess.kt)
  is short and quotable on purpose: an approved issue is public, an issue in
  triage is visible only to its submitter and the managers who can act on it.
  On the device that is a definition; on a server it has to be enforced.
  `supabase/schema.sql` is one way to write it.

## 6. Rename the app

The app's own name and identifiers are separate from the city profile:

| What | Where |
| --- | --- |
| Android label | `androidApp/src/main/res/values/strings.xml` |
| Android package and id | `namespace` and `applicationId` in `androidApp/build.gradle.kts` |
| iOS name and bundle id | `iosApp/Configuration/Config.xcconfig` (and `TEAM_ID` for signing) |
| Browser tab title | `webApp/src/jsMain/resources/index.html` |
| In-app name | `app_name` in `shared/src/commonMain/composeResources/values/strings.xml`, and `values-es/` |
| Launcher icons | Android: `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` (the adaptive layers) and the `res/mipmap-*` PNGs beside them. iOS: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` (light, dark and tinted). Web: `webApp/src/jsMain/resources/favicon.svg`, `favicon-32.png` and `apple-touch-icon.png`, linked from `index.html` |

Renaming the Kotlin package (`dev.helpmycity`) is optional and
mechanical. The one place that names it as a string is the Koin `@ComponentScan`
root in `di/AppModule.kt`; the generated Compose resources package
(`helpmycity.shared.generated.resources`) comes from the Gradle project names
instead, and `compose.resources { packageOfResClass = ... }` moves it.

## 7. Host the web build

```bash
./gradlew :webApp:jsBrowserDistribution
```

The bundle lands in `webApp/build/dist/js/productionExecutable`. It is static
files, but **not** ordinary ones in one respect: the local database is SQLite
WASM in OPFS, which only opens on a cross-origin-isolated page. Whatever serves
the build must send:

```
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
```

The dev server sets them in `webApp/webpack.config.d/webpack.config.js`. Without
them in production the app loads and then fails to open its database.

Two other things worth a look before a real launch. The MapLibre GL JS worker
loads from jsDelivr by default, which works — jsDelivr sends the
`Cross-Origin-Resource-Policy` header a cross-origin-isolated page needs — but
it is a third-party CDN in the path of a civic app;
`installMapLibreCompose(workerUrl = ...)` in `webApp/src/jsMain/.../main.kt`
takes a same-origin URL instead. And the branding images from step 2 have to be
reachable with CORS headers from the domain you serve.

## Adding a language

Copy `shared/src/commonMain/composeResources/values/strings.xml` to
`values-<code>/strings.xml` and translate the values. The file is the whole
surface: every user-visible string, including every enum label, is a resource,
and `ui/Labels.kt` routes them. No code changes.

## Checklist

- [ ] `ConfiguredCityProfile`: name, departments, neighborhoods, with stable ids
- [ ] Branding images hosted somewhere that sends CORS headers
- [ ] Map center, zoom, and a basemap you are willing to depend on
- [ ] Geocoder chosen, or deliberately left off
- [ ] `CityRequestGateway` pointed at the city's portal, or removed
- [ ] `demoLogins` override deleted
- [ ] Backend decided; access rules restated wherever the data lives
- [ ] App name, ids and icons replaced
- [ ] Web host sending COOP/COEP
- [ ] `./gradlew :shared:testAndroidHostTest` green
