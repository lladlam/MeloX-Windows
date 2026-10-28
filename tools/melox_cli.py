#!/usr/bin/env python3
"""Control the running MeloX Windows app using its opt-in local CLI endpoint."""
import argparse
import base64
import json
import os
from pathlib import Path
import socket
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')


def unprotect_windows(data):
    import ctypes
    from ctypes import wintypes

    class DataBlob(ctypes.Structure):
        _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]

    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    crypt.CryptUnprotectData.argtypes = [ctypes.POINTER(DataBlob), ctypes.POINTER(ctypes.c_wchar_p),
        ctypes.POINTER(DataBlob), ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(DataBlob)]
    crypt.CryptUnprotectData.restype = wintypes.BOOL
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    storage = (ctypes.c_ubyte * len(data)).from_buffer_copy(data)
    source = DataBlob(len(data), storage)
    target = DataBlob()
    if not crypt.CryptUnprotectData(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(target.data, target.size)
    finally:
        kernel.LocalFree(ctypes.cast(target.data, ctypes.c_void_p))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, default=Path.home(), help='MeloX user home (default: current user)')
    commands = parser.add_subparsers(dest='command', required=True)
    for command in ('status', 'queue', 'play', 'pause', 'toggle', 'next', 'previous', 'stop'):
        commands.add_parser(command)
    for command in ('seek', 'volume'):
        commands.add_parser(command).add_argument('value')
    commands.add_parser('shuffle').add_argument('value', choices=('on', 'off'))
    commands.add_parser('repeat').add_argument('value', choices=('off', 'one', 'all'))
    for command in ('search', 'search-play'):
        sub = commands.add_parser(command)
        sub.add_argument('query')
        sub.add_argument('--source', default='netease', help='Provider ID, e.g. netease, qq_music, local')
        if command == 'search-play':
            sub.add_argument('--index', type=int, default=0)
    args = parser.parse_args()
    try:
        endpoint = json.loads((args.home / '.melox' / 'cli-endpoint.json').read_text(encoding='utf-8'))
        if endpoint.get('version') != 1:
            raise ValueError('unsupported CLI endpoint version')
        secret = base64.b64decode(endpoint['token'], validate=True)
        if endpoint['protection'] == 'dpapi':
            if os.name != 'nt':
                raise ValueError('Windows DPAPI endpoint requires the same Windows user')
            secret = unprotect_windows(secret)
        elif endpoint['protection'] != 'owner-only':
            raise ValueError('unsupported CLI endpoint protection')
        if len(secret) != 32:
            raise ValueError('invalid CLI endpoint token')
        request = {'command': args.command, 'token': base64.b64encode(secret).decode('ascii')}
        if hasattr(args, 'value'):
            request['value'] = args.value
        if hasattr(args, 'query'):
            request.update(query=args.query, source=args.source)
        if hasattr(args, 'index'):
            request['value'] = str(args.index)
        with socket.create_connection(('127.0.0.1', int(endpoint['port'])), timeout=12) as connection:
            connection.sendall((json.dumps(request, ensure_ascii=False) + '\n').encode('utf-8'))
            with connection.makefile('rb') as stream:
                line = stream.readline(1_000_001)
            if not line.endswith(b'\n') or len(line) > 1_000_000:
                raise ValueError('invalid CLI response')
        response = json.loads(line)
    except (OSError, ValueError, KeyError, TypeError) as failure:
        response = {'ok': False, 'error': str(failure)}
    print(json.dumps(response, ensure_ascii=False, indent=2))
    return 0 if response.get('ok') is True else 1


if __name__ == '__main__':
    sys.exit(main())
