#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import gradle
import os
import sys
import utils

from shutil import copyfile

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Build and copy jars to an Android tree.')
  parser.add_argument('android_root', nargs=1,
      help='Android checkout root.')
  return parser.parse_args()

def Main():
  args = parse_arguments()
  targets = ['r8', 'd8', 'compatdx', 'compatproguard']
  gradle.RunGradle(targets)
  for target in targets:
    src = os.path.join(utils.REPO_ROOT, 'build', 'libs', target + '.jar')
    dest = os.path.join(
        args.android_root[0], 'prebuilts', 'r8', target + '-master.jar')
    print 'Copying: ' + src + ' -> ' + dest
    copyfile(src, dest)

if __name__ == '__main__':
  sys.exit(Main())
