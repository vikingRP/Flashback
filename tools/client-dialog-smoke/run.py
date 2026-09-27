"""Check native-dialog result delivery without starting Minecraft or opening a dialog."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
jdk = Path(args.java_home) if args.java_home else Path(shutil.which('javac') or '').resolve().parent.parent
suffix = '.exe' if os.name == 'nt' else ''
runtime_file = root / 'build/classpath/runClient_minecraftClasspath.txt'
if not runtime_file.is_file():
    raise SystemExit('Resolve the Forge client runtime and compile the mod before running this check')
output = root / 'build/client-dialog-smoke'
output.mkdir(parents=True, exist_ok=True)
runtime = runtime_file.read_text(encoding='utf-8').splitlines()
classpath = os.pathsep.join([str(output), str(root / 'build/classes/java/main'), *runtime])

def invoke(tool, arguments):
    executable = jdk / 'bin' / (tool + suffix)
    argument_file = output / (tool + '.args')
    argument_file.write_text('\n'.join('"' + str(arg).replace('\\', '/').replace('"', '\\"') + '"' for arg in arguments), encoding='utf-8')
    subprocess.run([str(executable), '@' + str(argument_file)], check=True, cwd=root, timeout=30)

invoke('javac', ['-proc:none', '-encoding', 'UTF-8', '--release', '17', '-cp', classpath, '-d', str(output),
                str(root / 'src/main/java/com/moulberry/flashback/utils/AsyncFileDialogs.java'),
                str(root / 'tools/client-dialog-smoke/ClientDialogSmoke.java')])
invoke('java', ['-cp', classpath, 'com.moulberry.flashback.utils.ClientDialogSmoke'])
