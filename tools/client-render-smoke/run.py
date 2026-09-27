"""Run the production OpenGL compositor in a hidden GLFW window (no Minecraft client needed)."""
from pathlib import Path
import argparse
import os
import platform
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'), help='JDK 17 directory')
parser.add_argument('--capture', action='store_true', help='Also test PBO readback using the compiled mod classes')
parser.add_argument('--export', action='store_true', help='Also encode/decode GPU images and audio using the production writer and embedded FFmpeg')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
java_home = Path(args.java_home) if args.java_home else Path(shutil.which('javac') or '').resolve().parent.parent
suffix = '.exe' if os.name == 'nt' else ''
java = java_home / 'bin' / ('java' + suffix)
javac = java_home / 'bin' / ('javac' + suffix)
if not java.is_file() or not javac.is_file():
    raise SystemExit('Set JAVA_HOME or pass --java-home with a JDK 17 installation')
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.lwjgl'
native = {'Windows': 'windows', 'Linux': 'linux', 'Darwin': 'macos'}[platform.system()]
if platform.machine().lower() in ('arm64', 'aarch64'):
    native += '-arm64'
paths = []
for module in ['lwjgl', 'lwjgl-glfw', 'lwjgl-opengl']:
    for name in [module + '-3.3.1.jar', module + '-3.3.1-natives-' + native + '.jar']:
        matches = list((cache / module / '3.3.1').rglob(name))
        if not matches:
            raise SystemExit('Missing ' + name + '; resolve Forge client dependencies first')
        paths.append(str(matches[0]))
output = root / 'build/client-render-smoke'
output.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(paths)
source = root / 'src/main/java/com/moulberry/flashback/visuals/ShaderManager.java'
subprocess.run([str(javac), '-encoding', 'UTF-8', '--release', '17', '-cp', classpath,
                '-d', str(output), str(source), str(root / 'tools/client-render-smoke/ClientRenderSmoke.java')], check=True, cwd=root)
subprocess.run([str(java), '-cp', str(output) + os.pathsep + classpath, 'ClientRenderSmoke'], check=True, cwd=root)
if args.capture or args.export:
    classes = root / 'build/classes/java/main'
    if not (classes / 'com/moulberry/flashback/exporting/SaveableFramebuffer.class').is_file():
        raise SystemExit('Run gradlew compileJava before --capture')
    mapped = list((root / 'build/fg_cache/net/minecraftforge/forge').glob('*mapped_official_1.20.1/*mapped_official_1.20.1.jar'))
    if not mapped:
        raise SystemExit('Mapped Forge jar missing; run gradlew compileJava')
    capture_classpath = os.pathsep.join([str(classes), str(mapped[0]), classpath])
    subprocess.run([str(javac), '-encoding', 'UTF-8', '--release', '17', '-cp', capture_classpath,
                    '-d', str(output), str(root / 'tools/client-render-smoke/ClientCaptureSmoke.java')], check=True, cwd=root)
    subprocess.run([str(java), '-cp', str(output) + os.pathsep + capture_classpath, 'ClientCaptureSmoke'], check=True, cwd=root)
    subprocess.run([str(javac), '-encoding', 'UTF-8', '--release', '17', '-cp', capture_classpath,
                    '-d', str(output), str(root / 'src/main/java/com/moulberry/flashback/exporting/PanoramaProjector.java'),
                    str(root / 'tools/client-render-smoke/ClientPanoramaSmoke.java')], check=True, cwd=root)
    subprocess.run([str(java), '-cp', str(output) + os.pathsep + capture_classpath, 'ClientPanoramaSmoke'], check=True, cwd=root)
if args.export:
    runtime_file = root / 'build/classpath/runClient_minecraftClasspath.txt'
    if not runtime_file.is_file():
        raise SystemExit('Resolve runClient dependencies before --export')
    runtime = runtime_file.read_text(encoding='utf-8').splitlines()
    export_classpath = os.pathsep.join([str(output), str(classes), *runtime, classpath])
    # JDK argument files avoid the Windows command-line length limit.
    def invoke(tool, arguments):
        argument_file = output / (tool.stem + '-export.args')
        argument_file.write_text('\n'.join('"' + str(arg).replace('\\', '/').replace('"', '\\"') + '"' for arg in arguments), encoding='utf-8')
        subprocess.run([str(tool), '@' + str(argument_file)], check=True, cwd=root, timeout=120)
    invoke(javac, ['-encoding', 'UTF-8', '--release', '17', '-cp', export_classpath, '-d', str(output),
                   str(root / 'src/main/java/com/moulberry/flashback/exporting/AsyncFFmpegVideoWriter.java'),
                   str(root / 'src/main/java/com/moulberry/flashback/exporting/PixelFormatHelper.java'),
                   str(root / 'src/main/java/com/moulberry/flashback/exporting/NativeLibraryBootstrap.java'),
                   str(root / 'tools/client-render-smoke/ClientExportSmoke.java')])
    invoke(java, ['-cp', export_classpath, 'ClientExportSmoke', str(output / 'exports')])
