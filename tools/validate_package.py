from pathlib import Path
import hashlib
import struct
import zipfile

root = Path(__file__).resolve().parents[1]
mod = root / "content/mods/ViewpointCeilingLightSources"
for relative in ("mod.info", "42/mod.info"):
    info = dict(line.split("=", 1) for line in (mod / relative).read_text().splitlines() if "=" in line)
    assert info["id"] == "ViewpointCeilingLightSources"
    assert info["modversion"] == "0.1.0"
    assert info["javaPkgName"] == "maeiro.ceilinglights"
    assert (mod / "42" / info["javaJarFile"]).is_file()
jar = mod / "42/media/java/client/viewpoint-ceiling-light-sources.jar"
with zipfile.ZipFile(jar) as archive:
    for name in archive.namelist():
        if name.endswith(".class"):
            assert name.startswith("maeiro/ceilinglights/"), name
            assert struct.unpack(">H", archive.read(name)[6:8])[0] == 61, name
        assert "Test" not in name and not name.startswith(("zombie/", "viewpoint/", "net/")), name
    for name in ("compatibility.properties", "emission.properties"):
        assert archive.read(name) == (root / "resources" / name).read_bytes()
staged = root / "dist/ViewpointCeilingLightSources/Contents/mods/ViewpointCeilingLightSources"
for source in mod.rglob("*"):
    if source.is_file():
        target = staged / source.relative_to(mod)
        assert target.is_file() and hashlib.sha256(source.read_bytes()).digest() == hashlib.sha256(target.read_bytes()).digest(), target
with zipfile.ZipFile(root / "dist/ViewpointCeilingLightSources.zip") as archive:
    names = {name.replace("\\", "/") for name in archive.namelist()}
    prefix = "ViewpointCeilingLightSources/Contents/mods/ViewpointCeilingLightSources/"
    assert prefix + "mod.info" in names
    assert prefix + "42/mod.info" in names
    assert prefix + "42/media/java/client/viewpoint-ceiling-light-sources.jar" in names
print("Package validated: correct metadata, Java 17, isolated classes, exact staging hashes, and install ZIP.")
