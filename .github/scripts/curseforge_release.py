import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tomllib
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[2]
MOD_ID = "polyglottooltip"
VERSION_PATTERN = r"\d+(?:\.\d+){1,2}(?:-(?:alpha|beta|rc)[.-]?\d+(?:\.\d+)?)?"
JAR_METADATA = {"forge": "META-INF/mods.toml", "neoforge": "META-INF/neoforge.mods.toml"}
ENTRY_CLASS = "com/starskyxiii/polyglottooltip/PolyglotTooltip.class"


def stonecutter_targets():
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    declarations = re.findall(
        r'^\s*version\("(?P<target>[\w.-]+)",\s*"(?P<minecraft>[\d.]+)"\)\.buildscript\s*=',
        settings,
        flags=re.MULTILINE,
    )
    targets = {}
    for target, minecraft in declarations:
        loader = target.rsplit("-", 1)[-1]
        if target != f"{minecraft}-{loader}" or loader not in {"fabric", *JAR_METADATA} or target in targets:
            raise ValueError(f"Invalid Stonecutter target: {target}")
        targets[target] = (minecraft, loader)
    if not targets:
        raise ValueError("No Stonecutter targets found.")
    return targets


def sha256(file):
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def release_file(jar, target, minecraft, loader):
    name = re.fullmatch(rf"{MOD_ID}-{re.escape(target)}-({VERSION_PATTERN})\.jar", jar.name)
    if not name:
        raise ValueError(f"Invalid production JAR name: {jar.name}")
    version = name[1]
    dependencies = ""
    with ZipFile(jar) as archive:
        with archive.open(ENTRY_CLASS) as entry:
            header = entry.read(8)
        if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
            raise ValueError(f"Invalid mod entry class in {jar.name}")
        java = int.from_bytes(header[6:8], "big") - 44
        if loader == "fabric":
            mod = json.loads(archive.read("fabric.mod.json"))
            required = mod["depends"]
            valid = (mod["id"], mod["version"], mod["environment"]) == (MOD_ID, version, "client")
            valid = valid and required["minecraft"] in (minecraft, f"={minecraft}")
            valid = valid and "fabricloader" in required and required["java"] == f">={java}"
            if "fabric-api" in required:
                dependencies = "fabric-api(required){curseforge:306612}"
        else:
            metadata = tomllib.loads(archive.read(JAR_METADATA[loader]).decode("utf-8"))
            mod, = metadata["mods"]
            required = {dependency["modId"]: dependency["versionRange"] for dependency in metadata["dependencies"][MOD_ID]}
            valid = (mod["modId"], mod["version"]) == (MOD_ID, version) and loader in required
            valid = valid and required["minecraft"].startswith((f"[{minecraft},", f"[{minecraft}]"))
        if not valid or java < 8:
            raise ValueError(f"Packaged mod metadata disagrees with the release target: {jar.name}")
    return {"target": target, "file": jar.name, "version": version, "minecraft": minecraft, "loader": loader,
            "java": str(java), "dependencies": dependencies, "sha256": sha256(jar)}


def changelog(version):
    source = ROOT / "changelogs" / f"{version}.md"
    if not any(line.strip() and not line.lstrip().startswith("#")
               for line in source.read_text(encoding="utf-8").splitlines()):
        raise ValueError(f"Release notes are empty: {source.name}")
    return source


def manifest(records, source, commit):
    versions = {record["version"] for record in records}
    if len(versions) != 1:
        raise ValueError(f"Production JAR versions disagree: {sorted(versions)}")
    return {"commit": commit, "version": versions.pop(), "changelog_sha256": sha256(source), "targets": records}


def stage(artifacts, bundle, targets, commit):
    records = []
    for target, (minecraft, loader) in targets.items():
        files = list((artifacts / f"{MOD_ID}-{target}").glob("*.jar"))
        jars = [file for file in files if re.fullmatch(rf"{MOD_ID}-{re.escape(target)}-{VERSION_PATTERN}\.jar", file.name)]
        if len(jars) != 1:
            raise ValueError(f"Expected one production JAR for {target}, found {[jar.name for jar in jars]}.")
        records.append(release_file(jars[0], target, minecraft, loader))
    source = changelog(records[0]["version"])
    metadata = manifest(records, source, commit)
    bundle.mkdir(parents=True)
    for entry in records:
        shutil.copyfile(artifacts / f"{MOD_ID}-{entry['target']}" / entry["file"], bundle / entry["file"])
    shutil.copyfile(source, bundle / "changelog.md")
    (bundle / "release.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    verify(bundle, targets, commit)
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"## PolyglotTooltip {metadata['version']}\n\nCommit `{commit}`\n\n")
            stream.write("| Production JAR | Java | SHA-256 |\n| --- | --- | --- |\n")
            for entry in records:
                stream.write(f"| `{entry['file']}` | {entry['java']} | `{entry['sha256']}` |\n")
            stream.write("\n" + source.read_text(encoding="utf-8"))


def verify(bundle, targets, commit):
    metadata = json.loads((bundle / "release.json").read_text(encoding="utf-8"))
    version = metadata["version"]
    if not re.fullmatch(VERSION_PATTERN, version):
        raise ValueError("Invalid release bundle version.")
    jars = {target: bundle / f"{MOD_ID}-{target}-{version}.jar" for target in targets}
    if {file.name for file in bundle.iterdir()} != {"release.json", "changelog.md", *(jar.name for jar in jars.values())}:
        raise ValueError("The release bundle does not contain exactly the expected files.")
    records = [release_file(jars[target], target, *variants) for target, variants in targets.items()]
    expected = manifest(records, changelog(version), commit)
    if expected != metadata or sha256(bundle / "changelog.md") != expected["changelog_sha256"]:
        raise ValueError("Release bundle metadata or checksums do not match.")
    print(f"Verified {len(records)} production JARs and the changelog for {version} ({commit}).")
    return metadata


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    staging = commands.add_parser("stage")
    staging.add_argument("artifacts", type=Path)
    staging.add_argument("bundle", type=Path)
    commands.add_parser("verify").add_argument("bundle", type=Path)
    args = parser.parse_args()
    try:
        commit = os.environ["GITHUB_SHA"]
        if not re.fullmatch(r"[a-f0-9]{40}", commit):
            raise ValueError("Invalid GITHUB_SHA.")
        targets = stonecutter_targets()
        if args.command == "stage":
            stage(args.artifacts, args.bundle, targets, commit)
        else:
            verified = verify(args.bundle, targets, commit)
            if output := os.environ.get("GITHUB_OUTPUT"):
                records = [dict(entry, changelog_sha256=verified["changelog_sha256"]) for entry in verified["targets"]]
                with open(output, "a", encoding="utf-8") as stream:
                    stream.write(f"version={verified['version']}\ntargets={json.dumps(records, separators=(',', ':'))}\n")
    except (BadZipFile, KeyError, OSError, TypeError, ValueError) as error:
        print(f"Release preparation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
