#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import apk_masseur
import toolhelper
import utils

def parse_options(argv):
  result = argparse.ArgumentParser(
      description='Relayout a given APK using a startup profile.')
  result.add_argument('--apk',
                      help='Path to the .apk',
                      required=True)
  result.add_argument('--out',
                      help='Destination of resulting apk',
                      required=True)
  result.add_argument('--profile',
                      help='Path to the startup profile',
                      required=True)
  options, args = result.parse_known_args(argv)
  return options, args

def get_min_api(apk):
  aapt = os.path.join(utils.getAndroidBuildTools(), 'aapt')
  cmd = [aapt, 'dump', 'badging', apk]
  stdout = subprocess.check_output(cmd).decode('utf-8').strip()
  for line in stdout.splitlines():
    if line.startswith('sdkVersion:\''):
      return int(line[len('sdkVersion:\''): -1])
  raise ValueError('Unexpected stdout: %s' % stdout)

def main(argv):
  (options, args) = parse_options(argv)
  with utils.TempDir() as temp:
    dex = os.path.join(temp, 'dex.zip')
    d8_args = [
        '--min-api', str(get_min_api(options.apk)),
        '--output', dex,
        '--no-desugaring',
        '--release',
        options.apk]
    extra_args = ['-Dcom.android.tools.r8.startup.profile=%s' % options.profile]
    toolhelper.run(
        'd8',
        d8_args,
        extra_args=extra_args,
        main='com.android.tools.r8.D8')
    apk_masseur.masseur(options.apk, dex=dex, out=options.out)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
