# Chess Reviews

An Android chess app (Kotlin, Jetpack Compose): games from Chess.com and Lichess, Stockfish-powered review and analysis, and training from your own mistakes. Published on Google Play by WRK Labs.

## Building

- `./gradlew assembleDebug`
- The Stockfish engine is not in the repository (each build is about 100 MB). Put a Stockfish 19 build, renamed `libstockfish.so`, in `app/src/main/jniLibs/<abi>/` for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- Release signing reads `key.properties` and the keystore it names, neither of which is committed.

## Credits

Chess Reviews is a derivative of [Rookahead](https://github.com/rookahead/rookahead) by bijoyv9 and contributors (GPL-3.0-or-later). The move-grading code (`classifier/`) is ported from Rookahead's `src/lib/classify.ts` from TypeScript to Kotlin, the Train puzzles made from your own mistakes follow Rookahead's `src/lib/mistakes.ts`. Changes include the Android UI, on-device Stockfish, local storage, Play Billing and the review limit. See [`NOTICE`](NOTICE).

## Licence

Chess Reviews is free software, released under the [GNU GPL v3 or later](LICENSE). It bundles Stockfish (GPL-3.0) and the Lichess move sounds (GPL/AGPL). Credits for everything it builds on are listed in the app under Settings > About > Open-source licences; keep that list in step with `ui/LicencesSheet.kt`, `NOTICE` and `SOUNDS.md`.
