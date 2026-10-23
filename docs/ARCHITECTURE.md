# Architecture

The first implementation uses four populated modules rather than the complete future module tree:

```text
app
  -> core:data
       -> core:database
            -> core:model
  -> core:database
  -> core:model
```

- `core:model` owns Android-free IDs, immutable domain models, contracts, state machines, and deterministic policies.
- `core:database` owns Room entities, DAOs, v1-v5 exported schemas, explicit migrations, and persistence transactions.
- `core:data` owns source contracts, SAF enumeration, staged fingerprints, reconciliation, deterministic fixtures, and mapping between database and domain records.
- `app` owns activities, Compose features, Hilt wiring, Room-backed feature repositories, WorkManager scheduling, session orchestration, local save snapshots, display/HOME/package Android APIs, launcher intent construction, and user-facing diagnostics.

The future `platform:*`, `integration:*`, and `feature:*` boundaries are retained as package names. A package is extracted into another Gradle module only after it owns meaningful production code and tests.

## Process and display topology

`HomeActivity` is both a normal launcher entry and a HOME-role candidate. `CompanionActivity` is explicit and non-exported. The display coordinator discovers all current displays, prefers a same-firmware user-verified assignment, otherwise treats the default display as an upper candidate and a dynamic active non-default display as a lower candidate. One active display produces the adaptive single-display shell.

An `ActivityOptions.setLaunchDisplayId` request and an observed physical panel are separate evidence. Game Hub can observe the display of its own activities. Arbitrary external-game placement remains a hardware/manual result.

## Data ownership

Room stores game identity, versions, installations, launch methods, sessions, notes, collections, roots, fingerprints, artwork provenance, save profiles/snapshots, and diagnostic evidence. URI strings are stored only for explicit user grants; exports redact them. Missing files or apps mark an Installation unavailable and never erase the Game or its history.

Immediate SAF import is user initiated. A constrained unique periodic WorkManager job reuses the same incremental source and reconciliation policy; it does not crawl outside persisted grants. The active sync transport is local-only and performs no network operation.

## Dependency injection

Hilt provides compile-time application-scoped dependencies at Android boundaries. Core policies remain constructor-injectable and testable without Hilt.

## Safety boundaries

- Standard flavor uses public Android APIs, SAF, explicit intents, and local data.
- Enhanced flavor currently exposes only the build-time capability boundary. Its `PrivilegeBridge` must remain unsupported until separately implemented and reviewed.
- `End Session` closes local Game Hub state. External-process exit is a separate adapter result and may remain unsupported.
- Snapshot creation reads only an explicitly granted save tree, streams through app-private temporary storage, hashes a manifest, and commits atomically. Restore is manual, requires a pre-restore snapshot, blocks during an open session, and verifies hashes.
- Screen/display callbacks are logged as observed signals; `LidSignalProvider` remains `UNKNOWN` until hardware evidence exists.
- Screenshot capture is not enabled until a MediaProjection hardware spike verifies the Thor capture target and current foreground-service requirements.
