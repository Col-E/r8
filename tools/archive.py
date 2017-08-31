#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import d8
import os
import r8
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

def GetStorageDestination(storage_prefix, version, file_name):
  return '%s%s/raw/%s/%s' % (storage_prefix, ARCHIVE_BUCKET, version, file_name)

def GetUploadDestination(version, file_name):
  return GetStorageDestination('gs://', version, file_name)

def GetUrl(version, file_name):
  return GetStorageDestination('http://storage.googleapis.com/',
                               version,
                               file_name)

def Main():
  if not 'BUILDBOT_BUILDERNAME' in os.environ:
    raise Exception('You are not a bot, don\'t archive builds')
  version = GetVersion()
  for jar in [utils.D8_JAR, utils.R8_JAR]:
    file_name = os.path.basename(jar)
    destination = GetUploadDestination(version, file_name)
    print('Uploading %s to %s' % (jar, destination))
    utils.upload_file_to_cloud_storage(jar, destination)
    print('File available at: %s' % GetUrl(version, file_name))

if __name__ == '__main__':
  sys.exit(Main())
