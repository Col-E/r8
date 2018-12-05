#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import create_maven_release
import gradle
import os
import shutil
import subprocess
import sys
import toolhelper
import utils
import zipfile
from build_r8lib import build_r8lib

ARCHIVE_BUCKET = 'r8-releases'

def GetToolVersion(jar_path):
  output = subprocess.check_output(['java', '-jar', jar_path, '--version'])
  return output.splitlines()[0].strip()

def GetVersion():
  r8_version = GetToolVersion(utils.R8_JAR)
  d8_version = GetToolVersion(utils.D8_JAR)
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

def GetStorageDestination(storage_prefix,
                          version_or_path,
                          file_name,
                          is_master):
  # We archive master commits under raw/master instead of directly under raw
  version_dir = GetVersionDestination(storage_prefix,
                                      version_or_path,
                                      is_master)
  return '%s/%s' % (version_dir, file_name)

def GetVersionDestination(storage_prefix, version_or_path, is_master):
  archive_dir = 'raw/master' if is_master else 'raw'
  return '%s%s/%s/%s' % (storage_prefix, ARCHIVE_BUCKET,
                         archive_dir, version_or_path)

def GetUploadDestination(version_or_path, file_name, is_master):
  return GetStorageDestination('gs://', version_or_path, file_name, is_master)

def GetUrl(version_or_path, file_name, is_master):
  return GetStorageDestination('http://storage.googleapis.com/',
                               version_or_path, file_name, is_master)

def GetMavenUrl(is_master):
  return GetVersionDestination('http://storage.googleapis.com/', '', is_master)

def Main():
  if not 'BUILDBOT_BUILDERNAME' in os.environ:
    raise Exception('You are not a bot, don\'t archive builds')

  # Generate an r8-ed build without dependencies.
  # Note: build_r8lib does a gradle-clean, this must be the first command.
  build_r8lib('r8', True, True, utils.R8_KEEP_RULES, utils.R8_EXCLUDE_DEPS_JAR)

  # Create maven release which uses a build that exclude dependencies.
  create_maven_release.main(["--out", utils.LIBS])

  # Generate and copy a full build without dependencies.
  gradle.RunGradleExcludeDeps([utils.R8, utils.R8_SRC])
  shutil.copyfile(utils.R8_JAR, utils.R8_FULL_EXCLUDE_DEPS_JAR)

  # Ensure all archived artifacts has been built before archiving.
  # The target tasks postfixed by 'r8' depend on the actual target task so
  # building it invokes the original task first.
  gradle.RunGradle(map((lambda t: t + 'r8'),
    [utils.D8, utils.R8, utils.COMPATDX, utils.COMPATPROGUARD]))
  version = GetVersion()
  is_master = IsMaster(version)
  if is_master:
    # On master we use the git hash to archive with
    print 'On master, using git hash for archiving'
    version = GetGitHash()

  destination = GetVersionDestination('gs://', version, is_master)
  if utils.cloud_storage_exists(destination):
    raise Exception('Target archive directory %s already exists' % destination)
  with utils.TempDir() as temp:
    version_file = os.path.join(temp, 'r8-version.properties')
    with open(version_file,'w') as version_writer:
      version_writer.write('version.sha=' + GetGitHash() + '\n')
      version_writer.write(
          'releaser=go/r8bot (' + os.environ.get('BUILDBOT_SLAVENAME') + ')\n')
      version_writer.write('version-file.version.code=1\n')

    for file in [utils.D8_JAR, utils.D8R8_JAR,
                 utils.R8_JAR, utils.R8R8_JAR,
                 utils.R8_SRC_JAR,
                 utils.R8_FULL_EXCLUDE_DEPS_JAR,
                 utils.R8_EXCLUDE_DEPS_JAR,
                 utils.COMPATDX_JAR, utils.COMPATDXR8_JAR,
                 utils.COMPATPROGUARD_JAR, utils.COMPATPROGUARDR8_JAR,
                 utils.MAVEN_ZIP,
                 utils.GENERATED_LICENSE]:
      file_name = os.path.basename(file)
      tagged_jar = os.path.join(temp, file_name)
      shutil.copyfile(file, tagged_jar)
      if file_name.endswith('.jar') and not file_name.endswith('-src.jar'):
        with zipfile.ZipFile(tagged_jar, 'a') as zip:
          zip.write(version_file, os.path.basename(version_file))
      destination = GetUploadDestination(version, file_name, is_master)
      print('Uploading %s to %s' % (tagged_jar, destination))
      utils.upload_file_to_cloud_storage(tagged_jar, destination)
      print('File available at: %s' % GetUrl(version, file_name, is_master))
      if file == utils.R8_JAR:
        # Upload R8 to a maven compatible location.
        maven_dst = GetUploadDestination(utils.get_maven_path(version),
                                         'r8-%s.jar' % version, is_master)
        utils.upload_file_to_cloud_storage(tagged_jar, maven_dst)
        print('Maven repo root available at: %s' % GetMavenUrl(is_master))


if __name__ == '__main__':
  sys.exit(Main())
