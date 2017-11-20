#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import gradle
import os
from shutil import copyfile
import sys
import tempfile
import utils
import urllib

MASTER_BUILD_ROOT = "http://storage.googleapis.com/r8-releases/raw/master/"
TARGETS = [utils.D8, utils.R8, utils.COMPATDX, utils.COMPATPROGUARD]

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Build and copy jars to an Android tree.')
  parser.add_argument('android_root', nargs=1,
      help='Android checkout root.')
  parser.add_argument('--commit_hash', default=None, help='Commit hash')
  return parser.parse_args()

def copy_targets(root, target_root):
  for target in TARGETS:
    src = os.path.join(root, target + '.jar')
    dest = os.path.join(
        target_root, 'prebuilts', 'r8', target + '-master.jar')
    print 'Copying: ' + src + ' -> ' + dest
    copyfile(src, dest)

def Main():
  args = parse_arguments()
  target_root = args.android_root[0]
  if args.commit_hash == None:
    gradle.RunGradle(TARGETS)
    root = os.path.join(utils.REPO_ROOT, 'build', 'libs')
    copy_targets(root, target_root)
  else:
    with utils.TempDir() as root:
      for target in TARGETS:
        url = MASTER_BUILD_ROOT + args.commit_hash + '/' + target + '.jar'
        download_path = os.path.join(root, target + '.jar')
        print 'Downloading: ' + url + ' -> ' + download_path
        urllib.urlretrieve(url, download_path)
      copy_targets(root, target_root)

if __name__ == '__main__':
  sys.exit(Main())
