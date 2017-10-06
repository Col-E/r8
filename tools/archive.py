#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import d8
import os
import r8
import subprocess
import sys
import utils

ARCHIVE_BUCKET = 'r8-releases'

def GetVersion():
  r8_version = r8.run(['--version'], build = False).strip()
  d8_version = d8.run(['--version'], build = False).strip()
  # The version printed is "D8 vVERSION_NUMBER" and "R8 vVERSION_NUMBER"
  # Sanity check that versions match.
  if d8_version.split()[1] != r8_version.split()[1]:
    raise Exception(
        'Version mismatch: \n%s\n%s' % (d8_version, r8_version))
  return d8_version.split()[1]

def GetGitBranches():
  return subprocess.check_output(['git', 'show', '-s', '--pretty=%d', 'HEAD'])

def GetGitHash():
  return subprocess.check_output(['git', 'rev-parse', 'HEAD']).strip()

def IsMaster(version):
  branches = subprocess.check_output(['git', 'branch', '-r', '--contains',
                                      'HEAD'])
  if not version.endswith('-dev'):
    # Sanity check, we don't want to archive on top of release builds EVER
    # Note that even though we branch, we never push the bots to build the same
    # commit as master on a branch since we always change the version to
    # not have dev (or we crash here :-)).
    if 'origin/master' in branches:
      raise Exception('We are seeing origin/master in a commit that '
                      'don\'t have -dev in version')
    return False;
  if not 'origin/master' in branches:
      raise Exception('We are not seeing origin/master '
                      'in a commit that have -dev in version')
  return True;

def GetStorageDestination(storage_prefix, version, file_name, is_master):
  # We archive master commits under raw/master instead of directly under raw
  archive_dir = 'raw/master' if is_master else 'raw'
  return '%s%s/%s/%s/%s' % (storage_prefix, ARCHIVE_BUCKET, archive_dir,
                            version, file_name)

def GetUploadDestination(version, file_name, is_master):
  return GetStorageDestination('gs://', version, file_name, is_master)

def GetUrl(version, file_name, is_master):
  return GetStorageDestination('http://storage.googleapis.com/',
                               version, file_name, is_master)

def Main():
  if not 'BUILDBOT_BUILDERNAME' in os.environ:
    raise Exception('You are not a bot, don\'t archive builds')
  version = GetVersion()
  is_master = IsMaster(version)
  if is_master:
    # On master we use the git hash to archive with
    print 'On master, using git hash for archiving'
    version = GetGitHash()

  for jar in [utils.D8_JAR, utils.R8_JAR, utils.COMPATDX_JAR]:
    file_name = os.path.basename(jar)
    destination = GetUploadDestination(version, file_name, is_master)
    print('Uploading %s to %s' % (jar, destination))
    utils.upload_file_to_cloud_storage(jar, destination)
    print('File available at: %s' % GetUrl(version, file_name, is_master))

if __name__ == '__main__':
  sys.exit(Main())
