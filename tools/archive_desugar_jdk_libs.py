#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# This script is designed to run on a buildbot to build from the source
# of https://github.com/google/desugar_jdk_libs and publish to the
# r8-release Cloud Storage Bucket.
#
# These files are uploaded:
#
#   raw/desugar_jdk_libs/<VERSION>/desugar_jdk_libs.jar
#   raw/desugar_jdk_libs/<VERSION>/desugar_jdk_libs.zip
#   raw/com/android/tools/desugar_jdk_libs/<VERSION>/desugar_jdk_libs-<VERSION>.jar
#
# The first two are the raw jar file and the maven compatible zip file. The
# third is the raw jar file placed and named so that the URL
# https://storage.googleapis.com/r8-releases/raw can be treated as a maven
# repository to fetch the artifact com.android.tools:desugar_jdk_libs:1.0.0

import archive
import git_utils
import optparse
import os
import re
import shutil
import subprocess
import sys
import utils

VERSION_FILE = 'VERSION.txt'
LIBRARY_NAME = 'desugar_jdk_libs'

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--dry-run', '--dry_run',
      help='Running on bot, use third_party dependency.',
      default=False,
      action='store_true')
  result.add_option('--dry-run-output', '--dry_run_output',
      help='Output directory for dry run.',
      type="string", action="store")
  result.add_option('--github-account', '--github_account',
      help='GitHub account to clone from.',
      default="google",
      type="string", action="store")
  (options, args) = result.parse_args(argv)
  return (options, args)


def GetVersion(version_file_name):
  with open(version_file_name, 'r') as version_file:
    lines = [line.strip() for line in version_file.readlines()]
    lines = [line for line in lines if not line.startswith('#')]
    if len(lines) != 1:
      raise Exception('Version file '
          + version_file + ' is expected to have exactly one line')
    version = lines[0].strip()
    utils.check_basic_semver_version(
        version, 'in version file ' + version_file_name)
    return version


def Upload(options, file_name, storage_path, destination, is_master):
  print('Uploading %s to %s' % (file_name, destination))
  if options.dry_run:
    if options.dry_run_output:
      dry_run_destination = \
          os.path.join(options.dry_run_output, os.path.basename(file_name))
      print('Dry run, not actually uploading. Copying to '
        + dry_run_destination)
      shutil.copyfile(file_name, dry_run_destination)
    else:
      print('Dry run, not actually uploading')
  else:
    utils.upload_file_to_cloud_storage(file_name, destination)
    print('File available at: %s' %
        destination.replace('gs://', 'https://storage.googleapis.com/', 1))


def Main(argv):
  (options, args) = ParseOptions(argv)
  if (len(args) > 0):
    raise Exception('Unsupported arguments')
  if not utils.is_bot() and not options.dry_run:
    raise Exception('You are not a bot, don\'t archive builds. '
        + 'Use --dry-run to test locally')
  if (options.dry_run_output and
      (not os.path.exists(options.dry_run_output) or
       not os.path.isdir(options.dry_run_output))):
    raise Exception(options.dry_run_output
        + ' does not exist or is not a directory')

  if utils.is_bot():
    archive.SetRLimitToMax()

  # Make sure bazel is extracted in third_party.
  utils.DownloadFromGoogleCloudStorage(utils.BAZEL_SHA_FILE)
  utils.DownloadFromGoogleCloudStorage(utils.JAVA8_SHA_FILE)

  # Only handling versioned desugar_jdk_libs.
  is_master = False

  with utils.TempDir() as checkout_dir:
    git_utils.GitClone(
      'https://github.com/'
          + options.github_account + '/' + LIBRARY_NAME, checkout_dir)
    with utils.ChangedWorkingDirectory(checkout_dir):
      version = GetVersion(VERSION_FILE)

      destination = archive.GetVersionDestination(
          'gs://', LIBRARY_NAME + '/' + version, is_master)
      if utils.cloud_storage_exists(destination) and not options.dry_run:
        raise Exception(
            'Target archive directory %s already exists' % destination)

      bazel = os.path.join(utils.BAZEL_TOOL, 'lib', 'bazel', 'bin', 'bazel')
      cmd = [bazel, 'build', 'maven_release']
      utils.PrintCmd(cmd)
      subprocess.check_call(cmd)
      cmd = [bazel, 'shutdown']
      utils.PrintCmd(cmd)
      subprocess.check_call(cmd)

      # Locate the library jar and the maven zip with the jar from the
      # bazel build.
      library_jar = os.path.join(
          'bazel-bin', 'src', 'share', 'classes', 'java', 'libjava.jar')
      maven_zip = os.path.join('bazel-bin', LIBRARY_NAME +'.zip')

      storage_path = LIBRARY_NAME + '/' + version
      # Upload the jar file with the library.
      destination = archive.GetUploadDestination(
          storage_path, LIBRARY_NAME + '.jar', is_master)
      Upload(options, library_jar, storage_path, destination, is_master)

      # Upload the maven zip file with the library.
      destination = archive.GetUploadDestination(
          storage_path, LIBRARY_NAME + '.zip', is_master)
      Upload(options, maven_zip, storage_path, destination, is_master)

      # Upload the jar file for accessing GCS as a maven repro.
      maven_destination = archive.GetUploadDestination(
          utils.get_maven_path('desugar_jdk_libs', version),
          'desugar_jdk_libs-%s.jar' % version,
          is_master)
      if options.dry_run:
        print('Dry run, not actually creating maven repo')
      else:
        utils.upload_file_to_cloud_storage(library_jar, maven_destination)
        print('Maven repo root available at: %s' % archive.GetMavenUrl(is_master))


if __name__ == '__main__':
  sys.exit(Main(sys.argv[1:]))
