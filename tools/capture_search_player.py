#!/usr/bin/env python3
"""Replay production search/player UI. Supply an isolated, onboarded capture home.

MELOX_GRADLE selects Gradle; MELOX_FIXTURES selects generated WAV/artwork/TTML;
MELOX_CAPTURE_JVMARGS optionally supplies the local JVM/proxy configuration.
Actual network failures remain failures unless the explicit unavailable scenario is selected.
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
if not gradle:
    raise SystemExit('Set MELOX_GRADLE to Gradle 9.5.1')
name, home = sys.argv[1:3]
fixtures = Path(os.environ.get('MELOX_FIXTURES', '/tmp/opencode/melox-search-player-fixtures')).resolve()
output = repo / f'parity/3f877ea/evidence/search-player-{name}-ui'
output.mkdir(parents=True, exist_ok=True)
args = ['--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark', '--output', str(output), '--wait-ms', '650', '--startup-wait-ms', '1000', '--frames-ms', '0,160,400']
if name == 'eq':
    steps = 'open:tap@settings-open-playback;reveal-enable:reveal@equalizer-enabled;enable:tap@equalizer-enabled;reveal-rock:reveal@equalizer-preset-rock;rock:tap@equalizer-preset-rock;reveal-custom:reveal@equalizer-preset-custom;custom:tap@equalizer-preset-custom;reveal-band:reveal@equalizer-band-5;gain:slider@equalizer-band-5,0.75;reveal-preamp:reveal@equalizer-preamp;preamp:slider@equalizer-preamp,0.25;reveal-disable:reveal@equalizer-enabled;disable:tap@equalizer-enabled;back:escape'
    expect = '00-settings=tag:settings-root;open=tag:settings-playback;enable=checked:equalizer-enabled,eq-enabled:true;rock=selected:equalizer-preset-rock,eq-preset:Rock;custom=selected:equalizer-preset-custom,eq-preset:Custom;gain=eq-band:5:6;preamp=eq-preamp:-6;disable=eq-enabled:false;back=tag:settings-root'
elif name == 'search':
    args += ['--source', 'Netease', '--start-tab', 'Search', '--wait-ms', '2500']
    steps = 'query:text@search-query,周杰伦;submit:tap@search-submit;songs:wait@6000;albums:tap@search-kind-albums;album-load:wait@5000;album-open:tap-first@search-media-albums-;album-back:escape;artists:tap@search-kind-artists;artist-load:wait@5000;playlists:tap@search-kind-playlists;playlist-load:wait@5000;more-scopes:scroll-x@310,236,5;users:tap@search-kind-users;user-load:wait@5000;profile:tap-first@search-media-users-;profile-load:wait@5000;profile-back:escape;clear:tap@search-clear;clear-history:tap@search-history-clear'
    expect = '00-search=tag:search-query;submit=selected:search-kind-songs;songs=tag-prefix:search-song-,absent:search-empty,absent:search-error;albums=selected:search-kind-albums;artists=selected:search-kind-artists;playlists=selected:search-kind-playlists;users=selected:search-kind-users;profile=tag:search-account-profile;profile-load=tag:search-account-hero,absent:search-account-error;profile-back=selected:search-kind-users;clear=tag:search-history-clear;clear-history=absent:search-history-clear,tag:search-category-0'
elif name == 'classic':
    args += ['--local-wavs', f'{fixtures}/generated-1000hz.wav,{fixtures}/generated-4000hz.wav', '--capture-pcm', '--width', '1100', '--height', '780']
    steps = 'appearance:tap@settings-open-appearance;classic:tap@player-shell-1;back:escape;player:tap@mini-player-expand;pause:tap@player-play-pause;quality:tap@player-quality;quality-close:escape;lyrics:tap@player-show-lyrics;actions:tap@player-actions;actions-close:escape;queue:tap@player-show-queue;next:tap@player-next;previous:tap@player-previous;collapse:tap@player-collapse'
    expect = 'appearance=tag:settings-appearance;classic=player-shell:Classic,selected:player-shell-1;player=page:Artwork,tag:player-controls,player-shell:Classic;pause=playing:false;quality=tag:quality-dialog;lyrics=page:Lyrics;actions=tag:song-actions;actions-close=absent:song-actions;queue=page:Queue;collapse=absent:now-playing'
elif name in ['search-play', 'search-play-unavailable']:
    args += ['--source', 'Netease', '--start-tab', 'Search', '--capture-pcm', '--wait-ms', '1200']
    steps = 'query:text@search-query,https://music.163.com/song?id=185670;submit:tap@search-submit;result:wait@4000;actions:hold-tag@search-song-185670;queue-add:tap@song-action-添加到播放队列;actions2:hold-tag@search-song-185670;play-next:tap@song-action-下一首播放;play:tap@search-song-185670;playing:wait@5000;player:tap@mini-player-expand;pause:tap@player-play-pause;quality:tap@player-quality;quality-back:escape;collapse:tap@player-collapse'
    expect = 'result=tag:search-song-185670,absent:search-empty,absent:search-error;actions=tag:song-actions;queue-add=queue-min:1,absent:song-actions;play-next=queue-min:2,absent:song-actions;playing=track:netease:185670,playing:true,position-min:500,no-playback-error:true;player=page:Artwork;pause=playing:false;quality=tag:quality-dialog;collapse=absent:now-playing'
    if name == 'search-play-unavailable':
        steps = steps.replace(';pause:tap@player-play-pause', '')
        expect = expect.replace('playing:true,position-min:500,no-playback-error:true', 'playing:false,no-playback-error:false').replace(';pause=playing:false', '')
elif name == 'search-catalog':
    args += ['--source', 'Netease', '--start-tab', 'Search', '--wait-ms', '2000']
    steps = 'query:text@search-query,周杰伦;submit:tap@search-submit;songs:wait@5000;artists:tap@search-kind-artists;artist-load:wait@4000;artist-open:tap-first@search-media-artists-;artist-detail:wait@4000;artist-back:escape;playlists:tap@search-kind-playlists;playlist-load:wait@4000;playlist-open:tap-first@search-media-playlists-;playlist-detail:wait@4000;playlist-back:escape;more-scopes:scroll-x@310,236,5;podcasts:tap@search-kind-podcasts;podcast-load:wait@4000;podcast-open:tap-first@search-media-podcasts-;podcast-detail:wait@4000;podcast-back:escape'
    expect = 'songs=tag-prefix:search-song-;artist-detail=tag:artist-detail;artist-back=selected:search-kind-artists;playlist-detail=tag:playlist-detail;playlist-back=selected:search-kind-playlists;podcast-detail=tag:podcast-detail,tag:podcast-detail-error;podcast-back=selected:search-kind-podcasts'
elif name in ['lyrics', 'eva-local', 'textpv', 'textpv-all']:
    args += ['--width', '1100', '--height', '780', '--wait-ms', '1500']
    if name == 'lyrics':
        args += ['--play-id', '185670']
    else:
        args += ['--local-wavs', f'{fixtures}/generated-1000hz.wav,{fixtures}/generated-4000hz.wav', '--scan-local-dir', str(fixtures), '--local-lyrics-file', str(fixtures / 'generated-1000hz.ttml'), '--capture-pcm']
    steps = 'lyric-settings:tap@settings-open-lyrics;style:tap@settings-selector:Lyrics style;eva:tap@settings-option:EVA kinetic type;back:escape;player:tap@mini-player-expand;lyrics:tap@player-show-lyrics;loaded:wait@6000;skyline:tap@player-skyline-open;skyline-exit:tap@player-skyline-exit;collapse:tap@player-collapse'
    expect = 'lyric-settings=tag:settings-lyrics;player=page:Artwork;lyrics=page:Lyrics,state:player-lyrics:Eva;skyline=tag:player-skyline,absent:player-controls;skyline-exit=absent:player-skyline,state:player-lyrics:Eva'
    if name == 'eva-local':
        steps = steps.replace('loaded:wait@6000;', '')
        expect += ';lyrics=page:Lyrics,state:player-lyrics:Eva,word-lines-min:20,playing:true,position-min:500,no-playback-error:true'
    if name == 'textpv':
        steps = 'lyric-settings:tap@settings-open-lyrics;style:tap@settings-selector:Lyrics style;textpv:tap@settings-option:Text PV;back:escape;player:tap@mini-player-expand;lyrics:tap@player-show-lyrics;pause:tap@player-show-lyrics;pause-play:tap@player-play-pause;lyrics-return:tap@player-show-lyrics;skyline:tap@player-skyline-open;skyline-exit:escape;collapse:tap@player-collapse;settings2:tap@settings-open-lyrics;reveal-slash:reveal@lyrics-pv-kineticsplit;slash:tap@lyrics-pv-kineticsplit;back2:escape;player2:tap@mini-player-expand;reveal-controls:490,130;collapse2:tap@player-collapse;settings3:tap@settings-open-lyrics;reveal-matrix:reveal@lyrics-pv-raincity;matrix:tap@lyrics-pv-raincity;back3:escape;player3:tap@mini-player-expand;collapse3:tap@player-collapse;settings4:tap@settings-open-lyrics;reveal-pixel:reveal@lyrics-pv-kawaiipixel;pixel:tap@lyrics-pv-kawaiipixel;back4:escape;player4:tap@mini-player-expand;collapse4:tap@player-collapse;settings5:tap@settings-open-lyrics;reveal-reset:reveal@lyrics-pv-reset;reset:tap@lyrics-pv-reset;back5:escape'
        expect = 'textpv=tag:lyrics-pv-bluebold;lyrics=page:Lyrics,state:player-lyrics:TextPV,word-lines-min:20;pause-play=playing:false;lyrics-return=page:Lyrics,state:player-lyrics:TextPV;skyline=tag:player-skyline;skyline-exit=absent:player-skyline;slash=selected:lyrics-pv-kineticsplit,pv-style:KineticSplit;player2=page:Lyrics,state:player-lyrics:TextPV,pv-style:KineticSplit;matrix=selected:lyrics-pv-raincity,pv-style:RainCity;pixel=selected:lyrics-pv-kawaiipixel,pv-style:KawaiiPixel;reset=pv-style:BlueBold,pv-speed:2,pv-intensity:1'
    if name == 'textpv-all':
        steps = 'lyric-settings:tap@settings-open-lyrics;style:tap@settings-selector:Lyrics style;textpv:tap@settings-option:Text PV;back:escape;player:tap@mini-player-expand;pause:tap@player-play-pause;lyrics:tap@player-show-lyrics;collapse:tap@player-collapse'
        expect = 'pause=playing:false;lyrics=page:Lyrics,state:player-lyrics:TextPV,word-lines-min:20'
        for style in ['BlueBold','KineticSplit','BluePlane','CyberGrunge','Geometric','RainCity','CyberpunkHUD','EmotionCinema','HystericNight','SpiderWeb','StaggeredText','CalmVillain','GirlyClouds','SweetPink','FlyMeToTheMoon','KawaiiPixel','CrimeScene','Haruhikage']:
            tag = f'lyrics-pv-{style.lower()}'
            steps += f';settings-{style}:tap@settings-open-lyrics;reveal-{style}:reveal@{tag};select-{style}:tap@{tag};back-{style}:escape;player-{style}:tap@mini-player-expand;collapse-{style}:tap@player-collapse'
            expect += f';select-{style}=selected:{tag},pv-style:{style};player-{style}=page:Lyrics,state:player-lyrics:TextPV,pv-style:{style},word-lines-min:20'
else:
    raise ValueError(name)
args += ['--steps', steps, '--expect', expect]
options = {}
index = 0
while index < len(args):
    key = args[index]
    has_value = index + 1 < len(args) and not args[index + 1].startswith('--')
    options[key] = args[index + 1] if has_value else None
    index += 2 if has_value else 1
args = [part for key, value in options.items() for part in ([key] if value is None else [key, value])]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'), f'-PcaptureHome={Path(home).resolve()}', f'-PcaptureArgsFile={argfile}']
(output / 'command.txt').write_text(subprocess.list2cmdline(command) + '\n')
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
print(name, result.returncode)
sys.exit(result.returncode)
