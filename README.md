<h1 align="center">Flashback</h1>

Copyright 2024 Moulberry. Do not reupload or redistribute.

Flashback is a Minecraft mod which allows you to record your Minecraft gameplay, play it back and create stunning cinematic shots

## Forge 1.20.1 migration branch

This branch ports Flashback **0.43.6** to **Minecraft 1.20.1**, **Forge 47.4.10**
and **Java 17**. Feature parity and in-game validation are still in progress.
See [the migration tracker](MIGRATION_1.20.1_FORGE.md) for verified results and
remaining checks. Recordings use the Minecraft 1.20.1 protocol; newer Minecraft
recordings cannot be read as 1.20.1 packets.

### Build and run locally

Set `JAVA_HOME` to a Java 17 JDK, then run:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

The main artifact is `build/libs/flashback-0.43.6.jar`. It includes the native
export libraries and nested Forge mixin dependencies. `-slim`, `-jij` and
`-sources` artifacts are not standalone installation files.

For a local installation, use a separate Minecraft 1.20.1 profile with Forge
47.4.10 and place the main JAR in that profile's `mods` directory. Flashback is
client-side. Optional integrations are not required for basic recording and
playback. Published Fabric releases do not work in this Forge profile.

Reproducible checks and diagnostic tooling are under `tools/`. For an existing
Gradle log at `build/migration-compile.log`, run `py tools/migration_status.py`.

## Support

If you need assistance installing or using the mod, feel free to join the [discord](https://discord.gg/flashbacktool) and ask for help in #support

## Contributing

Flashback currently does not accept outside contributions

## License

This project is licensed with a custom license, see [LICENSE.md](https://github.com/Moulberry/Flashback/blob/master/LICENSE.md)
The localization files (src/main/resources/assets/flashback/lang) are licensed separately under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
