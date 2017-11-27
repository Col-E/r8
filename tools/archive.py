#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import gradle
import create_maven_release
import d8
import os
import r8
import subprocess
import sys
import utils
import shutil
import zipfile

ARCHIVE_BUCKET = 'r8-releases'

def GetVersion():
  r8_version = r8.run(['--version'], build = False).splitlines()[0].strip()
  d8_version = d8.run(['--version'], build = False).splitlines()[0].strip()
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
    return False
  if not 'origin/master' in branches:
      raise Exception('We are not seeing origin/master '
                      'in a commit that have -dev in version')
  return True

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
  # Create maven release first which uses a build that exclude dependencies.
  create_maven_release.main(["--out", utils.LIBS])

  # Generate and copy the build that exclude dependencies.
  gradle.RunGradleExcludeDeps([utils.R8])
  shutil.copyfile(utils.R8_JAR, utils.R8_EXCLUDE_DEPS_JAR)

  # Ensure all archived artifacts has been built before archiving.
  gradle.RunGradle([utils.D8, utils.R8, utils.COMPATDX, utils.COMPATPROGUARD])
  version = GetVersion()
  is_master = IsMaster(version)
  if is_master:
    # On master we use the git hash to archive with
    print 'On master, using git hash for archiving'
    version = GetGitHash()

  with utils.TempDir() as temp:
    version_file = os.path.join(temp, 'r8-version.properties')
    with open(version_file,'w') as version_writer:
      version_writer.write('version.sha=' + GetGitHash() + '\n')
      version_writer.write(
          'releaser=go/r8bot (' + os.environ.get('BUILDBOT_SLAVENAME') + ')\n')
      version_writer.write('version-file.version.code=1\n')

    for file in [utils.D8_JAR,
                 utils.R8_JAR,
                 utils.R8_EXCLUDE_DEPS_JAR,
                 utils.COMPATDX_JAR,
                 utils.COMPATPROGUARD_JAR,
                 utils.MAVEN_ZIP,
                 utils.GENERATED_LICENSE]:
      file_name = os.path.basename(file)
      tagged_jar = os.path.join(temp, file_name)
      shutil.copyfile(file, tagged_jar)
      if file_name.endsWith('.jar'):
        with zipfile.ZipFile(tagged_jar, 'a') as zip:
          zip.write(version_file, os.path.basename(version_file))
      destination = GetUploadDestination(version, file_name, is_master)
      print('Uploading %s to %s' % (tagged_jar, destination))
      utils.upload_file_to_cloud_storage(tagged_jar, destination)
      print('File available at: %s' % GetUrl(version, file_name, is_master))

if __name__ == '__main__':
  sys.exit(Main())
