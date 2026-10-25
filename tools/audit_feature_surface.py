#!/usr/bin/env python3
"""Read live Android and both Windows presentations; source inventory, not runtime parity."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('android', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
android = args.android.resolve()
source = android / 'android/app/src/main/kotlin/com/lladlam/melox'
windows = repo / 'desktop/src/desktopMain/kotlin/melox/ui'
shared = repo / 'shared/src/commonMain/kotlin/melox'

# Explicit mapping is necessary: Android Activities are Windows overlays/tool windows.
areas = {
    'home': ('ui/discovery/*', 'screens/HomeScreen.kt', 'desktop/MeloXDesktopHome*.kt', 'music/provider/*'),
    'explore': ('ui/discovery/*', 'screens/ExploreScreen.kt', 'desktop/MeloXDesktopExplore.kt', 'music/provider/*'),
    'search': ('ui/search/*', 'screens/*Search*.kt', 'desktop/MeloXDesktopSearch.kt', 'music/*Search*.kt'),
    'library': ('ui/library/*', 'screens/*Library*.kt', 'desktop/MeloXDesktopLibrary*.kt', 'music/provider/*'),
    'playlist': ('ui/library/*', 'screens/*Playlist*.kt', 'desktop/MeloXDesktopCollection.kt', 'music/provider/ProviderCollectionPaging.kt'),
    'album-artist': ('ui/collection/*', 'screens/*DetailScreen.kt', 'desktop/*Detail.kt', 'network/NeteaseCollectionDetailsClient.kt'),
    'song-detail-wiki': ('ui/song/*', 'screens/*SongDetail*.kt', 'desktop/MeloXDesktopSongDetail.kt', 'network/*Song*.kt'),
    'song-actions': ('ui/player/*Actions*.kt', 'screens/*SongActions*.kt', 'desktop/MeloXDesktopSongMenu.kt', 'music/provider/ProviderSongActions.kt'),
    'downloads': ('ui/library/MeloXBatchDownloadSheet.kt', 'screens/DownloadsScreen.kt', 'desktop/MeloXDesktopLibraryPages.kt', 'download/*'),
    'cloud': ('ui/cloud/*', 'screens/CloudScreen.kt', 'desktop/MeloXDesktopLibraryPages.kt', 'network/*Cloud*.kt'),
    'podcasts': ('ui/podcast/*', 'screens/PodcastsScreen.kt', 'desktop/MeloXDesktopPodcasts.kt', 'network/*Podcast*.kt'),
    'messages': ('ui/messages/*', 'screens/MessagesScreen.kt', 'desktop/MeloXDesktopMessages.kt', 'network/*Message*.kt'),
    'accounts': ('ui/account/*', 'screens/*Login*.kt', 'desktop/MeloXDesktopLogin.kt', 'provider/**/*Session*.kt'),
    'services': ('ui/provider/*Services*.kt', 'screens/ProviderServicesScreen.kt', 'desktop/MeloXDesktopProviderServices.kt', 'music/provider/*'),
    'youtube-channels': ('ui/provider/YouTubeAccountControls.kt', 'screens/*YouTube*.kt', 'desktop/*YouTube*.kt', 'provider/youtubemusic/*'),
    'kugou-lite-rewards': ('ui/settings/KugouLiteVipSettings.kt', 'screens/*KugouLite*.kt', 'desktop/*KugouLite*.kt', 'provider/kugou/*Lite*.kt'),
    'player': ('ui/player/MeloXIOSNowPlaying*.kt', 'screens/MeloX*Player*.kt', 'desktop/MeloXDesktopNowPlaying.kt', 'playback/PlaybackCommands.kt'),
    'queue': ('ui/player/MeloXQueuePanel.kt', 'screens/*Queue*.kt', 'desktop/MeloXDesktopNowPlaying.kt', 'playback/PlaybackCommands.kt'),
    'lyrics': ('ui/player/*Lyrics*.kt', 'screens/*Lyrics*.kt', 'desktop/MeloXDesktopLyrics.kt', 'lyrics/*'),
    'lyric-sharing': ('ui/player/MeloXLyricShare*.kt', 'screens/*LyricShare*.kt', 'desktop/MeloXDesktopLyrics.kt', 'lyrics/*Share*.kt'),
    'custom-background': ('ui/player/MeloXCustomBackground.kt', 'screens/*Background*.kt', 'desktop/*Background*.kt', 'settings/*Background*.kt'),
    'recognition': ('ui/recognition/*', 'screens/PlatformSettingsPages.kt', 'desktop/MeloXDesktopTools.kt', 'recognition/*'),
    'listen-together': ('ui/player/MeloXListenTogetherInviteActivity.kt', 'screens/PlatformSettingsPages.kt', 'desktop/MeloXDesktopTools.kt', 'playback/*Together*.kt'),
    'lan-dlna': ('ui/player/*Output*.kt', 'screens/MeloXLanOutputPanel.kt', 'desktop/MeloXLanDialog.kt', 'lan/*'),
    'miplay-usb': ('miplay/*', 'screens/*Output*.kt', 'desktop/*Lan*.kt', 'miplay/*'),
    'settings': ('ui/settings/SettingsScreen.kt', 'screens/*Settings*.kt', 'desktop/MeloXDesktopSettingsControls.kt', 'settings/*'),
    'privacy': ('ui/settings/SettingsScreen.kt', 'screens/PrivacySettings.kt', 'screens/SettingsScreen.kt', 'recommendation/*'),
    'equalizer-automix': ('ui/settings/SettingsScreen.kt', 'screens/*PlaybackSettings.kt', 'screens/EqualizerSettings.kt', 'playback/*'),
    'autosync-models': ('ui/settings/AutoSyncModelPanel.kt', 'screens/*AutoSync*.kt', 'desktop/*AutoSync*.kt', 'lyrics/*AutoSync*.kt'),
    'remote-config': ('core/remoteconfig/*', 'screens/RemoteConfigSettings.kt', 'screens/SettingsScreen.kt', 'remoteconfig/*'),
    'onboarding': ('ui/MeloXOnboarding.kt', 'MeloXOnboarding.kt', 'desktop/MeloXDesktopOnboarding.kt', 'legal/*'),
    'about-legal': ('ui/legal/*', 'screens/*About*.kt', 'desktop/MeloXDesktopAbout.kt', 'legal/*'),
}

def describe(root, pattern):
    result = []
    for path in sorted(root.glob(pattern)):
        if path.is_file():
            data = path.read_bytes()
            result.append({'path': str(path.relative_to(android if root == source else repo)),
                           'sha256': hashlib.sha256(data).hexdigest()})
    return result

inventory = {name: dict(zip(('android', 'mobile', 'desktop', 'business'),
    (describe(source, patterns[0]), describe(windows, patterns[1]),
     describe(windows, patterns[2]), describe(shared, patterns[3])))) for name, patterns in areas.items()}
key_pattern = re.compile(r'"((?:lyrics_|player_|general_|playback_|feature_|placement_|content_|library_|tab_|home_|download_|experimental_)[a-z0-9_]+)"')
android_settings = (source / 'ui/settings/MeloXSettingsPreferences.kt').read_text()
windows_settings = '\n'.join(p.read_text() for p in (shared / 'settings').glob('*.kt'))
empty_handlers = []
for path in sorted(windows.rglob('*.kt')):
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'onClick\s*=\s*\{\s*\}', line):
            empty_handlers.append({'path': str(path.relative_to(repo)), 'line': number,
                                   'code': line.strip(), 'status': 'REVIEW: may be a deliberate modal input shield'})
result = {
    'checked_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'android_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=android, text=True).strip(),
    'scope': 'Live working-tree source inventory. Presence is not functional or visual parity; missing pattern matches require manual routing review.',
    'areas': inventory,
    'all_android_ui_sources': describe(source, 'ui/**/*.kt'),
    'all_windows_ui_sources': describe(windows, '**/*.kt'),
    'all_windows_business_sources': describe(shared, '**/*.kt'),
    'android_setting_keys_absent_from_windows_settings': sorted(set(key_pattern.findall(android_settings)) - set(key_pattern.findall(windows_settings))),
    'empty_click_handlers_for_review': empty_handlers,
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'areas': len(inventory), 'setting_keys_for_review': result['android_setting_keys_absent_from_windows_settings'],
                  'empty_handlers_for_review': len(empty_handlers)}, ensure_ascii=False, indent=2))
