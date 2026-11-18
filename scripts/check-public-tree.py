"""Check the Git-visible source tree without touching private local files."""
from pathlib import Path
import hashlib
import re
import subprocess
import sys
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def git_paths(*args):
    result = subprocess.run(["git", *args, "-z"], cwd=ROOT, check=True, capture_output=True)
    return {p for p in result.stdout.decode("utf-8").split("\0") if p}


def main():
    files = git_paths("ls-files", "--cached", "--others", "--exclude-standard")
    errors = []
    ignored_tracked = git_paths("ls-files", "--cached", "--ignored", "--exclude-standard")
    errors.extend(f"Tracked file violates allowlist: {p}" for p in sorted(ignored_tracked))
    private_roots = {".tools", "local-evidence", "research", "design", ".gradle", "build"}
    private_extensions = {".apk", ".aab", ".jks", ".keystore", ".p12", ".pem", ".key", ".bin"}
    content_checks = {
        "private absolute path": re.compile(r"(?i)[a-z]:[\\/]Users[\\/]|/[U]sers/|/home/[a-z][^ /]+/"),
        "Bluetooth address": re.compile(r"(?i)(?:\b[0-9a-f]{2}:){5}[0-9a-f]{2}\b"),
        "private LAN address": re.compile(r"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3})\b"),
        "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        "access token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b"),
    }
    for name in sorted(files):
        path = ROOT / name
        if path.is_symlink():
            errors.append(f"Review symlink before publishing: {name}")
            continue
        if name.split('/')[0] in private_roots or path.suffix.lower() in private_extensions:
            errors.append(f"Private/generated file in public tree: {name}")
        if not path.is_file():
            errors.append(f"Missing public file: {name}")
            continue
        if path.suffix.lower() in {".jar", ".png"}:
            continue
        text = path.read_text(encoding="utf-8-sig")
        for label, pattern in content_checks.items():
            matches = pattern.findall(text)
            if label == "Bluetooth address" and name == "app/src/test/java/dev/bladelight/app/AutoConnectPolicyTest.java":
                # Explicit synthetic fixtures; do not exempt other addresses or test files.
                matches = [m for m in matches if m.replace(':', '') not in {"001122334455", "001122334456"}]
            if matches:
                errors.append(f"Review {label} in {name}")
        if path.suffix == ".md":
            links = re.findall(r"\]\(([^)]+)\)", text) + re.findall(r'(?:src|href)="([^"]+)"', text)
            for link in links:
                link = link.strip('<>')
                if urllib.parse.urlsplit(link).scheme or link.startswith('#'):
                    continue
                target = (path.parent / urllib.parse.unquote(link.split('#')[0])).resolve()
                if not target.is_relative_to(ROOT):
                    errors.append(f"Link leaves public repository: {name}: {link}")
                elif target.is_dir():
                    prefix = target.relative_to(ROOT).as_posix().rstrip('/') + '/'
                    if not any(p.startswith(prefix) for p in files):
                        errors.append(f"Link points to excluded directory: {name}: {link}")
                elif target.relative_to(ROOT).as_posix() not in files:
                    errors.append(f"Missing/excluded link target: {name}: {link}")
    resources = []
    for folder in ("values", "values-ko"):
        tree = ET.parse(ROOT / f"app/src/main/res/{folder}/strings.xml")
        values = {node.attrib['name']: ''.join(node.itertext()) for node in tree.getroot()}
        if len(values) != len(tree.getroot()):
            errors.append(f"Duplicate string resources in {folder}")
        resources.append(values)
    if resources[0].keys() != resources[1].keys():
        errors.append("English/Korean resource keys differ")
    for key in resources[0].keys() & resources[1].keys():
        placeholders = [sorted(re.findall(r"%\d+\$[a-zA-Z]", r[key])) for r in resources]
        if placeholders[0] != placeholders[1]:
            errors.append(f"Translation placeholders differ: {key}")
    wrapper = ROOT / 'gradle/wrapper/gradle-wrapper.jar'
    expected = '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
    if hashlib.sha256(wrapper.read_bytes()).hexdigest() != expected:
        errors.append('Gradle8.13 wrapper JAR checksum mismatch')
    if errors:
        print('\n'.join(errors))
        return 1
    print(f'Public tree: {len(files)} files; local links, selected privacy patterns, wrapper checksum and {len(resources[0])} translation keys checked.')
    print('This is a targeted check, not a complete secret or licensing audit.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
