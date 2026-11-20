# Configuring a city

This is everything it takes to run this repo for your own city: every setting a city
supplies, where it goes, and what you will want to get right the first time.

Everything that makes this Oceanside's app lives in one module,
[`cityConfig/`](./cityConfig). Nothing outside of it names a city. Not `shared/`, not the
three composition roots, and not the tests. If you find yourself editing anything else just
to get it running in your own town, that is a bug in the seam, and I would definitely like to
hear about it.

Out of the box, that module is the configuration for the HelpMyCity demo at
[helpmycity.dev](https://helpmycity.dev). That is Oceanside, CA, on Supabase, with shared
sign-ins and a dataset that resets every night. Configuring your city means swapping its
contents out for your own.

If you want to know what the app does, read the [README](./README.md). If you want to know how
it is built, read [ARCHITECTURE.md](./ARCHITECTURE.md).

**Contents:** [Before you start](#before-you-start) · [1. Fork and run it](#1-fork-and-run-it) ·
[2. The city profile](#2-the-city-profile) ·
[3. Departments and neighborhoods](#3-departments-and-neighborhoods) ·
[4. Branding](#4-branding) · [5. The map](#5-the-map) ·
[6. Address lookup](#6-address-lookup) ·
[7. The city's request system](#7-the-citys-request-system) ·
[8. The backend](#8-the-backend) · [9. The app's name and icons](#9-the-apps-name-and-icons) ·
[10. Hosting the web build](#10-hosting-the-web-build) ·
[Adding a language](#adding-a-language) · [Checklist](#checklist)

## Before you start

You will need:

- **JDK 17 or newer.**
- **The Android SDK**, for the Android build. `local.properties` needs
  `sdk.dir=/path/to/Android/sdk`, but Android Studio writes that for you.
- **Xcode**, for the iOS build, with `xcode-select -p` pointing at
  `/Applications/Xcode.app/Contents/Developer` and not at `CommandLineTools`.
- Nothing at all for the web build.

You don't need a backend account to get started. A fresh clone runs local-only, so every
write stays in the device's database, the sync queue builds up without ever draining, and
sign-in is a mock that accepts any credential that looks valid.

## 1. Fork and run it

```bash
git clone https://github.com/<you>/<your-fork>.git
cd <your-fork>
./gradlew :webApp:jsBrowserDevelopmentRun
```

The browser is the quickest way to iterate, and the web is a first-class target here; Room
persists to OPFS and the map draws just like it does everywhere else. For the other two:

```bash
./gradlew :androidApp:assembleDebug
```

For iOS, open [iosApp/](./iosApp) in Xcode and run it.

Before you change anything, make sure the tests pass:

```bash
./gradlew :shared:testAndroidHostTest
```

## 2. The city profile

A city is described in one file,
[`ConfiguredCityProfile.kt`](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/ConfiguredCityProfile.kt).
Just edit it in place. Every name the module exports is generic (`ConfiguredCityProfile`,
`ConfiguredBackend`, and `CityRequestGateway`), so the rest of the repo keeps compiling while
you replace what is inside.

`CityProfile` is the whole interface, and only `displayName`, `departments`, and
`neighborhoods` are required:

| Field | What to put there | |
| --- | --- | --- |
| `displayName` | your city, the way residents would say it | required |
| `departments` | who issues get routed to | [step 3](#3-departments-and-neighborhoods) |
| `neighborhoods` | the areas issues are tagged with | [step 3](#3-departments-and-neighborhoods) |
| `branding` | a header photo, your logo, and a tagline | [step 4](#4-branding) |
| `map` | where the camera starts, how far zoomed in, and whose basemap tiles | [step 5](#5-the-map) |
| `geocoder` | address lookup for the report form, or nothing | [step 6](#6-address-lookup) |
| `externalRequestSystem` | the city's own request portal, or nothing | [step 7](#7-the-citys-request-system) |

The file also overrides `demoLogins`, which are the shared sign-ins the demo shows on its
sign-in screen. Those belong to the demo and not to Oceanside, so they go along with
everything else you replace. Without them the sign-in screen is just a normal one.

## 3. Departments and neighborhoods

These are the two lists every city has to supply. A `Department` is who issues get routed
to: a name, the categories it handles, and optionally a contact address. A `Neighborhood` is
an area an issue gets tagged with, and it names the council district it sits in.

**Ids are permanent.** Department and neighborhood ids get stored on every issue and merged
against on later backend pulls. Pick stable, readable ones (`dept-public-works`,
`nbhd-townsite`) and do not renumber them later.

**Council districts come from neighborhoods.** There is no separate list for them. Each
`Neighborhood` names its `councilDistrict`, and an issue's district is worked out from its
neighborhood when it gets submitted. That means a manager can be scoped to a district without
anyone ever tagging a report with one. If a neighborhood straddles two districts, leave
`councilDistrict` null instead of guessing. Issues there will not have a district, but a
citywide manager still sees them.

**Neighborhoods follow the profile.** Departments get written into the local store once, so
an admin's edits stick around. Neighborhoods do not have an editor, so every start replaces
them with whatever is in the profile. If you rename, add, or retire one, installs pick it up
with the next release.

### Neighborhood boundaries

If you give a `Neighborhood` a `boundaryGeoJson`, the map will outline it, let a tap inside
it select it, shade the selected ones, and frame their whole area (instead of just their
pins). The value is a GeoJSON geometry, meaning a `Polygon` or `MultiPolygon` and _not_ a
`Feature`, in longitude/latitude (WGS 84). If you leave it null, the neighborhood just does
not get drawn, and one that fails to parse gets skipped instead of breaking the map.

Most cities publish their neighborhood or planning-area polygons on an ArcGIS server, and any
ArcGIS layer will hand them over in the right shape. Oceanside's came from this query. It
reprojects to WGS 84, simplifies to about 5 m, and rounds to five decimal places, which keeps
all 17 areas to about 13 KB:

```
https://gis.oceansideca.org/gis/rest/services/WebService/Residential_Neighborhood/MapServer/3/query
    ?where=1%3D1&outFields=Name&outSR=4326&maxAllowableOffset=0.00005&geometryPrecision=5&f=geojson
```

Take each feature's `geometry` object as-is, and keep the strings out of the profile itself.
[`NeighborhoodBoundaries.kt`](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/NeighborhoodBoundaries.kt)
holds one constant per neighborhood, and the profile points at those. They ship inside the
app, so the map never depends on the city's server being up.

After that, say where they came from in `MapSettings.boundarySource`
([step 5](#5-the-map)). Official GIS data is usually public, but it rarely comes with a stated
license, so check the terms (or just ask the city's GIS team) before a public deployment uses
it. The same layer is also a good way to fill in `councilDistrict`. Overlay it on the city's
district map and see which neighborhoods fall inside a single district.

**The backend holds the same lists.** Issues refer to departments and neighborhoods by id, so
whatever stores your issues needs matching rows. On Supabase, `issues.department_id` is a
foreign key, so a report naming a department the table does not have gets rejected. Keep the
two in sync whenever either one changes.

## 4. Branding

`CityBranding` is what makes the app look like it belongs to the city, and not like a
template. It is a header photo, a logo, and a tagline.

**Images are fetched over the network, from script.** The web build loads them with `fetch`,
not with an `<img>` tag, so the host has to send CORS headers. A city's own CMS usually does
not, which is why the Oceanside logo is served from somewhere else. If you host copies you
have the rights to somewhere that allows cross-origin reads, they will work on all three
platforms.

Only set `logoIsSingleColor = true` if your logo is one flat color on a transparent
background, since the header draws it in white. A multi-color seal needs the default, which
puts it on a light plate.

## 5. The map

`MapSettings` takes the point the camera starts on, the zoom it starts at, and a basemap
style URL. Center it on your downtown and pick a zoom that shows most of the city.

If your neighborhoods have boundaries ([step 3](#neighborhood-boundaries)), set
`boundarySource` to a line saying where they came from. It shows up under the map, next to
the legend.

The default basemap is [OpenFreeMap](https://openfreemap.org/). It has real OpenStreetMap
detail, with no key and no signup. It is funded by donations, though, so if your deployment
ends up depending on it, you should donate or self-host. A [Protomaps](https://protomaps.com/)
`.pmtiles` extract on object storage behind a CDN is the cheapest long-term option.
`MapSettings` also has two other ready-made style constants if you want them.

## 6. Address lookup

**Geocoding is off unless you turn it on.** By default, the pin picker is the only way to
place an issue. With a geocoder, whatever a resident types resolves to a pin, and a pin they
drop resolves to an address.

The Oceanside combo, `deviceGeocoder() orElse NominatimPlatformGeocoder(...)`, is the one that
does not need an account on any target. It uses the phone's own geocoder on Android and iOS
and OpenStreetMap's public Nominatim in a browser. If you use Nominatim, please read the KDoc
on `NominatimPlatformGeocoder` first. The public instance is donated capacity with a usage
policy, the `userAgent` and `contactEmail` you pass in are how an admin would get in touch
with you, and a city with real traffic should run its own instance or use one of Compass's
keyed backends instead. Bound it with a `viewBox` of your city limits so "Main St" resolves
to _your_ Main St.

## 7. The city's request system

Most cities have some kind of request-a-service portal, and almost none of them have an API
for it.
[`CityRequestGateway.kt`](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/CityRequestGateway.kt)
is Oceanside's. It is a `ManualExternalRequestGateway` with a stable id, a display name, and
the URL a manager should open. That is enough for a manual hand-off; the manager files the
request on the city's site and then records the reference number in the app.

- The `systemId` gets saved on every `ExternalReference`, so it can never change. The display
  name can.
- If your city does have an API, implement `ExternalRequestGateway` directly instead of
  extending the manual base. Nothing above `data/remote` has to change.
- If there is no city system at all, leave `externalRequestSystem` unset, and the hand-off
  section disappears from the issue screen.

## 8. The backend

A backend is one object, a `BackendProvider` that exposes four interfaces. It is returned
from
[`ConfiguredBackend.kt`](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/ConfiguredBackend.kt)
and passed to `initKoin` at each entry point.

### Local-only, for now

Do nothing. `ConfiguredBackend` reports itself as unconfigured while its project details are
blank, so every API returns `NotConfigured`, sign-in falls back to the mock, and the app runs
end to end on the device. This is a real mode, not a broken one, and it is a pretty
reasonable place to be while you get the rest of the profile figured out.

### Supabase

This is what the demo runs on, and it is the backend that is already wired up in
`cityConfig`. [`supabase/README.md`](./supabase/README.md) walks through how the demo's project
was set up in the dashboard, step by step, and the table at the top says which of those steps
a city's project actually needs.

The backend itself is [`schema.sql`](./supabase/schema.sql), with the tables, the row-level
security policies, and the photo bucket. Two settings connect the app to the project. Put
them in `local.properties`, which is gitignored:

```properties
supabase.url=https://YOUR-PROJECT-REF.supabase.co
supabase.publishableKey=sb_publishable_YOUR_KEY
```

The build reads them through `cityConfig/build.gradle.kts`, checking the environment variable
first, then `-P`, then `local.properties`, so CI can set `SUPABASE_URL` and
`SUPABASE_PUBLISHABLE_KEY` as secrets. Neither value is actually a secret, though. The
publishable key is a public identifier, and what it can read and write is decided by the
policies in `schema.sql`. Never ship a `service_role` key; it skips every policy.

Load your departments and neighborhoods into their tables
([step 3](#3-departments-and-neighborhoods)), and then create some accounts. Roles come from
each account's `app_metadata`, which only a trusted server can write. (`user_metadata` would
let anyone make themselves an admin.) One admin is enough to get started, since every other
role can be assigned from inside the app.

### Something else

Implement `BackendProvider` and its four interfaces (`IssueBackendApi`,
`ReferenceDataBackendApi`, `PhotoBackendApi`, and `AuthService`) in your own module, and
return it from `ConfiguredBackend`. Nothing above `data/remote` has to change. For reference,
the [`supabase` package](./cityConfig/src/commonMain/kotlin/dev/helpmycity/cityconfig/supabase)
is about 600 lines total, row mapping included.

Here is what is worth knowing before you start. None of it comes from any particular
provider; it all comes from the sync engine.

- **Every write has to be idempotent.** The outbox replays, so if a push goes through but the
  acknowledgement gets lost, sending it again has to be harmless. Issue pushes are upserts
  keyed on the id, and whatever enforces permissions on your backend has to allow that
  _second_ attempt (an update), not only the insert.
- **The status-change table is append-only.** Rewriting history is the one thing an audit
  trail can never allow, so that push is an insert that ignores duplicates.
- **Return `NotConfigured`, not a failure,** when there is nothing to talk to. The engine
  treats it as "nothing to do" and keeps the queue.
- **Acknowledge what the backend stored,** and not what you sent, if a trigger or a policy can
  change a row on the way in.
- **Authentication is up to you.** `AuthService` only deals with accounts: sign in, sign up,
  continue as guest, and a `StateFlow` of the current one. A role and manager scope can come
  in as a claim on the account, which seeds a new user record and is never read again. If
  your provider does not have a claim like that, leave both null and let an admin assign
  roles in the app.
- **Restate the access rules where the data lives.**
  [`domain/access/IssueAccess.kt`](./shared/src/commonMain/kotlin/dev/helpmycity/domain/access/IssueAccess.kt)
  is short and easy to copy on purpose. An approved issue is public, and an issue in triage
  is only visible to the person who submitted it and the managers who can act on it. On the
  device that is just a definition, but on a server it has to actually be enforced.
  `supabase/schema.sql` is one way to write it.

## 9. The app's name and icons

The app's own name and identifiers are separate from the city profile:

| What | Where |
| --- | --- |
| Android label | `androidApp/src/main/res/values/strings.xml` |
| Android package and id | `namespace` and `applicationId` in `androidApp/build.gradle.kts` |
| iOS name and bundle id | `iosApp/Configuration/Config.xcconfig` (and `TEAM_ID` for signing) |
| Browser tab title | `webApp/src/jsMain/resources/index.html` |
| In-app name | `app_name` in `shared/src/commonMain/composeResources/values/strings.xml`, and `values-es/` |
| Launcher icons | Android: `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` (the adaptive layers) and the `res/mipmap-*` PNGs next to them. iOS: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` (light, dark, and tinted). Web: `webApp/src/jsMain/resources/favicon.svg`, `favicon-32.png`, and `apple-touch-icon.png`, linked from `index.html` |

Renaming the Kotlin package (`dev.helpmycity`) is optional, and pretty mechanical if you do
it. The only place that names it as a string is the Koin `@ComponentScan` root in
`di/AppModule.kt`. The generated Compose resources package
(`helpmycity.shared.generated.resources`) comes from the Gradle project names instead, and
`compose.resources { packageOfResClass = ... }` can move it.

## 10. Hosting the web build

```bash
./gradlew :webApp:jsBrowserDistribution
```

The bundle ends up in `webApp/build/dist/js/productionExecutable`. It is all static files,
but they are _not_ quite ordinary static files. The local database is SQLite WASM in OPFS,
which will only open on a cross-origin-isolated page, so whatever serves the build has to
send:

```
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
```

The dev server sets them in `webApp/webpack.config.d/webpack.config.js`. If they are missing
in production, the app loads and then fails to open its database, which is a fun one to
debug.

Two other things are worth checking before a real launch. The MapLibre GL JS worker gets
served from your own origin as `maplibre-gl-worker.mjs`
(`webApp/webpack.config.d/maplibreWorker.js` copies it next to the bundle), so make sure your
host serves `.mjs` files with a JavaScript content type. And the branding images from
[step 4](#4-branding) have to be reachable with CORS headers from whatever domain you serve
the app on.

## Adding a language

Copy `shared/src/commonMain/composeResources/values/strings.xml` to
`values-<code>/strings.xml` and translate the values. That file is everything; every string a
user can see, enum labels included, is a resource, and `ui/Labels.kt` maps them. No code
changes needed.

## Checklist

- [ ] `ConfiguredCityProfile` has your city in it: name, departments, and neighborhoods with
      stable ids, and none of Oceanside's stuff left over
- [ ] Departments and neighborhoods have matching rows in the backend
- [ ] Branding images are hosted somewhere that sends CORS headers
- [ ] Map center, zoom, and a basemap you are comfortable depending on
- [ ] Neighborhood boundaries, if you have them, credited in `boundarySource`
- [ ] A geocoder picked, or left off on purpose
- [ ] `CityRequestGateway` pointed at the city's portal, or removed
- [ ] A backend picked, and the access rules restated wherever the data lives
- [ ] App name, ids, and icons replaced
- [ ] Web host sending COOP/COEP
- [ ] `./gradlew :shared:testAndroidHostTest` passing
