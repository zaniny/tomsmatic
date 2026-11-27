"""Build and validate a Tomsmatic release with Python 3.11+ and JDK 25."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
from urllib.request import urlopen
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
LANGUAGES = RESOURCES / "assets/schematic_stock/lang"
OLDER_TARGETS = {
    "26.1": ("0.155.3", "2.10.3", "0.27.14", "0.28.12"),
    "26.2": ("0.161.0", "2.11.4", "0.28.8", "0.29.6"),
}
MINECRAFT_VERSIONS = ("26.1", "26.1.1", "26.1.2", "26.2", "26.3")


def read_json(path):
    def unique_keys(entries):
        result = dict(entries)
        if len(result) != len(entries):
            raise ValueError(f"Duplicate key in {path}")
        return result

    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)


def digest(path, algorithm):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def download(target, url, checksum, algorithm):
    if target.is_file() and digest(target, algorithm) == checksum:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temporary:
        path = Path(temporary.name)
    try:
        with path.open("wb") as temporary:
            with urlopen(url, timeout=60) as response:
                shutil.copyfileobj(response, temporary)
        if digest(path, algorithm) != checksum:
            raise ValueError(f"Checksum mismatch for {target.name}")
        path.replace(target)
    finally:
        path.unlink(missing_ok=True)
    return target


def dependencies(cache, minecraft_version):
    locked = read_json(ROOT / "scripts/dependencies.json")
    if minecraft_version != "26.3":
        older = read_json(ROOT / "scripts/legacy-dependencies.json")
        if minecraft_version.startswith("26.1"):
            locked.update(older["26.1"])
        locked.update(older[minecraft_version])
    files = {
        name: download(cache / name, spec["url"], spec["sha256"], "sha256")
        for name, spec in locked.items()
    }
    minecraft = read_json(files.pop("mc-version.json"))
    if minecraft["id"] != minecraft_version:
        raise ValueError("Requested Minecraft version differs from the dependency lock")
    client = minecraft["downloads"]["client"]
    yield download(cache / "game.jar", client["url"], client["sha1"], "sha1")
    yield from files.values()
    libraries = {lib["downloads"]["artifact"]["path"]: lib["downloads"]["artifact"]
                 for lib in minecraft["libraries"]
                 if "artifact" in lib.get("downloads", {})
                 and "-natives-" not in lib["downloads"]["artifact"]["path"]}
    for lib in libraries.values():
        yield download(cache / "libs" / lib["path"], lib["url"], lib["sha1"], "sha1")

    nested = re.compile(r"META-INF/jars/(fabric-(api-base|screen-api-v1|networking-api-v1|"
                        r"lifecycle-events-v1|key-mapping-api-v1)|mixinextras-fabric)-.+\.jar$")
    for name in ("fabric.jar", "loader.jar"):
        with zipfile.ZipFile(files[name]) as jar:
            for entry in jar.namelist():
                if nested.fullmatch(entry):
                    path = cache / "nested" / PurePosixPath(entry).name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(jar.read(entry))
                    yield path


def pack(classes, output, metadata):
    english = read_json(LANGUAGES / "en_us.json")
    russian = read_json(LANGUAGES / "ru_ru.json")
    if english.keys() != russian.keys():
        raise ValueError("English and Russian translation keys differ")
    icon = metadata.get("icon")
    if icon and not (RESOURCES / icon).is_file():
        raise ValueError(f"Icon declared in mod metadata is missing: {icon}")

    class_names = [name for items in metadata["entrypoints"].values() for name in items]
    for config in metadata["mixins"]:
        mixins = read_json(RESOURCES / config)
        class_names.extend(mixins["package"] + "." + name
                           for section in ("mixins", "client", "server")
                           for name in mixins.get(section, []))
    for name in class_names:
        if not (classes / (name.replace(".", "/") + ".class")).is_file():
            raise ValueError(f"Class declared in mod metadata is missing: {name}")

    for source in (classes, RESOURCES):
        for file in sorted(source.rglob("*")):
            if file.is_file():
                info = zipfile.ZipInfo(file.relative_to(source).as_posix())
                info.external_attr = 0o644 << 16
                content = (json.dumps(metadata, indent=2).encode("utf-8") + b"\n"
                           if file == RESOURCES / "fabric.mod.json" else file.read_bytes())
                output.writestr(info, content, compress_type=zipfile.ZIP_DEFLATED)
    license_file = ROOT / "LICENSE"
    if license_file.is_file():
        info = zipfile.ZipInfo("LICENSE")
        info.external_attr = 0o644 << 16
        output.writestr(info, license_file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)


def release_notes(version):
    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    sections = re.split(r"^## +([^\n]+)\n", changelog, flags=re.MULTILINE)
    for heading, body in zip(sections[1::2], sections[2::2]):
        if heading.strip() == version and body.strip():
            return body.strip() + "\n"
    raise ValueError(f"No release notes for {version} in CHANGELOG.md")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=ROOT / "build/ci-cache")
    parser.add_argument("--output", type=Path, default=ROOT / "build/release")
    parser.add_argument("--tag", help="Check that the release tag is v<mod version>")
    parser.add_argument("--minecraft", choices=MINECRAFT_VERSIONS,
                        default="26.3", help="Minecraft version to compile against")
    args = parser.parse_args()
    metadata = read_json(RESOURCES / "fabric.mod.json")
    if args.minecraft != metadata["depends"]["minecraft"]:
        series = ".".join(args.minecraft.split(".")[:2])
        fabric, tom, litematica, malilib = OLDER_TARGETS[series]
        metadata["depends"].update(minecraft=args.minecraft, toms_storage=tom)
        metadata["depends"]["fabric-api"] = f">={fabric}"
        metadata["suggests"].update(litematica=litematica, malilib=malilib)
    version = metadata["version"]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        parser.error("fabric.mod.json must contain a version of the form X.Y.Z")
    if args.tag and args.tag != f"v{version}":
        parser.error(f"Tag {args.tag} does not match fabric.mod.json version {version}")
    notes = release_notes(version) if args.tag else None

    args.output.mkdir(parents=True, exist_ok=True)
    artifact = args.output / f"tomsmatic-{version}-{args.minecraft}.jar"
    javac = str(Path(os.environ["JAVA_HOME"]) / "bin/javac") if "JAVA_HOME" in os.environ else "javac"
    screen_api = "26.1" if args.minecraft.split(".")[:2] == ["26", "1"] else "26.2+"
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    sources.extend(sorted((ROOT / "src" / screen_api / "java").rglob("*.java")))
    with tempfile.TemporaryDirectory(dir=args.output) as directory:
        staging = Path(directory)
        classes = staging / "classes"
        classes.mkdir()
        classpath = os.pathsep.join(str(path) for path in dependencies(args.cache / args.minecraft, args.minecraft))
        arguments = ["-proc:none", "--release", "25", "-encoding", "UTF-8",
                     "-classpath", classpath, "-d", str(classes), *map(str, sources)]
        argfile = staging / "javac.args"
        argfile.write_text("\n".join('"' + arg.replace("\\", "/") + '"' for arg in arguments),
                           encoding="utf-8")
        subprocess.run([javac, f"@{argfile}"], check=True)
        jar = staging / artifact.name
        with zipfile.ZipFile(jar, "w") as output:
            pack(classes, output, metadata)
        with zipfile.ZipFile(jar) as output:
            if output.testzip():
                raise ValueError("Corrupt release JAR")
        jar.replace(artifact)
    artifact.with_suffix(".jar.sha256").write_text(f"{digest(artifact, 'sha256')}  {artifact.name}\n", encoding="utf-8")
    if notes:
        (args.output / f"tomsmatic-{version}.md").write_text(notes, encoding="utf-8")
    print(artifact)


if __name__ == "__main__":
    main()
