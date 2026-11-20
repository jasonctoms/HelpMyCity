<p align="center">
  <img src="shared/src/commonMain/composeResources/drawable/helpy.webp"
       alt="Helpy, the HelpMyCity mascot: a smiling traffic cone" width="180">
</p>

<h1 align="center">HelpMyCity</h1>

HelpMyCity is an open source issue tracker that any city can fork and run! Written to be very
easy to configure and maintain, and an upgrade to the old school whitelabel solutions
that nobody enjoys using.

A resident reports a problem (a pothole, a dead streetlight, a blocked sidewalk ramp), a
community manager triages it, and then everyone can follow along until it gets fixed. I built
it for cities that lean on volunteers, where per-seat pricing gets expensive fast and the city's
own request system is not really somewhere a resident can go and watch their report.

It is one Kotlin Multiplatform and Compose Multiplatform codebase that runs on Android, iOS,
and the web. Everything is stored locally first, so it still works when you have no signal,
and every decision that is specific to a city or a backend lives in one module you can swap
out. If you want to fork it for your own town, that module is the only thing you should have
to edit.

- [CITY_CONFIG.md](./CITY_CONFIG.md) covers everything a city configures: its profile, map,
  request system, backend, and app identity.
- [ARCHITECTURE.md](./ARCHITECTURE.md) is how it is built and why, plus the constraints you
  will want to know about before you change any of it.

The module in this repo is set up for the HelpMyCity demo at
[helpmycity.dev](https://helpmycity.dev). That is Oceanside, CA, running on Supabase, with some
shared accounts on the sign-in screen and a dataset that resets every night. If you are curious
how that backend was set up, it is all in [supabase/](./supabase).

**Contents:** [Three roles](#three-roles) · [The life of a report](#the-life-of-a-report) ·
[Features](#features) · [Running it](#running-it) · [Status](#status)

## Three roles

| Role | Can do |
| --- | --- |
| Resident | File a report (no account needed), browse and search approved issues, see them on the map, add "same problem here" support, and follow their own report through triage, including reading why it got turned down. |
| Manager | Everything a resident can do, plus a Review tab with everything waiting on triage in their areas. They can approve, reject with a reason, fix up a report before approving it, change its status, and record the city's own reference number. A manager's areas can be any mix of citywide, council districts, neighborhoods, and departments. |
| Admin | Everything, everywhere, plus the people screen. Admins can give anyone a role and give a manager the districts, neighborhoods, and departments they are responsible for. They can also export every issue as a CSV file. |

## The life of a report

1. **Filed.** Anyone can submit a location, category, description, requested action,
   priority, department, and optionally some photos and contact details. Before they hit
   submit, the form points out any near-duplicates in the same category and place, so they can
   support one of those instead of reporting it again.
2. **In review.** It is _not_ public yet. Only the person who submitted it and the managers
   responsible for that area can see it. That is kind of the whole point of triage; a public
   map full of unverified reports is worth a lot less than a short queue.
3. **Approved or rejected.** A responsible manager either approves it, which moves it to Open,
   or rejects it _with a reason_. A rejected report stays visible to the submitter and their
   managers on a separate Rejected reports page (you get there from the profile or the Review
   tab), but it never shows up on the list, board, or map. The decision, who made it, and when
   all get recorded on the issue and exported with it.
4. **Tracked.** Approved issues show up in the list, on the kanban board, and on the map.
   Managers move them from Open to In Progress to Complete, and completing one asks for a note
   about how it was resolved, which everyone can see. Every change is kept as history on the
   issue.
5. **Handed off to the city.** When an issue needs official action, a manager files it in the
   city's own request system and records the reference number here. That way this app is
   still a reliable system of record, even when the city's tracking is...less reliable.

## Features

### Reporting

- **No account required.** The report form is the quickest way into the app. Signing in is
  for managers, and for residents who want to keep tabs on their own reports.
- **Structured, but short:** category, priority, description, requested action, department,
  location, and optionally a name, email, and phone number.
- **Near-duplicate nudge.** While someone is filling out the form, anything already reported
  in the same category and place gets offered up instead, with a "same problem here" count
  they can add to.
- **Photos** come from the system picker. They are stored with the report, uploaded when it
  syncs, and shown on the issue on every device that can see it.
- **Location by description or by pin.** Geocoding keeps the two in step; whatever a resident
  types resolves to an approximate pin, and a pin they drop resolves back to an address.
  Neither one overwrites the other, though. A dropped pin is more precise, and what a
  resident actually wrote is more useful than whatever a geocoder thinks the place is called.
- **The council district is worked out for you.** The resident is never asked for it, so a
  report still gets to the right district manager even if they have no idea which district
  they live in (most people don't).

### Triage and review

- **Nothing is public until a manager approves it.** A new report is only visible to whoever
  filed it and the managers responsible for that area.
- **A Review tab** with exactly what is waiting on triage in a manager's areas.
- **Rejecting needs a reason**, and the submitter gets to read it. That rule lives in the data
  itself (`IssueReview` will not hold a rejection without one), so it is not just a disabled
  button somebody can find a way around.
- **Edit for completeness.** A manager can fix an issue's fields before approving it. The edit
  gets stamped on the issue, along with which fields changed, and everyone who can see the
  issue can see that too, submitter included.
- **Status changes come with a note**, and the note is added to the issue's history in the
  same step, so the history can never drift away from the current status.
- **A place for the city's reference number**, plus the date it was submitted and the date
  someone last checked on it. Municipal queues are worth keeping track of; they are just not
  worth trusting.

### Browsing

- **List** with a search box over an issue's text and the four status filters.
- **Kanban board** grouped by status, using the same query and filters as the list.
- **Map** of every issue that has a location, with markers colored by status. You can filter
  by neighborhood and tap a marker to open it. If the city supplies neighborhood boundaries,
  those get outlined too; tapping one filters to it, the filtered ones get shaded, and the
  camera zooms to fit them.
- **A side-by-side workspace** on anything bigger than a phone, with the list and the map at
  the same time and the tabs and report button up in the header.
- **Issue detail** shows everything on the report: photos, full status history, support
  count, the review decision and reason, anything a manager changed, and the city hand-off.

### People

- **A profile behind the avatar in the top bar.** It shows who you are, what your role lets
  you do, and the areas you are responsible for. It also has a form for your own name and
  address, CSV export if you are an admin, and the sign-out button.
- **People and roles**, for admins. This lists everyone the deployment has seen, each with a
  role picker and the four area pickers that make up a manager's scope. Nobody can change
  their own role, so the last admin can't accidentally lock everyone out.
- **Continue as a guest**, for a resident who just wants to report and browse with no account
  at all. The demo hides this (and sign-up) and offers its shared accounts instead.

### Under the hood

- **Offline-first on every platform.** Reads come from a local SQLite database (Room 3, using
  SQLite on Android and iOS and SQLite WASM in OPFS on the web). Writes land there first and
  get queued up for the backend, so the app behaves the same in a dead zone as it does on
  wifi.
- **A sync engine that drains an outbox.** It retries what can be retried, and "no backend
  configured" is treated as a normal state instead of an error. If nothing is configured, the
  queue just keeps growing.
- **One replaceable module for the city and the backend.** `:cityConfig` has a `CityProfile`
  (departments, neighborhoods, branding, map, geocoder, and the city's request system) and a
  `BackendProvider`. Nothing else in the repo knows about a specific city or cloud.
- **A worked backend example.** Supabase handles Postgres, auth, and photo storage, and the
  schema and row-level security are in [supabase/](./supabase) next to the demo's seed data
  and nightly reset. Any other backend is just four small interfaces.
- **English and Spanish everywhere.** Every string a user can see is a Compose resource, enum
  labels included.
- **The city's own look.** A header photo, logo, and tagline all come from the city profile,
  so the app looks like it belongs to the city instead of looking like a template.
- **CSV export of every issue**, so a deployment never gets locked in. An admin can export
  from their profile, which opens a save dialog on Android and iOS and downloads the file on
  the web. The file has every issue synced to that device, including reporters' contact
  details, which is why only admins get the button.

## Running it

You need JDK 17 or newer, and the Android SDK if you are building for Android. That's it; a
fresh clone runs local-only with Oceanside's departments and neighborhoods and a mock sign-in.

In a browser:

```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

On Android:

```bash
./gradlew :androidApp:assembleDebug
```

For iOS, open [iosApp/](./iosApp) in Xcode and run it. Xcode builds the framework from the
`:iosApp` Gradle module next to it.

The tests:

```bash
./gradlew :shared:testAndroidHostTest
```

To try out the admin screens, sign in as `admin@example.com` with any password that is at
least eight characters. The mock provider picks a role from the part of the address before
the @, so `resident@…` is a resident and anything else is a citywide manager. The three
accounts on the sign-in screen belong to the demo. They are published on purpose, and they
are part of the demo's configuration, not the app.

If you want to configure your own city and set up a backend, head over to
[CITY_CONFIG.md](./CITY_CONFIG.md).

## Status

Android, iOS, and the web all work. The board is read-only for now and there are no dashboard
charts yet. The full list, and what each one would take, is in
[ARCHITECTURE.md § Known gaps](./ARCHITECTURE.md#known-gaps).
