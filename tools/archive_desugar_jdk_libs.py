#!/usr/bin/env python3
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
import jdk
import optparse
import os
import re
import shutil
import subprocess
import sys
import utils

VERSION_FILE_JDK8 = 'VERSION.txt'
VERSION_FILE_JDK11 = 'VERSION_JDK11.txt'
LIBRARY_NAME = 'desugar_jdk_libs'

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--variant',
      help='.',
      choices = ['jdk8', 'jdk11'],
      default='jdk11')
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
  result.add_option('--build_only', '--build-only',
      help='Build desugared library without archiving.',
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
        version, 'in version file ' + version_file_name, allowPrerelease = True)
    return version


def Upload(options, file_name, storage_path, destination, is_main):
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

def CloneDesugaredLibrary(github_account, checkout_dir):
  git_utils.GitClone(
    'https://github.com/'
        + github_account + '/' + LIBRARY_NAME, checkout_dir)

def GetJavaEnv():
  java_env = dict(os.environ, JAVA_HOME = jdk.GetJdk11Home())
  java_env['PATH'] = java_env['PATH'] + os.pathsep + os.path.join(jdk.GetJdk11Home(), 'bin')
  java_env['GRADLE_OPTS'] = '-Xmx1g'
  return java_env


def BuildDesugaredLibrary(checkout_dir, variant):
  if (variant != 'jdk8' and variant != 'jdk11'):
    raise Exception('Variant ' + variant + 'is not supported')
  with utils.ChangedWorkingDirectory(checkout_dir):
    bazel = os.path.join(utils.BAZEL_TOOL, 'lib', 'bazel', 'bin', 'bazel')
    cmd = [
        bazel,
        '--bazelrc=/dev/null',
        'build',
        '--spawn_strategy=local',
        '--verbose_failures',
        'maven_release' + ('_jdk11' if variant == 'jdk11' else '')]
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd, env=GetJavaEnv())
    cmd = [bazel, 'shutdown']
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd, env=GetJavaEnv())

    # Locate the library jar and the maven zip with the jar from the
    # bazel build.
    if variant == 'jdk8':
      library_jar = os.path.join(
          checkout_dir, 'bazel-bin', 'src', 'share', 'classes', 'java', 'libjava.jar')
    else:
      library_jar = os.path.join(
          checkout_dir, 'bazel-bin', 'jdk11', 'src', 'd8_java_base_selected_with_addon.jar')
    maven_zip = os.path.join(
      checkout_dir,
      'bazel-bin',
      LIBRARY_NAME + ('_jdk11' if variant == 'jdk11' else '') +'.zip')
    return (library_jar, maven_zip)


def MustBeExistingDirectory(path):
  if (not os.path.exists(path) or not os.path.isdir(path)):
    raise Exception(path + ' does not exist or is not a directory')

def Main(argv):
  (options, args) = ParseOptions(argv)
  if (len(args) > 0):
    raise Exception('Unsupported arguments')
  if not utils.is_bot() and not (options.dry_run or options.build_only):
    raise Exception('You are not a bot, don\'t archive builds. '
        + 'Use --dry-run or --build-only to test locally')
  if options.dry_run_output:
     MustBeExistingDirectory(options.dry_run_output)
  if options.build_only:
     MustBeExistingDirectory(options.build_only)
  if utils.is_bot():
    archive.SetRLimitToMax()

  # Make sure bazel is extracted in third_party.
  utils.DownloadFromGoogleCloudStorage(utils.BAZEL_SHA_FILE)
  utils.DownloadFromGoogleCloudStorage(utils.JAVA8_SHA_FILE)
  utils.DownloadFromGoogleCloudStorage(utils.JAVA11_SHA_FILE)

  if options.build_only:
    with utils.TempDir() as checkout_dir:
      CloneDesugaredLibrary(options.github_account, checkout_dir)
      (library_jar, maven_zip) = BuildDesugaredLibrary(checkout_dir, options.variant)
      shutil.copyfile(
        library_jar,
        os.path.join(options.build_only, os.path.basename(library_jar)))
      shutil.copyfile(
        maven_zip,
        os.path.join(options.build_only, os.path.basename(maven_zip)))
      return

  # Only handling versioned desugar_jdk_libs.
  is_main = False

  with utils.TempDir() as checkout_dir:
    CloneDesugaredLibrary(options.github_account, checkout_dir)
    version = GetVersion(
      os.path.join(
        checkout_dir,
        VERSION_FILE_JDK11 if options.variant == 'jdk11' else VERSION_FILE_JDK8))

    destination = archive.GetVersionDestination(
        'gs://', LIBRARY_NAME + '/' + version, is_main)
    if utils.cloud_storage_exists(destination) and not options.dry_run:
      raise Exception(
          'Target archive directory %s already exists' % destination)

    (library_jar, maven_zip) = BuildDesugaredLibrary(checkout_dir, options.variant)

    storage_path = LIBRARY_NAME + '/' + version
    # Upload the jar file with the library.
    destination = archive.GetUploadDestination(
        storage_path, LIBRARY_NAME + '.jar', is_main)
    Upload(options, library_jar, storage_path, destination, is_main)

    # Upload the maven zip file with the library.
    destination = archive.GetUploadDestination(
        storage_path, LIBRARY_NAME + '.zip', is_main)
    Upload(options, maven_zip, storage_path, destination, is_main)

    # Upload the jar file for accessing GCS as a maven repro.
    maven_destination = archive.GetUploadDestination(
        utils.get_maven_path('desugar_jdk_libs', version),
        'desugar_jdk_libs-%s.jar' % version,
        is_main)
    if options.dry_run:
      print('Dry run, not actually creating maven repo')
    else:
      utils.upload_file_to_cloud_storage(library_jar, maven_destination)
      print('Maven repo root available at: %s' % archive.GetMavenUrl(is_main))


if __name__ == '__main__':
  sys.exit(Main(sys.argv[1:]))
