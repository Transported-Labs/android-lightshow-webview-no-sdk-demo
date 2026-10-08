# android-lightshow-webview-no-sdk-demo

Write all documentation and all files in English.

Android demo app (Kotlin, Gradle, single `:app` module, package `com.cueaudio.nosdkdemo`) that opens a URL or the bundled `app/src/main/assets/index.html` in a WebView and exposes native torch, vibration and permission calls to the page through the `cueSDK` JavaScript interface (`WebViewLink.kt`).

## Repo etiquette

- Branch off `main` (the main branch); merge feature branches back into it.
- Conventional-commit subjects (`feat:`, `fix:`, `refactor:`).
- Commit or push only when asked.

## Core Principle: KISS

Keep it simple. Always reach for the smallest solution that solves the problem in front of you.

- No speculative abstraction, no premature generalization, no feature you weren't asked for (YAGNI).
- When code repeats, prefer duplication over an abstraction that adds coupling — wait until a pattern is proven before extracting it.
- Fewer moving parts beats clever. Optimize for the next person reading the code, not for the fewest lines.
- Match the surrounding code's idioms, naming, and structure rather than introducing a new style.

## Code Comments

Code explains itself through naming and structure. Default to **no comments**. Add one only when WHY is genuinely non-obvious (a hidden constraint, a bug workaround, a subtle invariant) — never WHAT. Keep it to **one short line**; no multi-paragraph or docstring-style blocks. Applies to every language.

## Testing

- Every code change carries tests for the new and modified behavior — success and error paths.
- Test behavior, not implementation details.
- Prefer real dependencies over mocks so tests exercise the actual integration.
- Local JVM unit tests (JUnit 4) live in `app/src/test`; instrumented tests (AndroidX Test, Espresso) live in `app/src/androidTest` and need a device or emulator.

## Linters & Cleanup

Always fix **every** problem you discover in the repo — lint warnings, failing tests, red CI, stale docs — even pre-existing and unrelated. Land it as a focused sibling change in the same session.

Always run Android lint (`./gradlew lint`), unit tests (`./gradlew test`) and the build (`./gradlew assembleDebug`, which also compiles and type-checks the Kotlin) after any change, plus `./gradlew connectedAndroidTest` when a device or emulator is available, and fix every issue they report before considering the work done.

## CI

After every push to `main`, watch the triggered build to completion and report the result — never push and walk away.
