# Introduction
this project is minecraft multiplayer platform, player can upload modpacks to create a host that can invite friends to play together
# Repository Guidelines

## Project Structure & Module Organization
- `client/ui/`: Desktop launcher/UI (Compose for Desktop), game install/play logic, and UI screens/components.
- `common/`: Shared models, network helpers, parsing services, and cross-module utilities.
- `server/master/`: Main backend (Ktor + Mongo + Docker orchestration).
- `server/proxy/`: Proxy service module.
- Source layout is standard Gradle Kotlin:
  - `src/main/kotlin`, `src/main/resources`
  - tests in `src/test/kotlin`.
- UI assets/icons are under `client/ui/src/main/resources/assets`.
- when you reads or writes file content via powershell, use absolute path to avoid permission issues.
- use CircleIconButton as more as possible when you are making buttons. do not forget @OptIn(ExperimentalMaterial3Api::class)
- if you wanna remove any files, you can replace content to empty instead, if failed, tell me
- use Syntactic sugar as more as possible
- reply in mandarin，but add english notation for terminologies, no matter how input language is
## Build, Test, and Development Commands
if you are an ai you are not needed to execute gradle commands unless explictly declared
Run commands inside each module directory (there is no repo-root wrapper).

- Client UI:
  - `cd client/ui && .\gradlew.bat run` — start desktop app.
  - `cd client/ui && .\gradlew.bat compileKotlin` — compile check.
  - `cd client/ui && .\gradlew.bat test` — run UI module tests.
- Common:
  - `cd common && .\gradlew.bat build` — compile + package shared library.
- Server master:
  - `cd server/master && .\gradlew.bat run` — run backend locally.
  - `cd server/master && .\gradlew.bat buildFatJar` — build deployable jar.
- Server proxy:
  - `cd server/proxy && .\gradlew.bat build` — build proxy artifacts.

## Coding Style & Naming Conventions
- Kotlin, 4-space indentation, UTF-8.
- Types/files: `PascalCase`; functions/vars: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Compose screens use `*Screen.kt`; reusable widgets use `*Card.kt`, `*Button.kt`, etc.
- Keep shared DTO/model changes in `common` first, then adapt client/server callers.
- no need to add spaces between chinese characters and numbers,letters. e.g. Mod数量8个 is ok, Mod 数量 8 个 is not ok
- do not use file.deleteRecursively() function, use file.deleteRecursivelyNoSymlink() instead (extension from calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink), because it will delete the symlink target on windows
## Testing Guidelines
- Frameworks: Kotlin test + JUnit Platform.
- Test file naming: `*Test.kt` (examples: `HostTest.kt`, `ModpackTest.kt`).
- Prefer focused unit tests for parsing/mapping and service logic; avoid network-dependent tests unless mocked.
- Note: some server/proxy test tasks are disabled in Gradle; enable when adding/maintaining those tests.

## Commit & Pull Request Guidelines
- Use short, imperative commit titles (history style is task-oriented: “fix …”, “migrate …”, “complete …”).
- Suggested format: `[module] action summary` (e.g., `[client/ui] fix forge library fallback`).
- PRs should include:
  - what changed and why,
  - affected modules (`client/ui`, `common`, `server/master`, `server/proxy`),
  - verification steps/commands,
  - screenshots for UI changes,
  - migration notes for model/API changes.

## Security & Configuration Tips
- Do not commit secrets/tokens/local machine paths.
- Keep environment-specific settings in module `resources/config.toml` or runtime config, not hardcoded.
- Validate user/file/network inputs in both client and server paths when adding features.
