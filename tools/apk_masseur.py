#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import apk_utils
import glob
import optparse
import os
import shutil
import subprocess
import sys
import utils

USAGE = 'usage: %prog [options] <apk>'

def parse_options():
  parser = optparse.OptionParser(usage=USAGE)
  parser.add_option('--dex',
                    help='directory with dex files to use instead of those in the apk',
                    default=None)
  parser.add_option('--out',
                    help='output file (default ./$(basename <apk>))',
                    default=None)
  parser.add_option('--keystore',
                    help='keystore file (default ~/.android/app.keystore)',
                    default=None)
  parser.add_option('--install',
                    help='install the generated apk with adb options -t -r -d',
                    default=False,
                    action='store_true')
  parser.add_option('--adb-options',
                    help='additional adb options when running adb',
                    default=None)
  (options, args) = parser.parse_args()
  if len(args) != 1:
    parser.error('Expected <apk> argument, got: ' + ' '.join(args))
  apk = args[0]
  return (options, apk)

def findKeystore():
  return os.path.join(os.getenv('HOME'), '.android', 'app.keystore')

def repack(processed_out, original_apk, temp):
  processed_apk = os.path.join(temp, 'processed.apk')
  shutil.copyfile(original_apk, processed_apk)
  if not processed_out:
    print 'Using original APK as is'
    return processed_apk
  print 'Repacking APK with dex files from', processed_apk
  with utils.ChangedWorkingDirectory(temp):
    cmd = ['zip', '-d', 'processed.apk', '*.dex']
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)
  if processed_out.endswith('.zip') or processed_out.endswith('.jar'):
    cmd = ['unzip', processed_out, '-d', temp]
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)
    processed_out = temp
  with utils.ChangedWorkingDirectory(processed_out):
    dex = glob.glob('*.dex')
    cmd = ['zip', '-u', '-9', processed_apk] + dex
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)
  return processed_apk

def sign(unsigned_apk, keystore, temp):
  signed_apk = os.path.join(temp, 'unaligned.apk')
  apk_utils.sign(unsigned_apk, signed_apk, keystore)
  return signed_apk

def align(signed_apk, temp):
  print 'Aligning'
  aligned_apk = os.path.join(temp, 'aligned.apk')
  cmd = ['zipalign', '-f', '4', signed_apk, aligned_apk]
  print ' '.join(cmd)
  subprocess.check_call(cmd)
  return signed_apk

def masseur(
    apk, dex=None, out=None, adb_options=None, keystore=None, install=False):
  if not out:
    out = os.path.basename(apk)
  if not keystore:
    keystore = findKeystore()
  with utils.TempDir() as temp:
    processed_apk = None
    if dex:
      processed_apk = repack(dex, apk, temp)
    else:
      print 'Signing original APK without modifying dex files'
      processed_apk = os.path.join(temp, 'processed.apk')
      shutil.copyfile(apk, processed_apk)
    signed_apk = sign(processed_apk, keystore, temp)
    aligned_apk = align(signed_apk, temp)
    print 'Writing result to', out
    shutil.copyfile(aligned_apk, out)
    adb_cmd = ['adb']
    if adb_options:
      adb_cmd.extend(
          [option for option in adb_options.split(' ') if option])
    if install:
      adb_cmd.extend(['install', '-t', '-r', '-d', out]);
      utils.PrintCmd(adb_cmd)
      subprocess.check_call(adb_cmd)

def main():
  (options, apk) = parse_options()
  masseur(apk, **vars(options))
  return 0

if __name__ == '__main__':
  sys.exit(main())
