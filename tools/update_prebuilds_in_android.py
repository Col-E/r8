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

JAR_TARGETS_MAP = {
  'full': [
    (utils.D8, 'd8-master'),
    (utils.R8, 'r8-master'),
    (utils.COMPATDX, 'compatdx-master'),
    (utils.COMPATPROGUARD, 'compatproguard-master'),
  ],
  'lib': [
    (utils.R8LIB, 'r8-master'),
    (utils.COMPATDXLIB, 'compatdx-master'),
    (utils.COMPATPROGUARDLIB, 'compatproguard-master'),
  ],
}

OTHER_TARGETS = ["LICENSE"]

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Build and copy jars to an Android tree.')
  parser.add_argument('android_root', nargs=1,
      help='Android checkout root.')
  parser.add_argument('--commit_hash', default=None, help='Commit hash')
  parser.add_argument('--version', default=None, help='The version to download')
  parser.add_argument(
    '--targets',
    required=True,
    choices=['full', 'lib'],
    help="Use 'full' to download the full, non-optimized jars (legacy" +
      " behaviour) and 'lib' for the R8-processed, optimized jars (this" +
      " one omits d8.jar)",
  )
  parser.add_argument(
    '--maps',
    action='store_true',
    help="Download proguard maps for jars, use only with '--target lib'.",
  )
  return parser.parse_args()

def copy_targets(root, target_root, srcs, dests, maps=False):
  assert len(srcs) == len(dests)
  for i in range(len(srcs)):
    src = os.path.join(root, srcs[i])
    dest = os.path.join(target_root, 'prebuilts', 'r8', dests[i])
    print 'Copying: ' + src + ' -> ' + dest
    copyfile(src, dest)
    if maps:
      print 'Copying: ' + src + '.map -> ' + dest + '.map'
      copyfile(src + '.map', dest + '.map')

def copy_jar_targets(root, target_root, jar_targets, maps):
  srcs = map((lambda t: t[0] + '.jar'), jar_targets)
  dests = map((lambda t: t[1] + '.jar'), jar_targets)
  copy_targets(root, target_root, srcs, dests, maps=maps)

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
  if args.maps and args.targets != 'lib':
    raise Exception("Use '--maps' only with '--targets lib.")
  target_root = args.android_root[0]
  jar_targets = JAR_TARGETS_MAP[args.targets]
  if args.commit_hash == None and args.version == None:
    gradle.RunGradle(map(lambda t: t[0], jar_targets))
    copy_jar_targets(utils.LIBS, target_root, jar_targets, args.maps)
    copy_other_targets(utils.GENERATED_LICENSE_DIR, target_root)
  else:
    assert args.commit_hash == None or args.version == None
    targets = map((lambda t: t[0] + '.jar'), jar_targets) + OTHER_TARGETS
    with utils.TempDir() as root:
      for target in targets:
        if args.commit_hash:
          download_hash(root, args.commit_hash, target)
          if args.maps and target not in OTHER_TARGETS:
            download_hash(root, args.commit_hash, target + '.map')
        else:
          assert args.version
          download_version(root, args.version, target)
          if args.maps and target not in OTHER_TARGETS:
            download_version(root, args.version, target + '.map')
      copy_jar_targets(root, target_root, jar_targets, args.maps)
      copy_other_targets(root, target_root)

if __name__ == '__main__':
  sys.exit(Main())
