#!/usr/bin/env python3
"""Build, verify, and atomically publish a signed LAN APK; never publish signing secrets."""
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "StudyDesk" if (ROOT / "StudyDesk/gradlew").is_file() else ROOT.parent
PACKAGE = "io.github.cuimiles.studydesk"


def signing_identity(java_home):
    private = Path.home() / ".config/studydesk/signing"
    private.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(private, 0o700)
    key = private / "release.p12"
    config = private / "release.json"
    if key.exists() != config.exists():
        raise RuntimeError("签名文件不完整；恢复原签名备份，不能生成新密钥覆盖旧版本")
    if not key.exists():
        password = secrets.token_urlsafe(36)
        alias = "studydesk-lan"
        env = {**os.environ, "STUDYDESK_KEYTOOL_PASSWORD": password}
        keytool = str(Path(java_home) / "bin/keytool") if java_home else "keytool"
        subprocess.run([keytool, "-genkeypair", "-storetype", "PKCS12", "-keystore", str(key),
                        "-alias", alias, "-keyalg", "RSA", "-keysize", "3072", "-validity", "12000",
                        "-dname", "CN=StudyDesk LAN, O=Personal StudyDesk",
                        "-storepass:env", "STUDYDESK_KEYTOOL_PASSWORD",
                        "-keypass:env", "STUDYDESK_KEYTOOL_PASSWORD"], env=env, check=True)
        config.write_text(json.dumps({"alias": alias, "password": password}))
        os.chmod(key, 0o600)
        os.chmod(config, 0o600)
    secret = json.loads(config.read_text())
    return key, secret


def build_tools(android_home):
    candidates = sorted((Path(android_home) / "build-tools").glob("*/apksigner"),
                        key=lambda path: tuple(int(n) for n in re.findall(r"\d+", path.parent.name)))
    if not candidates:
        raise RuntimeError("未找到 Android build-tools/apksigner；请安装 Android SDK Build Tools")
    folder = candidates[-1].parent
    return folder / "apksigner", folder / "aapt"


def atomic_bytes(path, data):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".publish-", delete=False) as tmp:
        temp = Path(tmp.name)
        tmp.write(data)
        tmp.flush()
        os.fsync(tmp.fileno())
    os.chmod(temp, 0o600)
    os.replace(temp, path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME", ""))
    parser.add_argument("--android-home", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", ""))
    parser.add_argument("--gradle-user-home", default=os.environ.get("GRADLE_USER_HOME", ""))
    parser.add_argument("--gradle", default=str(ANDROID / "gradlew"))
    parser.add_argument("--output-dir", default=str(ROOT / "webapp/runtime/android"))
    parser.add_argument("--offline", action="store_true", help="Use cached Gradle dependencies")
    args = parser.parse_args()
    if not (ANDROID / "app/build.gradle.kts").is_file() or not args.android_home:
        parser.error("需要 Android 工程和 --android-home（Android SDK 目录）")
    os.umask(0o077)
    key, secret = signing_identity(args.java_home)
    env = {**os.environ,
           "STUDYDESK_SIGNING_STORE_FILE": str(key),
           "STUDYDESK_SIGNING_STORE_PASSWORD": secret["password"],
           "STUDYDESK_SIGNING_KEY_ALIAS": secret["alias"],
           "STUDYDESK_SIGNING_KEY_PASSWORD": secret["password"],
           "ANDROID_HOME": args.android_home}
    if args.java_home:
        env["JAVA_HOME"] = args.java_home
    if args.gradle_user_home:
        env["GRADLE_USER_HOME"] = args.gradle_user_home
    command = [args.gradle, ":app:assembleRelease", "--no-daemon"]
    if args.offline:
        command.append("--offline")
    subprocess.run(command, cwd=ANDROID, env=env, check=True)
    output = ANDROID / "app/build/outputs/apk/release"
    metadata = json.loads((output / "output-metadata.json").read_text())
    item = metadata["elements"][0]
    code = item["versionCode"]
    name = item["versionName"]
    apk = output / item["outputFile"]
    if metadata["applicationId"] != PACKAGE or type(code) is not int or not 1 <= code <= 2_100_000_000:
        raise RuntimeError("APK 包名或版本号不正确")
    signer, aapt = build_tools(args.android_home)
    tool_env = {**os.environ}
    if args.java_home:
        tool_env["PATH"] = str(Path(args.java_home) / "bin") + os.pathsep + tool_env.get("PATH", "")
    verified = subprocess.check_output([str(signer), "verify", "--print-certs", str(apk)], text=True, env=tool_env)
    match = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", verified)
    if not match:
        raise RuntimeError("无法验证 APK 签名")
    keytool = str(Path(args.java_home) / "bin/keytool") if args.java_home else "keytool"
    key_env = {**os.environ, "STUDYDESK_KEYTOOL_PASSWORD": secret["password"]}
    certificate = subprocess.check_output([keytool, "-exportcert", "-storetype", "PKCS12",
                                           "-keystore", str(key), "-alias", secret["alias"],
                                           "-storepass:env", "STUDYDESK_KEYTOOL_PASSWORD"], env=key_env)
    certificate_hash = hashlib.sha256(certificate).hexdigest()
    if match.group(1).lower() != certificate_hash:
        raise RuntimeError("APK 签名与保留的长期签名密钥不一致")
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
    expected = f"package: name='{PACKAGE}' versionCode='{code}' versionName='{name}'"
    if expected not in badging:
        raise RuntimeError("APK 中的包名或版本与构建元数据不一致")
    size = apk.stat().st_size
    if not 0 < size <= 200_000_000:
        raise RuntimeError("APK 大小超出上限")
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    directory = Path(args.output_dir)
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(directory, 0o700)
    latest = directory / "latest.json"
    if latest.exists():
        previous = json.loads(latest.read_text())
        if code < previous["versionCode"] or code == previous["versionCode"] and digest != previous["sha256"]:
            raise RuntimeError("新 APK 必须提高 versionCode；同一版本不能替换成不同内容")
    filename = f"StudyDesk-{code}.apk"
    destination = directory / filename
    if not destination.exists():
        with apk.open("rb") as source:
            with tempfile.NamedTemporaryFile(dir=directory, prefix=".apk-", delete=False) as tmp:
                temporary = Path(tmp.name)
                shutil.copyfileobj(source, tmp)
                tmp.flush(); os.fsync(tmp.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, destination)
    else:
        with destination.open("rb") as source:
            if hashlib.file_digest(source, "sha256").hexdigest() != digest:
                raise RuntimeError("已发布的同版本 APK 不一致")
    release = {"packageName": PACKAGE, "versionCode": code, "versionName": name,
               "size": size, "sha256": digest, "url": f"/api/android/apk/{code}",
               "certificateSha256": certificate_hash}
    atomic_bytes(latest, (json.dumps(release, ensure_ascii=False, indent=2) + "\n").encode())
    print(f"Published StudyDesk {name} (versionCode {code}) to {destination}")
    print("Back up ~/.config/studydesk/signing/release.p12 and release.json privately; losing either prevents future in-place updates.")


if __name__ == "__main__":
    main()
