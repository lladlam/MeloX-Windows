#!/usr/bin/env python3
"""Pointer-driven production LAN UI against an explicit loopback protocol peer.

The peer records real control requests and TCP PCM; it is NOT an Android device
or a physical speaker. Playback uses actual generated Local WAV -> decoder PCM.
"""
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
import time
import uuid
import wave


class ProtocolPeer:
    def __init__(self, output):
        self.output = output
        self.id = str(uuid.uuid4())
        self.listener = socket.socket()
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen()
        self.port = self.listener.getsockname()[1]
        self.requests = []
        self.events = []
        self.connections = []
        self.cast_listener = None
        self.cast_socket = None
        self.cast_threads = []
        self.lock = threading.Lock()
        self.stopped = threading.Event()
        self.thread = threading.Thread(target=self.accept, daemon=True)
        self.thread.start()

    def accept(self):
        while not self.stopped.is_set():
            try: connection, _ = self.listener.accept()
            except OSError: return
            self.connections.append(connection)
            threading.Thread(target=self.serve, args=(connection,), daemon=True).start()

    def stop_cast(self):
        for item in (self.cast_socket, self.cast_listener):
            if item:
                try: item.shutdown(socket.SHUT_RDWR)
                except OSError: pass
                item.close()
        self.cast_socket = self.cast_listener = None

    def start_cast(self):
        self.stop_cast()
        listener = socket.socket(); listener.bind(('127.0.0.1', 0)); listener.listen()
        self.cast_listener = listener
        index = len(self.cast_threads)
        def receive():
            try:
                connection, _ = listener.accept(); self.cast_socket = connection
                with connection, connection.makefile('rb') as incoming, (self.output/f'peer-cast-{index}.s16le').open('wb') as pcm:
                    while True:
                        prefix = incoming.read(4)
                        if len(prefix) != 4: break
                        length = struct.unpack('>I', prefix)[0]
                        assert 0 < length <= 65536 and length % 4 == 0
                        data = incoming.read(length)
                        assert len(data) == length
                        pcm.write(data)
            except OSError: pass
        thread = threading.Thread(target=receive, daemon=True); self.cast_threads.append(thread); thread.start()
        return listener.getsockname()[1]

    def serve(self, connection):
        send_lock = threading.Lock()
        alive = threading.Event(); alive.set()
        def send(message):
            with send_lock: connection.sendall((json.dumps(message)+'\n').encode())
        def state():
            return {'title':'Loopback protocol fixture', 'artist':'Protocol test', 'playing':False, 'ready':True,
                'positionMs':0, 'serverTimeMs':round(time.time()*1000)}
        def events():
            while alive.wait(1) and alive.is_set():
                try: send({'type':'event', 'event':'state', 'state':state()})
                except OSError: return
                # Event.wait on a set event returns immediately; use a separate stop event.
                if self.stopped.wait(1): return
        try:
            with connection.makefile('rb') as incoming:
                hello = json.loads(incoming.readline())
                if hello.get('pin') != '0427':
                    send({'type':'auth_required', 'pinRequired':True}); return
                send({'type':'auth_ok', 'sessionId':str(uuid.uuid4()), 'deviceId':self.id,
                    'capabilities':['control','cast','transfer','together','together_barrier_v2']})
                threading.Thread(target=events, daemon=True).start()
                for line in incoming:
                    message = json.loads(line)
                    if message.get('type') == 'event':
                        with self.lock: self.events.append(message)
                        continue
                    if message.get('type') != 'request': continue
                    with self.lock: self.requests.append(message)
                    action = message['action']; result = {'ok':True}
                    if action == 'state': result['state'] = state()
                    elif action == 'ping': result.update(t0=message.get('t0'), serverTimeMs=round(time.time()*1000))
                    elif action == 'transfer':
                        assert message['track']['id'] and len(message['queue']) == 2
                    elif action == 'together.start': assert len(message['queue']) == 2
                    elif action == 'together.prepare':
                        assert len(message['queue']) == 2
                        result.update(sessionId=message['sessionId'], generation=message['generation'])
                    elif action == 'cast.start':
                        result.update(castPort=self.start_cast(), format=message['format'])
                    elif action == 'cast.stop':
                        self.stop_cast(); send({'type':'event','event':'cast.stopped'})
                    send(dict(result, type='response', id=message['id']))
        except (OSError, ValueError): pass
        finally: alive.clear(); connection.close()

    def close(self):
        self.stopped.set(); self.stop_cast(); self.listener.close()
        for connection in self.connections:
            try: connection.shutdown(socket.SHUT_RDWR)
            except OSError: pass
            connection.close()
        for thread in self.cast_threads: thread.join(timeout=5)
        (self.output/'protocol-peer.json').write_text(json.dumps({'kind':'loopback protocol fixture, not Android',
            'requests':self.requests, 'events':self.events}, indent=2)+'\n')


def main():
    mode, home, output = sys.argv[1:4]
    assert mode in ('mobile','desktop')
    home, output = Path(home).resolve(), Path(output).resolve()
    repo = Path(__file__).resolve().parents[1]
    prefs = home/'.melox/prefs_melox_app_settings.properties'
    assert not prefs.exists(), 'Use a fresh isolated home'
    prefs.parent.mkdir(parents=True,exist_ok=True)
    prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\nplayback_previous_restarts=false\n'
        f'general_desktop_layout={str(mode == "desktop").lower()}\n')
    output.mkdir(parents=True,exist_ok=True)
    files=[]
    for index,hz in enumerate((1000,4000)):
        file=home/f'lan-generated-{index}.wav'
        with wave.open(str(file),'wb') as stream:
            stream.setparams((2,2,44100,0,'NONE','not compressed'))
            second=b''.join(struct.pack('<hh',*[round(5000*math.sin(i*2*math.pi*hz/44100))]*2) for i in range(44100))
            for _ in range(600): stream.writeframes(second)
        files.append(str(file))
    peer=ProtocolPeer(output)
    steps=[];checks={}
    def add(name,action,expected=()):
        steps.append(f'{name}:{action}')
        if expected:checks[name]=list(expected)
    def tap(name,tag,expected=()):
        add('reveal-'+name,'reveal-edge@'+tag)
        add(name,'tap@'+tag,expected)
    add('consent','tap@remote-reject',['cloud-enabled:false'])
    add('playing','wait@1500',['playing:true'])
    if mode=='mobile':
        tap('open-player','mini-player-expand')
        add('opened','wait@1000',['page:Artwork'])
        tap('output','player-output',['tag:lan-output-panel','selected:lan-output-device:local'])
        tap('pause','lan-output-toggle',['playing:false'])
        tap('next','lan-output-next',['title:lan-generated-1'])
        tap('resume','lan-output-toggle',['playing:true'])
        tap('management','lan-output-settings',['tag:lan-dialog'])
    else: tap('management','desktop-lan',['tag:lan-dialog'])
    for tag,text in [('lan-manual-address','127.0.0.1'),('lan-manual-port',str(peer.port)),('lan-pin','0427')]:
        tap('focus-'+tag,tag)
        add('select-'+tag,'select-all')
        add('type-'+tag,'type@'+text)
    tap('connect','lan-manual-connect',['tag:lan-connected'])
    tap('trust-peer','lan-trust:'+peer.id,['tag:lan-untrust:'+peer.id])
    tap('untrust-peer','lan-untrust:'+peer.id,['tag:lan-trust:'+peer.id])
    if mode=='mobile':
        tap('management-close','lan-close',['tag:lan-output-panel'])
        tap('panel-close','lan-output-close',['absent:lan-output-panel'])
        tap('reopen','player-output',['tag:lan-output-disconnect'])
        tap('cast','lan-output-cast',['absent:lan-output-panel'])
        add('cast-playing','wait@1500',['playing:true'])
        tap('cast-reopen','player-output',['tag:lan-output-disconnect','unselected:lan-output-device:local'])
        tap('cast-previous','lan-output-previous',['title:lan-generated-0'])
        add('cast-survives-track','wait@1500',['unselected:lan-output-device:local'])
        tap('local','lan-output-device:local',['selected:lan-output-device:local'])
        add('local-audio','wait@1500',['playing:true'])
        tap('together','lan-output-together',['absent:lan-output-panel'])
        tap('together-reopen','player-output',['tag:lan-output-together-stop'])
        tap('together-stop','lan-output-together-stop',['selected:lan-output-device:local'])
        tap('transfer','lan-output-transfer',['absent:lan-output-panel','playing:false'])
        tap('transfer-reopen','player-output',['tag:lan-output-disconnect'])
        tap('disconnect','lan-output-disconnect',['absent:lan-output-disconnect'])
    else:
        tap('close','lan-close',['absent:lan-dialog'])
        tap('reopen','desktop-lan',['tag:lan-connected'])
        tap('together','lan-together')
        tap('local','lan-local',['tag:lan-connected'])
        tap('transfer','lan-transfer',['playing:false'])
        tap('disconnect','lan-disconnect',['absent:lan-connected'])
        tap('close-again','lan-close',['absent:lan-dialog'])
    (output/'steps.txt').write_text('\n'.join(steps)+'\n')
    (output/'expect.txt').write_text('\n'.join(k+'='+','.join(v) for k,v in checks.items())+'\n')
    args=['--source','Local','--start-tab','Settings','--theme','Dark','--width','430' if mode=='mobile' else '1100',
        '--height','850' if mode=='mobile' else '780','--output',str(output),'--scan-local-dir',str(home),
        '--local-wavs',','.join(files),'--capture-pcm','--wait-ms','250','--startup-wait-ms','1500',
        '--steps-file',str(output/'steps.txt'),'--expect-file',str(output/'expect.txt')]
    (output/'arguments.txt').write_text('\n'.join(args)+'\n')
    gradle=os.environ.get('MELOX_GRADLE') or shutil.which('gradle');assert gradle
    try:
        with (output/'gradle.log').open('w') as log:
            result=subprocess.run([gradle,':desktop:captureUi','--offline','--no-daemon','--max-workers=1','--console=plain',
                '-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
                '-PcaptureHome='+str(home),'-PcaptureArgsFile='+str(output/'arguments.txt')],cwd=repo,stdout=log,stderr=subprocess.STDOUT)
    finally:peer.close()
    if result.returncode:
        print((output/'gradle.log').read_text(errors='replace')[-12000:]);raise SystemExit(result.returncode)
    data=json.loads((output/'capture.json').read_text());assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions',[]))
    assert any(r['action']=='transfer' and len(r['queue'])==2 for r in peer.requests)
    assert any(r['action']=='together.stop' for r in peer.requests)
    assert any(r['action']=='together.prepare' for r in peer.requests)
    assert any(e.get('event')=='together.commit' for e in peer.events)
    if mode=='mobile':
        pcm=b''.join(p.read_bytes() for p in output.glob('peer-cast-*.s16le'))
        assert len(pcm)>44100*4 and any(pcm), 'No real non-silent PCM received by TCP peer'
        local=b''.join((output/p['file']).read_bytes() for p in data['pcm_outputs'])
        chunks=[local[i:i+44100*4] for i in range(0,len(local)-44100*4+1,44100*4)]
        silent=[i for i,p in enumerate(chunks) if not any(p)]
        assert silent and any(any(p) for p in chunks[:silent[0]]) and any(any(p) for p in chunks[silent[-1]+1:]), 'Must hear local PCM before cast, silence during cast, and local PCM after return'
    print(f'PASS LAN {mode}: {len(data["scenes"])} scenes / '+str(sum(len(s.get('assertions',[])) for s in data['scenes']))+' assertions; loopback protocol peer only')


if __name__=='__main__':main()
