#!/usr/bin/env python3
"""Production pointer replay with an explicit loopback SSDP/SOAP renderer, not a speaker."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import sys
import threading
from urllib.request import Request, urlopen
import uuid
import wave
import xml.etree.ElementTree as ET


class Renderer:
    def __init__(self, output):
        self.id = 'uuid:melox-ui-' + str(uuid.uuid4())
        self.output = output
        self.calls = []
        self.ranges = []
        self.errors = []
        self.playing = False
        self.position = '00:00:00'
        self.volume = '50'
        self.url = ''
        owner = self
        service = 'urn:schemas-upnp-org:service:AVTransport:1'

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def reply(self, xml):
                body = xml.encode()
                self.send_response(200)
                self.send_header('Content-Type', 'text/xml; charset=utf-8')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                self.reply(f'<root><device><UDN>{owner.id}</UDN><friendlyName>Loopback DLNA fixture</friendlyName>'
                    '<manufacturer>Protocol fixture</manufacturer><modelName>Not a physical speaker</modelName>'
                    f'<serviceList><service><serviceType>{service}</serviceType><controlURL>/transport</controlURL></service>'
                    '<service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>'
                    '<controlURL>/rendering</controlURL></service></serviceList></device></root>')

            def do_POST(self):
                try:
                    method = self.headers['SOAPACTION'].strip('"').split('#')[-1]
                    root = ET.fromstring(self.rfile.read(int(self.headers['Content-Length'])))
                    fields = {node.tag.split('}')[-1]: node.text or '' for node in root.iter() if not list(node)}
                    owner.calls.append({'action': method, 'fields': fields})
                    values = {}
                    if method == 'SetAVTransportURI':
                        owner.url = fields['CurrentURI']
                        assert 'object.item.audioItem.musicTrack' in fields['CurrentURIMetaData']
                    elif method == 'Play':
                        with urlopen(Request(owner.url, headers={'Range': 'bytes=0-4095'}), timeout=5) as response:
                            audio = response.read()
                            assert response.status == 206 and len(audio) == 4096 and audio[:4] == b'RIFF'
                            owner.ranges.append({'status': response.status, 'content_range': response.headers['Content-Range'],
                                'bytes': len(audio), 'mime': response.headers['Content-Type']})
                            (output/f'renderer-audio-{len(owner.ranges)}.bin').write_bytes(audio)
                        owner.playing = True
                    elif method in ('Stop', 'Pause'):
                        owner.playing = False
                    elif method == 'Seek':
                        owner.position = fields['Target']
                    elif method == 'SetVolume':
                        owner.volume = fields['DesiredVolume']
                    elif method == 'GetVolume':
                        values = {'CurrentVolume': owner.volume}
                    elif method == 'GetTransportInfo':
                        values = {'CurrentTransportState': 'PLAYING' if owner.playing else 'STOPPED'}
                    elif method == 'GetPositionInfo':
                        values = {'RelTime': owner.position, 'TrackDuration': '00:10:00'}
                    self.reply('<Envelope><Body><Response>' + ''.join(f'<{k}>{v}</{k}>' for k, v in values.items()) + '</Response></Body></Envelope>')
                except Exception as failure:
                    owner.errors.append(str(failure))
                    self.send_error(500)

        self.http = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=self.http.serve_forever, daemon=True).start()
        self.ssdp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.ssdp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.ssdp.bind(('', 1900))
        self.ssdp.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, socket.inet_aton('239.255.255.250') + socket.inet_aton('0.0.0.0'))
        self.ssdp.settimeout(1)
        self.stopped = threading.Event()
        threading.Thread(target=self.announce, daemon=True).start()

    def announce(self):
        while not self.stopped.is_set():
            try:
                packet, peer = self.ssdp.recvfrom(16384)
                if packet.startswith(b'M-SEARCH '):
                    response = (f'HTTP/1.1 200 OK\r\nLOCATION: http://127.0.0.1:{self.http.server_port}/description\r\n'
                        f'ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nUSN: {self.id}\r\n\r\n').encode()
                    self.ssdp.sendto(response, peer)
            except socket.timeout:
                pass
            except OSError:
                return

    def close(self):
        self.stopped.set()
        self.ssdp.close()
        self.http.shutdown()
        self.http.server_close()
        (self.output/'renderer.json').write_text(json.dumps({'kind': 'loopback renderer, not physical DLNA',
            'calls': self.calls, 'ranges': self.ranges, 'errors': self.errors}, indent=2)+'\n')


def main():
    mode, home, output = sys.argv[1:4]
    assert mode in ('mobile', 'desktop')
    home, output = Path(home).resolve(), Path(output).resolve()
    prefs = home/'.melox/prefs_melox_app_settings.properties'
    assert not prefs.exists(), 'Use a fresh isolated home'
    prefs.parent.mkdir(parents=True, exist_ok=True)
    prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\nplayback_previous_restarts=false\n'
        f'general_desktop_layout={str(mode == "desktop").lower()}\n')
    output.mkdir(parents=True, exist_ok=True)
    audio = home/'dlna-generated.wav'
    second = b''.join(struct.pack('<hh', *[round(5000*math.sin(i*2*math.pi*1000/44100))]*2) for i in range(44100))
    with wave.open(str(audio), 'wb') as stream:
        stream.setparams((2, 2, 44100, 0, 'NONE', 'not compressed'))
        for _ in range(600):
            stream.writeframes(second)
    renderer = Renderer(output)
    steps, checks = [], {}

    def add(name, action, expected=()):
        steps.append(f'{name}:{action}')
        if expected:
            checks[name] = list(expected)

    def tap(name, tag, expected=()):
        add('reveal-'+name, 'reveal-edge@'+tag)
        add(name, 'tap@'+tag, expected)

    add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
    add('playing', 'wait@1500', ['playing:true'])
    if mode == 'mobile':
        tap('open-player', 'mini-player-expand')
        tap('open-output', 'player-output', ['tag:lan-output-panel'])
        add('discover', 'wait@3500', ['tag:lan-output-device:dlna:'+renderer.id])
        tap('cast', 'lan-output-device:dlna:'+renderer.id, ['absent:lan-output-panel', 'playing:false', 'output-playing:true'])
        tap('reopen', 'player-output', ['selected:lan-output-device:dlna:'+renderer.id])
        tap('pause', 'lan-output-toggle', ['output-playing:false'])
        tap('resume', 'lan-output-toggle', ['output-playing:true'])
        add('reveal-volume', 'reveal-edge@lan-output-volume')
        add('volume', 'slider@lan-output-volume,0.37', ['output-volume:0.37'])
        tap('dismiss-output', 'lan-output-close', ['absent:lan-output-panel'])
        tap('reopen-again', 'player-output', ['selected:lan-output-device:dlna:'+renderer.id])
        tap('local', 'lan-output-device:local', ['selected:lan-output-device:local', 'playing:true'])
    else:
        tap('open-output', 'desktop-lan', ['tag:lan-dialog'])
        add('discover', 'wait@3500', ['tag:lan-dlna-cast:'+renderer.id])
        tap('cast', 'lan-dlna-cast:'+renderer.id, ['tag:lan-dlna-disconnect', 'playing:false', 'output-playing:true'])
        tap('close-output', 'lan-close', ['absent:lan-dialog'])
        tap('pause', 'mini-player-play-pause', ['output-playing:false'])
        tap('resume', 'mini-player-play-pause', ['output-playing:true'])
        tap('reopen', 'desktop-lan', ['tag:lan-dlna-disconnect'])
        tap('local', 'lan-dlna-disconnect', ['playing:true', 'absent:lan-dlna-disconnect'])
    (output/'steps.txt').write_text('\n'.join(steps)+'\n')
    (output/'expect.txt').write_text('\n'.join(k+'='+','.join(v) for k,v in checks.items())+'\n')
    args = ['--source','Local','--start-tab','Settings','--theme','Dark','--width','430' if mode == 'mobile' else '1100',
        '--height','850' if mode == 'mobile' else '780','--output',str(output),'--scan-local-dir',str(home),
        '--local-wavs',str(audio),'--capture-pcm','--wait-ms','250','--startup-wait-ms','1500',
        '--steps-file',str(output/'steps.txt'),'--expect-file',str(output/'expect.txt')]
    (output/'arguments.txt').write_text('\n'.join(args)+'\n')
    gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
    assert gradle
    try:
        with (output/'gradle.log').open('w') as log:
            result = subprocess.run([gradle, ':desktop:captureUi','--offline','--no-daemon','--max-workers=1','--console=plain',
                '-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
                '-PcaptureHome='+str(home),'-PcaptureArgsFile='+str(output/'arguments.txt')],
                cwd=Path(__file__).resolve().parents[1], stdout=log, stderr=subprocess.STDOUT)
    finally:
        renderer.close()
    assert result.returncode == 0, (output/'gradle.log').read_text(errors='replace')[-12000:]
    assert not renderer.errors, renderer.errors
    assert len(renderer.ranges) >= 2, 'Initial Play and resumed Play must fetch actual audio'
    assert {'Pause', 'Stop', 'GetTransportInfo'} <= {c['action'] for c in renderer.calls}
    data = json.loads((output/'capture.json').read_text())
    assert all(a['passed'] for scene in data['scenes'] for a in scene.get('assertions', []))
    print(f'PASS DLNA {mode}: {len(data["scenes"])} scenes, actual SSDP/SOAP/Range audio; loopback renderer only')


if __name__ == '__main__':
    main()
