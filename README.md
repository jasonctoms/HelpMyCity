<p align="center">
  <img src="shared/src/commonMain/composeResources/drawable/helpy.webp"
       alt="Helpy, the HelpMyCity mascot: a smiling traffic cone" width="180">
</p>

<h1 align="center">HelpMyCity</h1>

A neighborhood issue tracker any city can run. A resident reports a problem — a
pothole, a dead streetlight, a blocked sidewalk ramp — a community manager
triages it, and everyone can follow it until it is fixed. It is built for cities
delegating to volunteers, where per-seat spreadsheet pricing does not scale and
the city's own request system is not somewhere residents can watch their report.

One Kotlin Multiplatform + Compose Multiplatform codebase runs on **Android, iOS
and the web**, stores everything locally first so it works with no signal, and
keeps every city-specific and backend-specific decision in one replaceable
module. Forking it for your own town means editing that module and nothing else.

- [**CITY_CONFIG.md**](./CITY_CONFIG.md) — everything a city configures: its
  profile, map, request system, backend and app identity.
- [**ARCHITECTURE.md**](./ARCHITECTURE.md) — how it is built and why, including
  the constraints worth knowing before changing any of it.

The module in this repo configures the HelpMyCity demo at
[helpmycity.dev](https://helpmycity.dev): Oceanside, CA, on Supabase, with shared
sign-ins on the sign-in screen and a dataset that resets nightly.
[supabase/](./supabase) is how its backend was set up.

**Contents** — [Three roles](#three-roles) · [The life of a report](#the-life-of-a-report) ·
[Features](#features) · [Running it](#running-it) · [Status](#status)

## Three roles

| Role | Can do |
| --- | --- |
| **Resident** | File a report (no account needed), browse and search approved issues, see them on the map, add "same problem here" support, and follow their own report through triage — including reading why it was turned down. |
| **Manager** | Everything a resident can, plus a Review tab of everything awaiting triage in their areas: approve, reject with a reason, correct a report before approving it, change status, and record the city's own reference number. A manager's areas are any mix of citywide, council districts, neighborhoods and departments. |
| **Admin** | Everything, everywhere, plus the people screen: give anyone a role, and give a manager the districts, neighborhoods and departments they answer for. Admins can also export every issue as a CSV file. |

## The life of a report

1. **Filed.** Anyone submits location, category, description, requested action,
   priority, department, optional photos and optional contact details. Before
   they submit, the form nudges them about near-duplicates in the same category
   and place, which they can support instead of re-reporting.
2. **In review.** It is *not* public yet. Only the submitter and the
   managers responsible for that area can see it. That is the point of the
   triage step: a public map of unverified reports is worth less than a short
   queue.
3. **Approved or rejected.** A responsible manager approves it, which moves it
   to Open, or rejects it *with a reason*. A rejected report stays visible to
   its submitter and its managers, on a separate Rejected reports page reached
   from the profile and the Review tab, and never on the list, board or map. The decision,
   who made it and when are recorded on the issue and exported with it.
4. **Tracked.** Approved issues appear in the list, on the kanban board and on
   the map. Managers move them from Open to In Progress to Complete, and
   completing one takes a note of how it was resolved, which everyone can see.
   Every change is kept as history on the issue.
5. **Handed off to the city.** When an issue needs official action, a manager
   files it in the city's own request system and records the reference number
   here, so this app stays the reliable system of record even when the city's
   tracking is not.

## Features

### Reporting

- **No account required.** The report form is the shortest path into the app, and
  signing in is for managers and for residents who want to follow their own
  reports.
- **Structured but short:** category, priority, description, requested action,
  department, location, and optional name, email and phone.
- **Near-duplicate nudge.** While the form is being filled in, anything already
  reported in the same category and place is offered instead, with a
  "same problem here" count to pile onto.
- **Photos** from the system picker, stored with the report, uploaded when the
  report syncs and shown on the issue on every device that can see it.
- **Location by description or by pin**, kept in step by geocoding: what a
  resident types resolves to an approximate pin, and a pin they drop resolves
  back to an address. Neither overwrites the other — a dropped pin is the more
  precise answer, and what a resident wrote outranks what a geocoder would call
  the place.
- **The council district is derived**, not asked for, so a report reaches the
  right district manager without the resident knowing which district they are in.

### Triage and review

- **Nothing is public until a manager approves it.** A new report is visible only
  to the person who filed it and to the managers responsible for that area.
- **A Review tab** holding exactly what is awaiting triage in a manager's areas.
- **Rejection requires a reason**, and the submitter reads it. The rule lives in
  the data — `IssueReview` refuses to hold a rejection without one — so it is not
  a disabled button someone can route around.
- **Edit for completeness.** A manager can correct an issue's own fields before
  approving it. The correction is stamped on the issue and shown to everyone who
  can see it, submitter included, along with which fields changed.
- **Status changes with a note**, appended to the issue's history in the same
  step, so history can never drift from the current status.
- **A place for the city's reference number**, with the date it was submitted and
  the date it was last checked, because municipal queues are worth recording and
  not worth trusting.

### Browsing

- **List** with a search box over an issue's text and the four status filters.
- **Kanban board** grouped by status, using the same query and the same filters
  as the list.
- **Map** of every located issue, markers colored by status, filterable by
  neighborhood, tap to open.
- **A side-by-side workspace** on anything wider than a phone: the list and the
  map at once, with the tabs and the report button in the header.
- **Issue detail:** everything on the report, its photos, its full status
  history, its support count, the review decision and reason, what a manager
  changed, and the city hand-off.

### People

- **A profile behind the avatar in the top bar:** who you are, what your role
  lets you do, the areas you answer for, a form for your own name and address,
  CSV export for an admin, and the way out.
- **People and roles**, for admins: everyone this deployment has seen, each with
  a role picker and the four area pickers that make up a manager's scope. Nobody
  can change their own role, so the last admin cannot lock everyone out.
- **Continue as a guest**, for a resident who wants to report and browse without
  an account at all. The demo hides it, along with sign-up, and offers its
  shared accounts instead.

### Under the hood

- **Offline-first on every target.** Reads come from a local SQLite database
  (Room 3 — SQLite on Android and iOS, SQLite WASM in OPFS on the web), writes
  land there first and are queued for the backend. The app behaves the same in a
  dead zone as on wifi.
- **A sync engine that drains an outbox**, retries what is retryable, and treats
  "no backend configured" as a state rather than an error — with nothing
  configured the queue simply builds.
- **One replaceable module holds the city and the backend.** `:cityConfig`
  carries a `CityProfile` (departments, neighborhoods, branding, map, geocoder,
  the city's request system) and a `BackendProvider`. Nothing else in the repo
  names a city or a cloud.
- **A worked backend example.** Supabase — Postgres, auth and photo storage —
  with schema and row-level security in [supabase/](./supabase), beside the
  demo's seed data and daily reset. Any other backend is four small interfaces.
- **English and Spanish throughout**, with every user-visible string a Compose
  resource, including every enum label.
- **A city's own face:** a header photograph, its logo and a tagline come from
  the city profile, so the app looks like the city's rather than like a template.
- **CSV export of every issue**, so a deployment is never locked in. An admin
  exports from their profile: a save dialog on Android and iOS, a download on
  the web. The file holds every issue synced to that device, reporter contact
  details included, which is why only admins get the button.

## Running it

Requires JDK 17+ and, for Android, the Android SDK. Nothing else needs
configuring — a fresh clone runs local-only, with Oceanside's departments and
neighborhoods and a mock sign-in.

In a browser:

```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

On Android:

```bash
./gradlew :androidApp:assembleDebug
```

For iOS, open [iosApp/](./iosApp) in Xcode and run; Xcode builds the framework
from the `:iosApp` Gradle module beside it.

The tests:

```bash
./gradlew :shared:testAndroidHostTest
```

Sign in as `admin@example.com`, with any password of eight characters or more,
to try the admin screens. The mock provider reads a role from the address's
local part: `resident@…` is a resident, and anything else is a citywide
manager. The three accounts the sign-in screen offers are the demo's own,
published deliberately, and belong to its configuration rather than to the app.

See [CITY_CONFIG.md](./CITY_CONFIG.md) for configuring your own city and
setting up a backend.

## Status

Android, iOS and the web all run. The board is read-only and there are no
dashboard charts. The full list, with what each one would take, is in
[ARCHITECTURE.md § Known gaps](./ARCHITECTURE.md#known-gaps).
