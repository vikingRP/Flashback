"""Validate the standalone Forge release archive without launching Minecraft."""
import argparse
import io
import json
from pathlib import Path
import sys
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", nargs="?", default="build/libs/flashback-0.43.6.jar")
    parser.add_argument("--allow-stale", action="store_true", help="Inspect an older archive without validating source freshness")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    jar = root / args.jar
    errors = []
    checks = 0

    def check(condition, message):
        nonlocal checks
        checks += 1
        if not condition:
            errors.append(message)

    if not jar.is_file():
        parser.error(f"Archive does not exist: {jar}")
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        required = [
            "META-INF/mods.toml", "META-INF/accesstransformer.cfg", "META-INF/jarjar/metadata.json",
            "META-INF/licenses/Lattice-MIT.txt", "flashback.refmap.json", "pack.mcmeta",
            "com/moulberry/flashback/FlashbackForge.class", "com/moulberry/lattice/Lattice.class",
            "imgui/moulberry90/ImGui.class", "org/bytedeco/javacv/FFmpegFrameRecorder.class",
            "org/lwjgl/util/nfd/NativeFileDialog.class",
        ]
        for name in required:
            check(name in names, f"Missing required entry: {name}")
        check("fabric.mod.json" not in names, "Fabric loader metadata leaked into Forge archive")
        check(not any(name.endswith("module-info.class") for name in names), "JPMS module descriptor leaked into merged archive")
        check("org/lwjgl/system/Library.class" not in names, "LWJGL core must be provided by Minecraft")
        check(not any("/smoketest/" in name for name in names), "Development smoke-test classes leaked into release")
        for config_name in ["flashback.mixins.json", "lattice1201.mixins.json"]:
            check(config_name in names, f"Missing mixin configuration: {config_name}")
            if config_name not in names:
                continue
            config = json.loads(archive.read(config_name))
            check(config.get("compatibilityLevel") == "JAVA_17", f"Wrong Java compatibility in {config_name}")
            check(config.get("refmap") in names, f"Missing reference map for {config_name}")
            for mixin in sum((config.get(side, []) for side in ["mixins", "client", "server"]), []):
                name = config["package"].replace(".", "/") + "/" + mixin.replace(".", "/") + ".class"
                check(name in names, f"Mixin class is absent: {name}")
        if "META-INF/jarjar/metadata.json" in names:
            nested = json.loads(archive.read("META-INF/jarjar/metadata.json"))["jars"]
            artifacts = {item["identifier"]["artifact"] for item in nested}
            check({"mixinextras-forge", "mixinsquared-forge"} <= artifacts, "Missing Forge mixin bootstrap dependencies")
            for item in nested:
                check(item["path"] in names, f"Missing nested archive: {item['path']}")
                if item["path"] in names:
                    companion = jar.with_name(jar.stem + "-jij.jar")
                    if companion.is_file():
                        with zipfile.ZipFile(companion) as original:
                            check(archive.read(item["path"]) == original.read(item["path"]),
                                  f"Nested archive bytes changed during shading/reobfuscation: {item['path']}")
                    with zipfile.ZipFile(io.BytesIO(archive.read(item["path"]))) as dependency:
                        check(any(name.endswith(".class") for name in dependency.namelist()), f"Empty bootstrap dependency: {item['path']}")
        for name in names:
            if name.endswith(".class"):
                data = archive.read(name)
                check(int.from_bytes(data[6:8], "big") <= 61, f"Class needs newer than Java 17: {name}")
        for library in ["jniavcodec.dll", "jniavformat.dll", "jniavutil.dll", "lwjgl_nfd.dll", "imgui-moulberry90-java64.dll"]:
            check(any(name.endswith("/" + library) for name in names), f"Missing Windows native library: {library}")
    if not args.allow_stale:
        inputs = list((root / "src/main").rglob("*")) + [root / name for name in ["build.gradle", "gradle.properties", "settings.gradle"]]
        newer = [str(path.relative_to(root)) for path in inputs if path.is_file() and path.stat().st_mtime > jar.stat().st_mtime]
        check(not newer, f"Archive predates {len(newer)} build inputs; rebuild before delivery")
    result = {"jar": str(jar), "checks": checks, "passed": not errors, "errors": errors}
    print(json.dumps(result, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
