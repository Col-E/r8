#!/usr/bin/env python3
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import optparse
import os
import shutil
import subprocess
import sys
import time

import utils
import zip_utils

USAGE = 'usage: %prog [options] <apk>'


def parse_options():
    parser = optparse.OptionParser(usage=USAGE)
    parser.add_option('--keystore',
                      help='keystore file (default ~/.android/app.keystore)',
                      default='~/.android/app.keystore')
    parser.add_option('--sign',
                      help='Sign the passed in apk.',
                      default=False,
                      action='store_true')
    parser.add_option('--use_apksigner',
                      help='Use apksigner to sign.',
                      default=False,
                      action='store_true')
    parser.add_option('--output',
                      help='Where to put the signed apk.)',
                      default=None)

    (options, args) = parser.parse_args()
    if len(args) != 1:
        parser.error('Expected <apk> argument, got: ' + ' '.join(args))
    apk = args[0]
    return (options, apk)


def add_baseline_profile_to_apk(apk, baseline_profile,
                                baseline_profile_metadata, tmp_dir):
    if baseline_profile is None:
        return apk
    ts = time.time_ns()
    dest_apk = os.path.join(tmp_dir, 'app-%s.apk' % ts)
    dest_apk_aligned = os.path.join(tmp_dir, 'app-aligned-%s.apk' % ts)
    dest_apk_signed = os.path.join(tmp_dir, 'app-signed-%s.apk' % ts)
    shutil.copy2(apk, dest_apk)
    zip_utils.remove_files_from_zip(
        ['assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'],
        dest_apk)
    zip_utils.add_file_to_zip(baseline_profile, 'assets/dexopt/baseline.prof',
                              dest_apk)
    if baseline_profile_metadata is not None:
        zip_utils.add_file_to_zip(baseline_profile_metadata,
                                  'assets/dexopt/baseline.profm', dest_apk)
    align(dest_apk, dest_apk_aligned)
    sign_with_apksigner(dest_apk_aligned, dest_apk_signed)
    return dest_apk_signed


def align(apk, aligned_apk):
    zipalign_path = ('zipalign' if 'build_tools' in os.environ.get('PATH') else
                     os.path.join(utils.getAndroidBuildTools(), 'zipalign'))
    cmd = [zipalign_path, '-f', '-p', '4', apk, aligned_apk]
    utils.RunCmd(cmd, quiet=True, logging=False)
    return aligned_apk


def default_keystore():
    return os.path.join(os.getenv('HOME'), '.android', 'app.keystore')


def get_min_api(apk):
    aapt = os.path.join(utils.getAndroidBuildTools(), 'aapt')
    cmd = [aapt, 'dump', 'badging', apk]
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    for line in stdout.splitlines():
        if line.startswith('sdkVersion:\''):
            return int(line[len('sdkVersion:\''):-1])
    raise ValueError('Unexpected stdout: %s' % stdout)


def sign(unsigned_apk, signed_apk, keystore, quiet=False, logging=True):
    utils.Print('Signing (ignore the warnings)', quiet=quiet)
    cmd = ['zip', '-d', unsigned_apk, 'META-INF/*']
    utils.RunCmd(cmd, quiet=quiet, logging=logging, fail=False)
    cmd = [
        'jarsigner', '-sigalg', 'SHA1withRSA', '-digestalg', 'SHA1',
        '-keystore', keystore, '-storepass', 'android', '-signedjar',
        signed_apk, unsigned_apk, 'androiddebugkey'
    ]
    utils.RunCmd(cmd, quiet=quiet)


def sign_with_apksigner(unsigned_apk,
                        signed_apk,
                        keystore=None,
                        password='android',
                        quiet=False,
                        logging=True):
    cmd = [
        os.path.join(utils.getAndroidBuildTools(), 'apksigner'), 'sign', '-v',
        '--ks', keystore or default_keystore(), '--ks-pass', 'pass:' + password,
        '--min-sdk-version', '19', '--out', signed_apk, '--v2-signing-enabled',
        unsigned_apk
    ]
    utils.RunCmd(cmd, quiet=quiet, logging=logging)
    return signed_apk


def main():
    (options, apk) = parse_options()
    if options.sign:
        if not options.output:
            print('When signing you must specify an output apk')
            return 1
        if not options.keystore:
            print('When signing you must specify a keystore')
            return 1
        if options.use_apksigner:
            sign_with_apksigner(apk, options.output, options.keystore)
        else:
            sign(apk, options.output, options.keystore)
    return 0


if __name__ == '__main__':
    sys.exit(main())
