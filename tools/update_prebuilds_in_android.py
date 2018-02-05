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

BUILD_ROOT = "http://storage.googleapis.com/r8-releases/raw/"
MASTER_BUILD_ROOT = "%smaster/" % BUILD_ROOT

JAR_TARGETS = [utils.D8, utils.R8, utils.COMPATDX, utils.COMPATPROGUARD]
OTHER_TARGETS = ["LICENSE"]

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Build and copy jars to an Android tree.')
  parser.add_argument('android_root', nargs=1,
      help='Android checkout root.')
  parser.add_argument('--commit_hash', default=None, help='Commit hash')
  parser.add_argument('--version', default=None, help='The version to download')
  return parser.parse_args()

def copy_targets(root, target_root, srcs, dests):
  for i in range(len(srcs)):
    src = os.path.join(root, srcs[i])
    dest = os.path.join(target_root, 'prebuilts', 'r8', dests[i])
    print 'Copying: ' + src + ' -> ' + dest
    copyfile(src, dest)

def copy_jar_targets(root, target_root):
  srcs = map((lambda t: t + '.jar'), JAR_TARGETS)
  dests = map((lambda t: t + '-master.jar'), JAR_TARGETS)
  copy_targets(root, target_root, srcs, dests)

def copy_other_targets(root, target_root):
  copy_targets(root, target_root, OTHER_TARGETS, OTHER_TARGETS)

def download_hash(root, commit_hash, target):
  url = MASTER_BUILD_ROOT + commit_hash + '/' + target
  download_target(root, url, target)

def download_version(root, version, target):
  url = BUILD_ROOT + version + '/' + target
  download_target(root, url, target)

def download_target(root, url, target):
  download_path = os.path.join(root, target)
  print 'Downloading: ' + url + ' -> ' + download_path
  result = urllib.urlretrieve(url, download_path)
  if 'X-GUploader-Request-Result: success' not in str(result[1]):
    raise IOError('Failed to download ' + url)

def Main():
  args = parse_arguments()
  target_root = args.android_root[0]
  if args.commit_hash == None and args.version == None:
    gradle.RunGradle(JAR_TARGETS)
    copy_jar_targets(utils.LIBS, target_root)
    copy_other_targets(utils.GENERATED_LICENSE_DIR, target_root)
  else:
    assert args.commit_hash == None or args.version == None
    targets = map((lambda t: t + '.jar'), JAR_TARGETS) + OTHER_TARGETS
    with utils.TempDir() as root:
      for target in targets:
        if args.commit_hash:
          download_hash(root, args.commit_hash, target)
        else:
          assert args.version
          download_version(root, args.version, target)
      copy_jar_targets(root, target_root)
      copy_other_targets(root, target_root)

if __name__ == '__main__':
  sys.exit(Main())
