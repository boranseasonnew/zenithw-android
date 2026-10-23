"""Run platform independent regression checks using an existing Gradle Kotlin cache.

Usage: python tests/run_jvm_checks.py PATH_TO_GRADLE_USER_HOME
Requires JAVA_HOME and already downloaded Kotlin 2.2.20 dependencies; no network.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
cache = Path(sys.argv[1]) / "caches/modules-2/files-2.1"
groups = {
    "org.jetbrains.kotlin": ["kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-reflect", "kotlin-daemon-embeddable"],
    "org.jetbrains.intellij.deps": ["trove4j"],
    "org.jetbrains": ["annotations"],
    "org.jetbrains.kotlinx": ["kotlinx-coroutines-core-jvm"],
}
jars = []
for group, names in groups.items():
    for name in names:
        found = list((cache / group / name).rglob("*.jar"))
        preferred = [p for p in found if "2.2.20" in p.parts]
        jars.extend(preferred or sorted(found)[-1:])
assert any(p.name.startswith("kotlin-compiler-embeddable-2.2.20") for p in jars), "Kotlin 2.2.20 is not cached"
classpath = os.pathsep.join(map(str, jars))
java = Path(os.environ["JAVA_HOME"]) / "bin/java"
source = root / "app/src/main/java/space/zenithw/app"
names = ["DownloadPolicy", "BulkLinks", "InspectionPolicy", "CommandArguments", "ExtractionDiagnostics", "PreviewPolicy"]
with tempfile.TemporaryDirectory(prefix="zenith-checks-") as output:
    files = [str(source / f"{name}.kt") for name in names]
    files += [str(root / f"tests/{name}Check.kt") for name in names]
    subprocess.run([str(java), "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-classpath", classpath,
                    "-d", output, *files], check=True)
    for name in names:
        subprocess.run([str(java), "-cp", output + os.pathsep + classpath,
                        f"space.zenithw.app.{name}CheckKt"], check=True)
