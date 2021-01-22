#!/usr/bin/env python3
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import optparse
import os
import subprocess
import sys
import utils

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


def sign(unsigned_apk, signed_apk, keystore, quiet=False, logging=True):
  utils.Print('Signing (ignore the warnings)', quiet=quiet)
  cmd = ['zip', '-d', unsigned_apk, 'META-INF/*']
  utils.RunCmd(cmd, quiet=quiet, logging=logging, fail=False)
  cmd = [
    'jarsigner',
    '-sigalg', 'SHA1withRSA',
    '-digestalg', 'SHA1',
    '-keystore', keystore,
    '-storepass', 'android',
    '-signedjar', signed_apk,
    unsigned_apk,
    'androiddebugkey'
  ]
  utils.RunCmd(cmd, quiet=quiet)

def sign_with_apksigner(
    unsigned_apk, signed_apk, keystore, password='android', quiet=False,
    logging=True):
  cmd = [
    os.path.join(utils.getAndroidBuildTools(), 'apksigner'),
    'sign',
    '-v',
    '--ks', keystore,
    '--ks-pass', 'pass:' + password,
    '--min-sdk-version', '19',
    '--out', signed_apk,
    unsigned_apk
  ]
  utils.RunCmd(cmd, quiet=quiet, logging=logging)


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
