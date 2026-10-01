#!/usr/bin/env python3
"""Validate UI localization without launching any application or UI test.

The shared Compose XML resources are authoritative. Official Compose resource
accessors and assets are generated from them; SwiftUI owns native .strings.
Only application UI literals are checked, never music, lyrics, logs, or protocols.
"""
from pathlib import Path
import re,json
from ui_resource_keys import resource_key_errors
HAN=re.compile('[\u3400-\u4dbf\u4e00-\u9fff]')
# Parse Kotlin interpolation expressions as code, including nested strings/comments.
def quoted(s,i):
 raw=s.startswith('"""',i); q='"""' if raw else '"'; j=i+len(q); parts=[]; args=[]; start=j
 while j<len(s):
  if s.startswith(q,j):
   parts.append(s[start:j]);return j+len(q),parts,args,raw
  if not raw and s[j]=='\\': j+=2;continue
  if s[j]=='$' and j+1<len(s):
   if s[j+1]=='{':
    k=j+2;level=1
    while k<len(s) and level:
     if s[k]=='"':k=quoted(s,k)[0];continue
     if s.startswith('//',k): k=s.find('\n',k);continue
     if s[k]=='{':level+=1
     if s[k]=='}':level-=1
     k+=1
    parts.append(s[start:j]);args.append(s[j+2:k-1]);j=k;start=j;continue
   m=re.match(r'[A-Za-z_][A-Za-z_0-9]*',s[j+1:])
   if m:
    k=j+1+len(m[0]);parts.append(s[start:j]);args.append(m[0]);j=k;start=j;continue
  j+=1
 raise ValueError(s[i:i+120])
def unescape(s,raw=False):
 if raw:return s
 return re.sub(r'\\(u[0-9a-fA-F]{4}|.)',lambda m:chr(int(m[1][1:],16)) if m[1].startswith('u') else {'n':'\n','r':'\r','t':'\t','b':'\b','f':'\f','"':'"',"'":"'",'\\':'\\','$':'$'}.get(m[1],m[0]),s)
def literals(s):
 i=0
 while i<len(s):
  if s.startswith('//',i):i=s.find('\n',i);i=len(s) if i<0 else i;continue
  if s.startswith('/*',i):
   j=s.find('*/',i+2);i=len(s) if j<0 else j+2;continue
  if s[i]=="'":
   i+=1
   while i<len(s):
    if s[i]=='\\':i+=2;continue
    if s[i]=="'":i+=1;break
    i+=1
   continue
  if s[i]=='"':
   end,parts,args,raw=quoted(s,i)
   literal=''.join(unescape(p,raw) for p in parts)
   if HAN.search(literal):
    template=''.join(unescape(p,raw)+(f'%{j+1}$s' if j<len(args) else '') for j,p in enumerate(parts))
    yield i,end,template,args,raw
   i=end;continue
  i+=1

import sys
import xml.etree.ElementTree as ET
from collections import Counter
ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / "shared/resources/src/commonMain/composeResources"
LOCALES = ("values", "values-zh", "values-zh-rTW")
errors = []
catalogs = {}
argument_pattern = re.compile(r"%([1-9][0-9]*)\$([sd])")
def validate_resource_key(key, location):
    errors.extend(resource_key_errors(key, location))

for locale in LOCALES:
    catalog = {}
    for path in sorted((RESOURCE_ROOT / locale).glob("*.xml")):
        for element in ET.parse(path).getroot():
            key = element.attrib["name"]
            if key in catalog:
                errors.append(f"{locale}: duplicate key {key}")
            validate_resource_key(key, locale)
            values = ({item.attrib["quantity"]: item.text or "" for item in element}
                      if element.tag == "plurals" else {"string": element.text or ""})
            if element.tag == "plurals" and set(values) != {"one", "other"}:
                errors.append(f"{locale}: {key} needs one/other quantities")
            for text in values.values():
                slots = {int(index) for index, _ in argument_pattern.findall(text)}
                if slots and slots != set(range(1, max(slots) + 1)):
                    errors.append(f"{locale}: {key} has nonconsecutive arguments")
            catalog[key] = element.tag, values
    catalogs[locale] = catalog

# Handwritten Android resources follow the same naming rule as the shared XML.
for relative in ("composeApp/src/androidMain/res", "android/runtime/src/main/res",
                 "tvApp/src/main/res", "automotiveApp/src/main/res"):
    for path in (ROOT / relative).glob("values*/*.xml"):
        for element in ET.parse(path).getroot():
            if element.tag in {"string", "string-array", "plurals"}:
                validate_resource_key(element.attrib["name"], path.relative_to(ROOT))

base = catalogs["values"]
for locale in LOCALES[1:]:
    if catalogs[locale].keys() != base.keys():
        errors.append(f"{locale}: translation keys differ")
    for key in base.keys() & catalogs[locale].keys():
        kind, patterns = base[key]
        translated_kind, translated = catalogs[locale][key]
        if kind != translated_kind or patterns.keys() != translated.keys():
            errors.append(f"{locale}: {key} resource/quantity types differ")
            continue
        for quantity, text in patterns.items():
            if Counter(argument_pattern.findall(text)) != Counter(argument_pattern.findall(translated[quantity])):
                errors.append(f"{locale}: {key}/{quantity} argument number/type differs")

# Static key references, including non-Compose business messages.
for module in ("player", "shared", "composeApp", "android", "tvApp", "automotiveApp", "cast"):
    for path in (ROOT / module).rglob("*.kt"):
        if "build" in path.parts or any(part.endswith("Test") for part in path.parts):
            continue
        source = path.read_text()
        for kind, key in re.findall(r'\bRes\.(string|plurals)\.([a-z0-9_]+)', source):
            if key not in base:
                errors.append(f"{path.relative_to(ROOT)}: missing resource {key}")
            elif (kind == "plurals") != (base[key][0] == "plurals"):
                errors.append(f"{path.relative_to(ROOT)}: incorrect resource type {key}")
        if re.search(r'(?:uiString|uiText|uiPlural|nativeUiString)\(\s*"[a-z0-9_]+"', source):
            errors.append(f"{path.relative_to(ROOT)}: obsolete string-key resource call")
        if path.name != "UiResourceEnvironment.kt" and re.search(r'INVISIBLE_(?:REFERENCE|MEMBER)|InternalResourceApi|LocalComposeEnvironment|LanguageQualifier|RegionQualifier', source):
            errors.append(f"{path.relative_to(ROOT)}: resource internals must stay in the language adapter")

# Explicit UI-only scan. These remaining Chinese literals are diagnostic invariants.
allowed_diagnostics = {
    "App.kt": {"应用资源释放未完全成功。", "关闭资源失败：%1$s"},
    "LibraryNavigation.kt": {"在线导航目标应在本地导航解析前处理。"},
    "AndroidOfflineDownloadGateway.kt": {"离线文件不存在。"},
    "JvmOfflineDownloadGateway.kt": {"离线文件不存在。"},
    "OfflineDownloadRepositories.kt": {"上次下载未完成。", "下载失败。", "离线文件不存在。"},
    "AndroidWebDavSupport.kt": {"读取失败。"},
    "JvmWebDavSupport.kt": {"读取失败。"},
    "AndroidDesktopLyricsOverlayService.kt": {"保存桌面歌词位置失败。"},
}
ui_paths = list((ROOT / "player/app/src/commonMain").rglob("*.kt"))
ui_paths += [p for p in (ROOT / "tvApp/src/main").rglob("*.kt")
             if p.name.endswith(("Activity.kt", "Screen.kt", "Overlay.kt", "List.kt"))]
ui_paths += [ROOT / "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform" / name
             for name in ("AndroidEqualizerActivity.kt", "AndroidLocalFolderPickerActivity.kt", "AndroidWebDavSupport.kt", "AndroidAudioTagEditorPlatformService.kt", "AndroidSambaSupport.kt", "AndroidPlaybackErrorText.kt")]
ui_paths += [ROOT / "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music" / name
             for name in ("JvmDesktopStartingScreen.kt", "JvmCrashReporter.kt")]
ui_paths += [ROOT / "cast/api/src/commonMain/kotlin/top/iwesley/lyn/music/cast/CastModels.kt"]
ui_paths += [ROOT / "cast/upnp/android/src/main/kotlin/top/iwesley/lyn/music/cast/upnp/android" / name
             for name in ("AndroidUpnpCastGateway.kt", "NativeCastErrorText.kt", "AndroidUpnpMediaRenderer.kt", "NativeRendererErrorText.kt")]
ui_paths += [ROOT / path for path in (
    "shared/core/src/commonMain/kotlin/top/iwesley/lyn/music/core/model/AppleMediaLocatorResolver.kt",
    "shared/core/src/commonMain/kotlin/top/iwesley/lyn/music/core/model/OfflineDownloadFailureText.kt",
    "shared/core/src/commonMain/kotlin/top/iwesley/lyn/music/core/model/WebDav.kt",
    "shared/core/src/commonMain/kotlin/top/iwesley/lyn/music/core/model/SambaUiText.kt",
    "shared/data/src/commonMain/kotlin/top/iwesley/lyn/music/platform/SambaPlaybackSupport.kt",
    "shared/data/src/commonMain/kotlin/top/iwesley/lyn/music/data/repository/OfflineDownloadRepositories.kt",
    "composeApp/src/commonMain/kotlin/top/iwesley/lyn/music/platform/ApplePlaybackErrorText.kt",
    "composeApp/src/applePlaybackMain/kotlin/top/iwesley/lyn/music/platform/ApplePlaybackGateway.kt",
    "composeApp/src/iosMain/kotlin/top/iwesley/lyn/music/platform/AppleNativePlayer.ios.kt",
    "composeApp/src/macosMain/kotlin/top/iwesley/lyn/music/platform/AppleNativePlayer.macos.kt",
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmOfflineDownloadGateway.kt",
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmWebDavSupport.kt",
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmSambaSupport.kt",
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmLyricsShareFontLibraryPlatformService.kt",
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmNativeFilePicker.kt",
    "tvApp/src/main/kotlin/top/iwesley/lyn/music/tv/TvRendererActivityPlaybackSession.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidOfflineDownloadGateway.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidDesktopLyricsOverlayService.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidDesktopLyricsLanguageRefresh.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/SambaCastProxyResource.kt",
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/VlcSupport.kt",
    "shared/data/src/commonMain/kotlin/top/iwesley/lyn/music/domain/WorkflowLyricsEngine.kt",
)]
for path in ui_paths:
    source = path.read_text()
    # Enum entries live for the process lifetime; only resource keys may be cached there.
    for entries in re.findall(r'enum\s+class\s+\w+\s*(?:\([^{}]*\))?\s*\{([^;{}]*)(?:;|\})', source, re.S):
        if re.search(r'\b(?:uiString|nativeUiString|resolveUiText)\s*\(', entries):
            errors.append(f"{path.relative_to(ROOT)}: enum entries cache translated UI strings")
    for start, _, template, _, _ in literals(source):
        if template not in allowed_diagnostics.get(path.name, set()):
            errors.append(f"{path.relative_to(ROOT)}:{source.count(chr(10), 0, start) + 1}: hardcoded UI text {template}")

# Connection-test failures are displayed directly; other platform sections include excluded logs and metadata.
for relative_path in (
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidPlatform.kt",
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmPlatform.kt",
):
    source = (ROOT / relative_path).read_text()
    start = source.index("override suspend fun testSamba(")
    end = source.index("override suspend fun scanSamba(", start)
    for offset, _, template, _, _ in literals(source[start:end]):
        errors.append(f"{relative_path}:{source.count(chr(10), 0, start + offset) + 1}: hardcoded Samba connection UI text {template}")

# Check desktop display boundaries without including migration diagnostics or user metadata.
desktop_ui_sections = {
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmPlatform.kt": (
        ("override suspend fun pickArtworkBytes(", "override suspend fun loadArtworkBytes("),
        ("private class JvmVlcPathPickerPlatformService", "private fun resolveJvmLocalTrackPath("),
        ("override suspend fun pickLocalFolder(", "override suspend fun scanLocalFolder("),
    ),
    "composeApp/src/jvmMain/kotlin/top/iwesley/lyn/music/platform/JvmDataLocation.kt": (
        ("suspend fun scheduleChange(", "suspend fun applyPendingChange("),
        ("private fun validateTargetTopology(", "private fun validateSourceDatabaseCollection("),
        ("private fun validateSourceOwnership(", "private fun requireRootIdentity("),
        ("private fun ensureActiveRootIdentity(", "private fun copyNonDatabaseFiles("),
        ("internal class JvmAppDataLocationPlatformService", "internal fun isJvmWindowsOs("),
    ),
}
for relative_path, sections in desktop_ui_sections.items():
    source = (ROOT / relative_path).read_text()
    for start_marker, end_marker in sections:
        start = source.index(start_marker)
        end = source.index(end_marker, start)
        for offset, _, template, _, _ in literals(source[start:end]):
            errors.append(f"{relative_path}:{source.count(chr(10), 0, start + offset) + 1}: hardcoded desktop UI text {template}")

# Application-generated JNI errors use resource IDs rather than translated diagnostic strings.
cast_native_source = (ROOT / "cast/upnp/android/src/main/cpp/lyn_upnp_cast_jni.cpp").read_text()
native_cast_keys = set(re.findall(r'"lyn_ui:(cast_[a-z0-9_]+)"', cast_native_source))
cast_adapter_source = (ROOT / "cast/upnp/android/src/main/kotlin/top/iwesley/lyn/music/cast/upnp/android/NativeCastErrorText.kt").read_text()
adapter_keys_block = re.search(r'nativeCastErrorResources\s*=\s*mapOf\((.*?)\)', cast_adapter_source, re.S)
adapter_cast_keys = set(re.findall(r'"(cast_[a-z0-9_]+)"', adapter_keys_block.group(1))) if adapter_keys_block else set()
if not native_cast_keys or native_cast_keys != adapter_cast_keys:
    errors.append("cast: JNI error IDs and the UI adapter allowlist differ")
for key in native_cast_keys:
    if key not in base:
        errors.append(f"cast: missing native error resource {key}")

renderer_native_source = (ROOT / "cast/upnp/android/src/main/cpp/lyn_upnp_renderer_jni.cpp").read_text()
native_renderer_keys = set(re.findall(r'"lyn_ui:(renderer_[a-z0-9_]+)"', renderer_native_source))
renderer_adapter_source = (ROOT / "cast/upnp/android/src/main/kotlin/top/iwesley/lyn/music/cast/upnp/android/NativeRendererErrorText.kt").read_text()
renderer_keys_block = re.search(r'nativeRendererErrorResources\s*=\s*mapOf\((.*?)\)', renderer_adapter_source, re.S)
adapter_renderer_keys = set(re.findall(r'"(renderer_[a-z0-9_]+)"', renderer_keys_block.group(1))) if renderer_keys_block else set()
if not native_renderer_keys or native_renderer_keys != adapter_renderer_keys:
    errors.append("renderer: JNI error IDs and the UI adapter allowlist differ")
for key in native_renderer_keys:
    if key not in base:
        errors.append(f"renderer: missing native error resource {key}")
for message in re.findall(r'(?:return\s+|Error\(env,\s*)"([^"\n]+)"', renderer_native_source):
    if HAN.search(message):
        errors.append("renderer: native source still contains hardcoded Chinese errors")

native = {}
for locale in ("en", "zh-Hans", "zh-Hant"):
    path = ROOT / f"macosApp/macosApp/{locale}.lproj/Localizable.strings"
    pairs = re.findall(r'^"([^"\n]+)"\s*=\s*("(?:\\.|[^"\\])*");', path.read_text(), re.M)
    native[locale] = {key: json.loads(value) for key, value in pairs}
    for key in native[locale]:
        validate_resource_key(key, f"macOS/{locale}")
    if len(pairs) != len(native[locale]):
        errors.append(f"macOS/{locale}: duplicate keys")
if any(native[locale].keys() != native["en"].keys() for locale in native):
    errors.append("macOS: native keys differ")
for path in (ROOT / "macosApp/macosApp").glob("*.swift"):
    for key in re.findall(r'\.text\("([^"\n]+)"\)', path.read_text()):
        if key not in native["en"]:
            errors.append(f"macOS: missing native resource {key}")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"UI localization checks passed: {len(base)} shared keys, "
      f"{sum(kind == 'plurals' for kind, _ in base.values())} plurals, "
      f"{len(native['en'])} macOS native keys, 3 complete languages.")
