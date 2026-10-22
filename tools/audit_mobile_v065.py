#!/usr/bin/env python3
"""Inventory pinned Android/mobile Windows UI and animation call sites.

Reads Android from git objects, never its working tree or a moving tag. This is
an audit index with source hashes, not a pixel/behavior parity verdict.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('android', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
sha = '53244b9d6b21d7e3a609e8cb11876efef70f218b'
repo = Path(__file__).resolve().parents[1]
base = 'android/app/src/main/kotlin/com/lladlam/melox/ui/'
target = 'desktop/src/desktopMain/kotlin/melox/ui/'
pairs = {
    'home': ('discovery/MeloXDiscoveryScreens.kt', ['screens/HomeScreen.kt']),
    'explore': ('discovery/MeloXDiscoveryScreens.kt', ['screens/ExploreScreen.kt']),
    'search': ('search/SearchScreen.kt', ['screens/SearchScreen.kt']),
    'library': ('library/LibraryScreen.kt', ['screens/MeloXLibraryScreen.kt', 'screens/MeloXLibraryPlaylistDetail.kt', 'screens/PlaylistDetailScreen.kt']),
    'page-motion': ('MeloXPageTransitions.kt', ['navigation/MeloXDetailMotion.kt']),
    'motion-specs': ('animation/MeloXMotion.kt', ['foundation/MeloXMotion.kt']),
    'mini-player': ('player/MeloXIOSMiniPlayer.kt', ['screens/MeloXMiniPlayer.kt']),
    'player-host': ('player/MeloXIOSNowPlayingSharedHost.kt', ['screens/MeloXNowPlaying.kt', 'screens/MeloXIOSPlayerScene.kt']),
    'player-motion': ('player/MeloXPlayerTransitionSpec.kt', ['screens/MeloXPlayerTransition.kt']),
    'player-landscape': ('player/MeloXIOSNowPlayingScene.kt', ['screens/MeloXIOSLandscapePlayerScene.kt']),
    'lyrics': ('player/MeloXIOSLyricsPanel.kt', ['screens/MeloXPlayerLyrics.kt']),
    'lyric-styles': ('player/MeloXAlternativeLyricsPanels.kt', ['screens/MeloXAlternativeLyricsPanels.kt']),
    'song-actions': ('player/MeloXSongActionsOverlay.kt', ['screens/MeloXSongActionsOverlay.kt']),
    'podcasts': ('podcast/MeloXPodcastScreens.kt', ['screens/PodcastsScreen.kt']),
    'messages': ('messages/MessagesScreen.kt', ['screens/MessagesScreen.kt']),
    'cloud': ('cloud/MeloXCloudMusicScreen.kt', ['screens/CloudScreen.kt']),
    'settings': ('settings/SettingsScreen.kt', ['screens/SettingsScreen.kt']),
    'provider-services': ('provider/ProviderServicesScreen.kt', ['screens/ProviderServicesScreen.kt']),
    'app-navigation': ('MeloXApp.kt', ['MeloXApp.kt', 'navigation/MeloXBottomChrome.kt']),
    'split-layout': ('layout/MeloXPositionedChrome.kt', ['layout/MeloXPositionedChrome.kt']),
}
pattern = re.compile(r'\b(AnimatedContent|AnimatedVisibility|sharedElement|sharedBounds|MeloXInterruptiblePage|MeloXDetailOverlay|meloXPageRecede|animateFloatAsState|animateDpAsState|animateTo|SeekableTransitionState)\s*\(')


def describe(path, content):
    text = content.decode('utf-8')
    sites = []
    in_comment = False
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith('/*'):
            in_comment = True
        if in_comment:
            if '*/' in line:
                in_comment = False
            continue
        if stripped.startswith('//'):
            continue
        for match in pattern.finditer(line.split('//', 1)[0]):
            sites.append({'line': number, 'symbol': match[1]})
    return {'path': path, 'sha256': hashlib.sha256(content).hexdigest(), 'lines': len(text.splitlines()), 'motion_sites': sites}


records = {}
for area, (source, destinations) in pairs.items():
    source_path = base + source
    source_bytes = subprocess.check_output(['git', 'show', f'{sha}:{source_path}'], cwd=args.android)
    windows = []
    for destination in destinations:
        path = repo / (target + destination)
        windows.append(describe(target + destination, path.read_bytes()) if path.exists() else {'path': target + destination, 'present': False})
    records[area] = {'android': describe(source_path, source_bytes), 'windows': windows}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps({'android_sha': sha, 'scope': 'source inventory; manual findings in MOBILE_UI_AUDIT.md; not a parity verdict',
    'areas': records}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Indexed {len(records)} mobile UI areas against {sha}')
