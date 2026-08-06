# Introduction
this project is minecraft multiplayer platform, player can upload modpacks to create a host that can invite friends to play together
# Repository Guidelines
no need do any git commands unless explictly declared
unless i explictly express agree to change code, otherwise you should not change code.
when you wanna change the code, you should gimme plan
## Project Structure & Module Organization
- `client/ui/`: Desktop launcher/UI (Compose for Desktop), game install/play logic, and UI screens/components.
- `common/`: Shared models, network helpers, parsing services, and cross-module utilities.
- `server/master/`: Main backend (Ktor + Mongo + Docker orchestration).
- `server/proxy/`: Proxy service module.
- Source layout is standard Gradle Kotlin:
  - `src/main/kotlin`, `src/main/resources`
  - tests in `src/test/kotlin`.
- UI assets/icons are under `client/ui/assets/src/main/resources/assets`.
- use CircleIconButton as more as possible when you are making buttons.
- do not remove any project files, for any dir or file to be removed, move to /DEL dir instead.
- use Material3
- hide technological details to user if possible, such as we dont need to let user know what's docker container, this situation use host instead.
- you can read minecraft source code on "client\mc\1.21.1-neoforge\build\moddev\artifacts\neoforge-${neoforge-version}-sources" for 1.21.1
- for models with uuid needed, use uuidv7
## Build, Test, and Development Commands
no need to execute gradle commands unless explictly declared
no need execute javac i wanna execute it manually and tell you error 
no need care about CRLF/LF issue, but Dockerfile and *.sh files must be LF
- on wsl, when you wanna read gradle cache, go windows host to read, not read ~
## Coding Style & Naming Conventions
- Kotlin, 4-space indentation, UTF-8.
- Types/files: `PascalCase`; functions/vars: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Compose screens use `*Screen.kt`; reusable widgets use `*Card.kt`, `*Button.kt`, etc.
- Keep shared DTO/model changes in `common` first, then adapt client/server callers.
- no need to add spaces between chinese characters and numbers,letters. e.g. Mod数量8个 is ok, Mod 数量 8 个 is not ok
- when using kotlin string template feature, if there's variable called abc next to chinese e.g. "测试$abc测试测试", this situation entire abc测试测试 will be parsed as a variable making compile fail, we should make abc bracketed "测试${abc}测试测试"
- for kotlin code, if a function is possible to fail e.g. disk io/network io,use Result<> as return type,the invoker should use runCatching-getOrElse/getOrThrow, reduce use getOrNull, explictly log the exception, or throw it by situation
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
