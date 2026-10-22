#!/usr/bin/env python3
"""Reproducible source inventory against a pinned Android commit (no network).

Source identity is evidence, not runtime/visual acceptance. Never promote a
different implementation to MATCH based on filenames or declared capabilities.
"""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ANDROID_SHA = "94e17c920bf554966584955afa2bd2ee92580a8b"
WINDOWS_SHA = "87ba7992b44c5e8c844a5db2a61f2149148eed26"


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args]).decode()


def normalized(text):
    return text.replace("com.lladlam.melox.core.", "melox.").replace("com.lladlam.melox.", "melox.").strip()


def symbols(text):
    return sorted(set(re.findall(r"\b(?:class|interface|object|fun)\s+(?:[\w.]+\.)?([A-Za-z_]\w*)", text)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--android", type=Path, default=ROOT.parent / "MeloX-Android")
    args = parser.parse_args()
    paths = git(args.android, "ls-tree", "-r", "--name-only", ANDROID_SHA).splitlines()
    android = {p: git(args.android, "show", f"{ANDROID_SHA}:{p}") for p in paths
               if p.endswith((".kt", ".xml")) and ("/src/main/" in p or "/src/test/" in p)}
    windows = {str(p.relative_to(ROOT)): p.read_text(encoding='utf-8') for folder in ("shared/src", "desktop/src")
               for p in (ROOT / folder).rglob("*") if p.suffix in (".kt", ".xml")}
    by_name = collections.defaultdict(list)
    for p in windows:
        by_name[Path(p).name].append(p)
    aliases = {
        "MeloXPlaybackService.kt": ["PlaybackCommands.kt", "AudioPlayer.kt", "MeloXDesktopAutoplay.kt"],
        "MeloXProviderLyricsLoader.kt": ["MeloXLyricsLoad.kt"],
        "MeloXIOSMiniPlayer.kt": ["MeloXMiniPlayer.kt"],
        "MeloXIOSNowPlayingV2.kt": ["MeloXNowPlaying.kt"],
        "MeloXIOSNowPlayingScene.kt": ["MeloXIOSPlayerScene.kt", "MeloXIOSLandscapePlayerScene.kt"],
        "MeloXIOSNowPlayingSharedHost.kt": ["MeloXNowPlaying.kt", "MeloXIOSPlayerScene.kt"],
        "MeloXIOSLyricsPanel.kt": ["MeloXPlayerLyrics.kt", "MeloXGlyphLyricText.kt", "MeloXLyricCascade.kt", "MeloXLyricPresentation.kt"],
        "MeloXNowPlayingCoreControls.kt": ["MeloXNowPlaying.kt", "MeloXPlayerQuality.kt"],
        "MeloXQualitySelectionOverlay.kt": ["MeloXPlayerQuality.kt"],
        "MeloXLyricShareDialog.kt": ["MeloXPlayerLyrics.kt"],
        "MeloXLyricShareImage.kt": ["MeloXPlayerLyrics.kt"],
        "MeloXDiscoveryScreens.kt": ["HomeScreen.kt", "ExploreScreen.kt"],
        "LibraryScreen.kt": ["MeloXLibraryScreen.kt"],
        "MeloXIosComponents.kt": ["MeloXComponents.kt"],
        "MeloXMiSansFont.kt": ["MeloXFont.kt"],
        "MeloXTheme.kt": ["MeloXMaterialTheme.kt", "MeloXApp.kt"],
    }
    files = []
    for path, text in sorted(android.items()):
        candidates = by_name[Path(path).name].copy()
        for alias in aliases.get(Path(path).name, []):
            candidates.extend(by_name[alias])
        exact = [p for p in candidates if normalized(text) == normalized(windows[p])]
        files.append({"android": path, "windows": candidates,
                       "status": "PARTIAL" if candidates else "MISSING",
                       "source_identical": bool(exact),
                       "evidence": "package-normalized source identical; runtime acceptance still required" if exact else
                      "mapped implementation differs; runtime/visual acceptance required" if candidates else
                      "no filename/explicit alias mapping; inspect equivalents before implementation",
                      "android_sha256": hashlib.sha256(text.encode()).hexdigest(),
                      "android_lines": len(text.splitlines()),
                      "windows_lines": {p: len(windows[p].splitlines()) for p in candidates},
                       "symbols": [{"name": s, "status": "PARTIAL" if exact or any(s in symbols(windows[p]) for p in candidates) else "MISSING"}
                                  for s in symbols(text)]})
    findings = []
    pattern = re.compile(r"\b(mock\w*|placeholder|fake|dummy|TODO|FIXME|hardcoded)\b|onClick\s*=\s*\{\s*\}|not yet implemented|尚未接入", re.I)
    for path, text in windows.items():
        for n, line in enumerate(text.splitlines(), 1):
            if pattern.search(line):
                findings.append({"path": path, "line": n, "text": line.strip(),
                                 "status": "PARTIAL", "evidence": "review hit; not proof of a defect"})
    methods = {
        "song search": "searchSongs", "playlist search": "searchPlaylists", "album search": "searchAlbums",
        "artist search": "searchArtists", "home/recommendations": "homeFeed", "rankings": "rankingTracks",
        "playlist detail": "playlistDetail", "album detail": "albumDetail", "artist detail": "artistDetail",
        "playback": "resolvePlayback", "lyrics": "lyrics", "favorite/unfavorite": "setFavorite",
        "user playlists": "userPlaylists", "create playlist": "createPlaylist", "rename playlist": "renamePlaylist",
        "delete playlist": "deletePlaylist", "add track": "addTrackToPlaylist", "remove track": "removeTrackFromPlaylist",
        "reorder track": "reorderPlaylistTrack", "saved albums": "savedAlbums", "followed artists": "followedArtists",
        "download": "resolveDownload", "account summary": "accountSummary",
    }
    providers = []
    for name in ("Netease", "QQMusic", "Kugou", "Kuwo", "Spotify", "YouTubeMusic", "Bilibili", "Jellyfin", "Local", "LxUser", "AppleMusic"):
        ap = next((p for p in android if Path(p).name == name + "Provider.kt"), None)
        wp = next(iter(by_name[name + "Provider.kt"]), None)
        atext, wtext = android.get(ap, ""), windows.get(wp, "")
        for capability, method in methods.items():
            a = bool(re.search(r"\bfun\s+" + method + r"\s*\(", atext))
            w = bool(re.search(r"\bfun\s+" + method + r"\s*\(", wtext))
            providers.append({"provider": name, "capability": capability, "android": ap, "windows": wp,
                              "android_declares": a, "windows_declares": w,
                              "status": "PARTIAL" if w or not a else "MISSING",
                              "evidence": "method presence only; native calls, session and UI gating require verification" if a else
                              "not declared in Android provider class; inspect delegated/platform implementation"})
        for capability in ("login", "session", "fallback"):
            providers.append({"provider": name, "capability": capability, "status": "PARTIAL",
                              "evidence": "requires session/client/playback and UI call-chain review"})
    specs = []
    for path, text in android.items():
        if "/ui/" not in path:
            continue
        for n, line in enumerate(text.splitlines(), 1):
            if re.search(r"\b(?:dampingRatio|stiffness|durationMillis|delayMillis|visibilityThreshold)\s*=|\d+(?:\.\d+)?\.(?:dp|sp)\b", line):
                specs.append({"android": path, "line": n, "spec": line.strip(), "status": "PARTIAL"})
    out = ROOT / "parity" / ANDROID_SHA[:7]
    out.mkdir(parents=True, exist_ok=True)
    data = {"android_sha": ANDROID_SHA, "windows_base_sha": WINDOWS_SHA,
            "windows_worktree": git(ROOT, "status", "--porcelain").splitlines(),
            "acceptance": "source scan only; no runtime, account, visual or Windows OS acceptance implied",
            "files": files, "providers": providers, "review_findings": findings, "android_design_specs": specs}
    (out / "inventory.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    counts = collections.Counter(f["status"] for f in files)
    report = ["# Pinned source parity inventory", "", f"Android: `{ANDROID_SHA}`", f"Windows base: `{WINDOWS_SHA}`", "",
              "This is a conservative source inventory. Source identity is recorded separately and never promotes a row to MATCH. "
              "PARTIAL includes unverified implementations. MISSING means no mapped file/symbol, not proof that an equivalent cannot exist. "
              "Runtime, authenticated provider, Windows native, visual and animation acceptance are separate evidence.", "",
              f"Files: {dict(counts)}. Review hits: {len(findings)}. Extracted Android design/animation lines: {len(specs)}.", "",
              "## File matrix", "", "| Android | Windows | Status |", "| --- | --- | --- |"]
    report.extend(f"| `{f['android']}` | {'<br>'.join('`'+p+'`' for p in f['windows']) or '—'} | {f['status']} |" for f in files)
    report += ["", "## Provider capability matrix", "", "Method declarations alone never qualify as MATCH.", "",
               "| Provider | Capability | Android declares | Windows declares | Status |", "| --- | --- | --- | --- | --- |"]
    report.extend(f"| {p['provider']} | {p['capability']} | {p.get('android_declares', '—')} | {p.get('windows_declares', '—')} | {p['status']} |" for p in providers)
    (out / "MATRIX.md").write_text("\n".join(report) + "\n")
    print(f"{out}: {dict(counts)}, {len(providers)} provider rows, {len(findings)} review hits, {len(specs)} design specs")


if __name__ == "__main__":
    main()
