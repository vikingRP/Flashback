# Replay protocol smoke test

Requires a Java 17 JDK selected by `JAVA_HOME` or available on `PATH`.
From PowerShell, run:

```powershell
./tools/replay-protocol-smoke/run.ps1
```

The runner compiles the mod and exports the Gradle runtime classpath, then runs
22 assertions against the real Minecraft 1.20.1 packet classes. It checks tick
rate payload serialization, deferred custom payload buffer ownership, protocol 763 world-time serialization, freeze easing,
snapshot/action framing and seeking, chunk deduplication including heightmaps, and preservation of Forge server configs and
locks when a replay snapshot clears old world data.
It does not require a display, Minecraft account, or running client.

Use `-SkipCompile` after `gradlew compileJava writeMigrationClasspath` to reuse a
current build. These checks do not replace recording/playback/export tests in a
running Forge client.

Five additional tests require Forge's transformed runtime and run during client setup with:

```powershell
./gradlew.bat runClient -PreplaySmoke
```

These validate registry content change detection across reconstructed snapshots and
network round-tripping of a viewer Login with a changed dimension definition,
while retaining the camera player's ID and spectator mode. The optional test source
set is excluded from release artifacts.
