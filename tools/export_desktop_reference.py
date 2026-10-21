#!/usr/bin/env python3
"""Export the selected native Desktop labels from the exact Git tree, never its checkout."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

REVISION = 'a41ea0e1e1fa69fb543d827b853fe3aff5420951'
KEYS = '''ui.navigation.search ui.navigation.home ui.navigation.explore ui.navigation.podcasts
ui.navigation.library ui.navigation.library.history ui.navigation.library.liked_songs
ui.navigation.library.liked_playlists ui.library.artists.title ui.navigation.library.liked_albums
ui.navigation.library.podcasts ui.navigation.downloads ui.navigation.cloud
ui.desktop.sidebar.not_signed_in ui.desktop.sidebar.fallback_avatar_initial
ui.desktop.commands.show_lyrics ui.desktop.commands.show_queue ui.common.lyrics ui.player.queue
ui.player.previous ui.player.next ui.player.shuffle_on ui.player.shuffle_off
ui.common.close ui.desktop.player.not_playing ui.home.section.quick_actions'''.split()
KEYS += [f'ui.home.action.{action}{suffix}' for action in
         ['daily_songs', 'heart_mode', 'private_roaming', 'hot_songs', 'private_radar', 'similar_songs']
         for suffix in ['', '.subtitle', '.eyebrow']]
KEYS += ['ui.home.section.recommended_playlists', 'ui.home.section.tailored', 'ui.common.play', 'ui.player.pause',
         'ui.player.volume', 'ui.player.repeat', 'ui.player.progress']
KEYS += ['ui.common.more', 'ui.common.clear', 'ui.common.history', 'ui.player.queue_empty',
         'ui.desktop.player.autoplay', 'ui.desktop.player.continue_playing', 'ui.settings.automix.title',
         'ui.desktop.player.remove_from_queue']
KEYS += ['ui.common.collapse', 'ui.common.playlist', 'ui.common.album', 'ui.common.back',
         'ui.common.song_count', 'ui.common.metadata_separator', 'ui.common.play_count',
         'ui.player.shuffle', 'ui.desktop.collection.favorite', 'ui.desktop.collection.unfavorite',
         'ui.desktop.collection.download_all', 'ui.desktop.collection.disc', 'ui.common.share',
         'ui.common.retry', 'ui.error.music_content_load_failed', 'ui.error.unknown',
         'ui.playlists.loading', 'ui.album.loading', 'ui.common.play_named']
KEYS += ['ui.error.favorite_failed']
KEYS += ['ui.common.view_all', 'ui.albums.new.title', 'ui.home.section.podcast_recommendations',
         'ui.desktop.pagination.previous', 'ui.desktop.pagination.next', 'ui.error.home.load_failed']
KEYS += ['ui.desktop.settings.account', 'ui.messages.private.title', 'ui.desktop.account.settings',
         'ui.settings.account.logout', 'ui.settings.account.logout.confirmation', 'ui.common.cancel',
         'ui.common.done', 'ui.common.login', 'ui.account.login_netease', 'ui.desktop.account.login_message']
KEYS += ['ui.common.title_detail_separator', 'ui.desktop.player.playback_progress', 'ui.desktop.player.toggle_mute']
KEYS += ['ui.player.play_next', 'ui.player.add_to_queue', 'ui.playlists.add_to',
         'ui.playlists.create.title', 'ui.playlists.create.name', 'ui.playlists.create.private', 'ui.playlists.create.action',
         'ui.playlists.none_available', 'ui.playlists.remove_song', 'ui.playlists.remove_song.message',
         'ui.playlists.add_failed', 'ui.library.error.title', 'ui.song.unlike', 'ui.song.like',
         'ui.common.download', 'ui.song.view_information', 'ui.artist.go_to_generic', 'ui.common.ok']
KEYS += ['ui.song.detail.title', 'ui.comments.title', 'ui.desktop.song.content', 'ui.common.songs',
         'ui.desktop.lyrics.unavailable', 'ui.comments.empty', 'ui.song.loading']
KEYS += ['ui.desktop.artist.latest_releases', 'ui.artist.popular_songs', 'ui.artist.loading',
         'ui.common.artist', 'ui.desktop.artist.follow_failed', 'ui.desktop.artist.following', 'ui.desktop.artist.follow']
KEYS += ['ui.desktop.settings.tab.general', 'ui.desktop.settings.tab.features', 'ui.settings.playback.section.playback',
         'ui.desktop.settings.tab.files', 'ui.desktop.settings.tab.advanced', 'ui.desktop.settings.restore_defaults_ellipsis',
         'ui.desktop.settings.restore_defaults.confirmation', 'ui.desktop.settings.restore_defaults', 'ui.desktop.settings.restore_defaults.message']
KEYS += ['ui.podcast.sort.newest', 'ui.podcast.sort.oldest']
KEYS += [f'ui.settings.audio_quality.{quality}' for quality in
         ['standard', 'high', 'lossless', 'hi_res', 'hd_surround', 'immersive_surround', 'master']]
SOURCES = [
    'MeloXDesktop/App/DesktopRootView.swift',
    'MeloXDesktop/App/DesktopNavigation.swift',
    'MeloXDesktop/App/DesktopMainWindowConfiguration.swift',
    'MeloXDesktop/Features/Sidebar/DesktopSidebar.swift',
    'MeloXDesktop/Features/Sidebar/DesktopSidebarVisibilityLock.swift',
    'MeloXDesktop/Features/Player/DesktopBottomPlayer.swift',
    'MeloXDesktop/Features/Player/BottomBar/DesktopBottomPlayerMetrics.swift',
    'MeloXDesktop/Features/Player/DesktopPlaybackControls.swift',
    'MeloXDesktop/Features/Player/BottomBar/DesktopBottomMetadataSlot.swift',
    'MeloXDesktop/Features/Player/DesktopPlayerSidePanel.swift',
    'MeloXDesktop/Features/Player/DesktopNowPlayingLayout.swift',
    'MeloXDesktop/Features/Player/DesktopNowPlayingPlayerColumn.swift',
    'MeloXDesktop/Features/Player/DesktopNowPlayingArtwork.swift',
    'MeloXDesktop/Features/Player/DesktopNowPlayingProgress.swift',
    'MeloXDesktop/Features/Player/DesktopNowPlayingPage.swift',
    'MeloXDesktop/Features/Player/DesktopNowPlayingWindow.swift',
    'MeloXDesktop/Features/Player/Chrome/DesktopNowPlayingGlass.swift',
    'MeloXDesktop/Features/Player/Chrome/DesktopNowPlayingVolumeControl.swift',
    'MeloXDesktop/Core/Lyrics/AppleMusicLyricsTypographyProfile.swift',
    'MeloXDesktop/Core/Lyrics/AppleMusicLyricsMotionProfile.swift',
    'MeloXDesktop/Features/Player/DesktopLyricsLayoutMetrics.swift',
    'MeloXDesktop/Features/Player/Queue/DesktopQueueView.swift',
    'MeloXDesktop/Features/Player/Queue/DesktopQueueMetrics.swift',
    'MeloXDesktop/Features/Player/Queue/DesktopQueueRow.swift',
    'MeloXDesktop/Features/Home/DesktopHomeView.swift',
    'MeloXDesktop/Features/Home/DesktopHomeQuickActionsView.swift',
    'MeloXDesktop/Features/Home/DesktopHomePagingShelf.swift',
    'MeloXDesktop/Shared/DesktopMediaCard.swift',
    'MeloXDesktop/Features/Account/DesktopAccountView.swift',
    'MeloXDesktop/Features/Detail/DesktopRouteView.swift',
    'MeloXDesktop/Features/Detail/DesktopPlaylistDetailView.swift',
    'MeloXDesktop/Features/Detail/DesktopAlbumDetailView.swift',
    'MeloXDesktop/Shared/DesktopTrackRow.swift',
    'MeloXDesktop/Features/Playlist/PlaylistCreationSheet.swift',
    'MeloXDesktop/Features/Detail/DesktopSongDetailView.swift',
    'MeloXDesktop/Features/Detail/DesktopArtistDetailView.swift',
]
EXPORTED_SURFACES = [
    'MeloXDesktop/Features/Settings/DesktopSettingsView.swift',
    'MeloXDesktop/Features/Explore/DesktopDiscoveryView.swift',
    'MeloXDesktop/Features/Explore/DesktopPodcastCategoryView.swift',
    'MeloXDesktop/Features/Detail/DesktopPodcastDetailView.swift',
    'MeloXDesktop/Features/SongRecognition/DesktopSongRecognitionView.swift',
    'MeloXDesktop/Features/ListenTogether/DesktopListenTogetherView.swift',
    'MeloXDesktop/Features/About/DesktopAboutView.swift',
    'MeloXDesktop/Features/About/DesktopProjectLicensesView.swift',
    'MeloXDesktop/Features/Onboarding/DesktopOnboardingDialog.swift',
    'MeloXDesktop/Core/Settings/Application/AppAppearance.swift',
    'MeloXDesktop/Core/Settings/Recognition/SongRecognitionPreferences.swift',
    'MeloXDesktop/Features/Library/DesktopLibraryView.swift',
    'MeloXDesktop/Features/Search/DesktopSearchView.swift',
    'MeloXDesktop/Features/Messages/DesktopMessagesView.swift',
    'MeloXDesktop/Features/Account/DesktopLoginView.swift',
]
SOURCES += EXPORTED_SURFACES
KEYS += ['ui.error.listen_together.missing_room_id', 'ui.recognition.failed']
KEYS += ['ui.downloads.delete', 'ui.cloud.delete', 'ui.common.refresh']
KEYS += ['ui.search.prompt', 'ui.common.netease_cloud_music', 'ui.desktop.search.failed',
         'ui.desktop.search.empty.title', 'ui.desktop.search.empty.message']
KEYS += [f'ui.search.kind.{kind}' for kind in ['songs', 'albums', 'artists', 'playlists', 'podcasts']]
KEYS += ['ui.account.login_required', 'ui.desktop.messages.login_message', 'ui.messages.empty',
         'ui.messages.empty.message', 'ui.desktop.messages.load_failed', 'ui.desktop.messages.conversations',
         'ui.desktop.messages.select_conversation', 'ui.desktop.messages.select_conversation.message',
         'ui.desktop.messages.conversation_failed', 'ui.desktop.messages.input_to', 'ui.messages.start_private_message',
         'ui.messages.send_failed', 'ui.desktop.messages.refresh',
         'ui.messages.no_messages', 'ui.messages.no_messages.message', 'ui.messages.search_following',
         'ui.messages.contacts_not_found', 'ui.messages.private_send_failed']

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('repository', type=Path)
parser.add_argument('--verify', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]

def blob(path):
    return subprocess.check_output(['git', '-C', str(args.repository), 'show', REVISION + ':' + path])

for source in EXPORTED_SURFACES:
    KEYS += re.findall(r'"(ui\.[a-zA-Z0-9_.]+)"', blob(source).decode('utf-8'))
KEYS = sorted(set(KEYS))

def save(path, data):
    target = root / path
    if args.verify:
        assert target.read_bytes() == data, str(path)
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

hashes = {}
logo_source = 'MeloXDesktop/Assets.xcassets/MeloXLogo.imageset/MeloXLogo.png'
logo = blob(logo_source)
save('desktop/src/main/resources/parity/desktop-logo.png', logo)
hashes[logo_source] = hashlib.sha256(logo).hexdigest()
legal_resources = {}
for relative in ['AudioFingerprint/NeteaseAudioFingerprint-NOTICE.txt', 'AudioFingerprint/NeteaseAPI-LICENSE.txt', 'Models/BeatNet/BeatNet-NOTICE.md']:
    path = 'MeloXDesktop/Resources/' + relative
    data = blob(path)
    target = 'parity/legal/' + Path(relative).name
    save('desktop/src/main/resources/' + target, data)
    hashes[path] = hashlib.sha256(data).hexdigest()
    legal_resources[target] = hashlib.sha256(data).hexdigest()
for lang in ['en', 'zh-Hans']:
    path = f'MeloXLocalization/{lang}.lproj/Localizable.strings'
    raw = blob(path)
    hashes[path] = hashlib.sha256(raw).hexdigest()
    values = {k: json.loads('"' + v + '"') for k, v in re.findall(r'"([^"\\]+)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;', raw.decode('utf-8'))}
    assert set(KEYS) <= values.keys(), set(KEYS) - values.keys()
    text = '# Exact Desktop reference ' + REVISION + '\n'
    # Java Properties consumes unescaped leading whitespace after '='.
    def property_value(value):
        encoded = value.replace('\\', '\\\\').replace('\n', '\\n')
        encoded = re.sub(r'^ +', lambda m: '\\u0020' * len(m[0]), encoded)
        return re.sub(r' +$', lambda m: '\\u0020' * len(m[0]), encoded)
    text += ''.join(k + '=' + property_value(values[k]) + '\n' for k in sorted(KEYS))
    resource = f'desktop/src/main/resources/parity/desktop-reference-{lang}.properties'
    save(resource, text.encode('utf-8'))
resources = {f'parity/desktop-reference-{lang}.properties': hashlib.sha256((root / f'desktop/src/main/resources/parity/desktop-reference-{lang}.properties').read_bytes()).hexdigest() for lang in ['en', 'zh-Hans']}
resources['parity/desktop-logo.png'] = hashlib.sha256(logo).hexdigest()
resources.update(legal_resources)
# The exact SF Symbols subset is an implementation resource, not a source label
# export. Pinning it here makes the packaged-resource audit cover the icons too.
resources['fonts/sf_pro_subset.ttf'] = hashlib.sha256(
    (root / 'desktop/src/main/resources/fonts/sf_pro_subset.ttf').read_bytes()).hexdigest()
for path in sorted((root / 'desktop/src/main/resources/fonts').glob('sf_pro_*.ttf')):
    resources['fonts/' + path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
for path in SOURCES:
    hashes[path] = hashlib.sha256(blob(path)).hexdigest()
audit = {'repository': 'https://github.com/lladlam/melox', 'commit': REVISION,
    'verified_against_pure_export': True, 'source_sha256': hashes, 'resource_sha256': resources,
    'keys': sorted(KEYS), 'scope': 'Native macOS Desktop shell reference; separate from pinned Android v0.6.5'}
save('parity/desktop-a41ea0e/source-audit.json', (json.dumps(audit, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
print('PASS exact Desktop source and', len(KEYS), 'labels in two source locales')
