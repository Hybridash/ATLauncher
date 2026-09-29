# Hybrid (ATLauncher fork)

This is an unofficial fork of [ATLauncher](https://github.com/ATLauncher/ATLauncher) that adds a few features for playing with mods. It is **not** made or supported by the ATLauncher team, so please don't send them bug reports about it. Report problems [here](https://github.com/Hybridash/ATLauncher/issues) instead.

## What's added

Each feature can be turned on or off in **Settings → Hybrid**.

| Feature | What it does | Default |
|---|---|---|
| **World backups** | Before each launch, any world that changed since its last backup is zipped into `<instance>/backups/worlds/<world>/`. The newest 5 backups of each world are kept. To roll a world back, use **Backup → Restore World Backup...** on the instance. The current version of the world is kept as a copy, never deleted. | On |
| **Crash explainer** | When the game crashes, a short "What went wrong?" section is added to the console, and a popup shows it too. It covers common causes: missing or wrong-version mods, the wrong Java version, running out of memory, broken mixins, graphics driver problems, and the mod a crash report points to. Each cause comes with a fix. | On |
| **Mod checker** | Before launch, it warns in the console about the same mod installed twice, mods known to break each other (for example OptiFine + Sodium), and required mods that seem to be missing. It only warns and never stops the game from starting. | On |
| **RAM advice** | Suggests a maximum memory setting based on how many mods you have and how much RAM your computer has. | On |
| **Shared keybinds and servers** | Uses the same keybinds and multiplayer server list in every instance. When a game closes, they are saved, and the next instance you launch gets them. Video and sound settings stay separate for each instance. | Off |

ATLauncher's own features, like full instance backups and playtime tracking, work as before.

## Differences from ATLauncher

- The window title says it's an unofficial fork.
- Self-update is turned off, so it never replaces itself with official ATLauncher.
- Crash reports are not sent to ATLauncher's error tracker, and analytics is off by default.
- It uses the same data folder layout as ATLauncher.

## Downloads

Every push to `main` is built automatically. Open the [Actions tab](https://github.com/Hybridash/ATLauncher/actions), click the latest successful **Application** run, and download **Hybrid-ATLauncher** from **Artifacts** at the bottom. It contains a `.jar` (runs anywhere with Java), a Windows `.exe`, and a `.zip`. You need to be signed in to GitHub to download it.

## Building

You need Java 8 or newer. Then run:

```sh
./gradlew build
```

The files end up in `dist/`. To run the tests, use `./gradlew test`.

The new code is in `src/main/java/com/atlauncher/hybrid/`, with tests in `src/test/java/com/atlauncher/hybrid/`.

## Please read before sharing

ATLauncher's license (GPL-3.0) allows forks like this one. However, the ATLauncher team asks that other launchers not use their CDN, files or modpacks. This fork still downloads packs and data from ATLauncher's servers, the same way ATLauncher does. That's fine for trying things out yourself, but don't publish it as a launcher for other people without changing that. The fork also still uses ATLauncher's CurseForge API key.

## License

This work is licensed under the GNU General Public License v3.0, like ATLauncher. Credit for the launcher goes to the ATLauncher team and contributors.
