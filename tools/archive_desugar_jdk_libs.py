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
import defines
import git_utils
import gradle
import hashlib
import jdk
import optparse
import os
import re
import shutil
import subprocess
import sys
import utils
import zipfile

VERSION_FILE_JDK8 = 'VERSION.txt'
VERSION_FILE_JDK11_LEGACY = 'VERSION_JDK11_LEGACY.txt'
VERSION_FILE_JDK11_MINIMAL = 'VERSION_JDK11_MINIMAL.txt'
VERSION_FILE_JDK11 = 'VERSION_JDK11.txt'
VERSION_FILE_JDK11_NIO = 'VERSION_JDK11_NIO.txt'

VERSION_MAP = {
  'jdk8': VERSION_FILE_JDK8,
  'jdk11_legacy': VERSION_FILE_JDK11_LEGACY,
  'jdk11_minimal': VERSION_FILE_JDK11_MINIMAL,
  'jdk11': VERSION_FILE_JDK11,
  'jdk11_nio': VERSION_FILE_JDK11_NIO
}

GITHUB_REPRO = 'desugar_jdk_libs'

BASE_LIBRARY_NAME = 'desugar_jdk_libs'

LIBRARY_NAME_MAP = {
  'jdk8': BASE_LIBRARY_NAME,
  'jdk11_legacy': BASE_LIBRARY_NAME,
  'jdk11_minimal': BASE_LIBRARY_NAME + '_minimal',
  'jdk11': BASE_LIBRARY_NAME,
  'jdk11_nio': BASE_LIBRARY_NAME + '_nio'
}

MAVEN_RELEASE_TARGET_MAP = {
  'jdk8': 'maven_release',
  'jdk11_legacy': 'maven_release_jdk11_legacy',
  'jdk11_minimal': 'maven_release_jdk11_minimal',
  'jdk11': 'maven_release_jdk11',
  'jdk11_nio': 'maven_release_jdk11_nio'
}

MAVEN_RELEASE_ZIP = {
  'jdk8': BASE_LIBRARY_NAME + '.zip',
  'jdk11_legacy': BASE_LIBRARY_NAME + '_jdk11_legacy.zip',
  'jdk11_minimal': BASE_LIBRARY_NAME + '_jdk11_minimal.zip',
  'jdk11': BASE_LIBRARY_NAME + '_jdk11.zip',
  'jdk11_nio': BASE_LIBRARY_NAME + '_jdk11_nio.zip'
}

DESUGAR_JDK_LIBS_HASH_FILE = os.path.join(
    defines.THIRD_PARTY, 'openjdk', 'desugar_jdk_libs_11', 'desugar_jdk_libs_hash')


def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--variant',
      help="Variant(s) to build",
      metavar=('<variants(s)>'),
      choices=['jdk8', 'jdk11_legacy', 'jdk11_minimal', 'jdk11', 'jdk11_nio'],
      default=[],
      action='append')
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

def CloneDesugaredLibrary(github_account, checkout_dir, desugar_jdk_libs_hash):
  git_utils.GitClone(
    'https://github.com/'
        + github_account + '/' + GITHUB_REPRO, checkout_dir)
  git_utils.GitCheckout(desugar_jdk_libs_hash, checkout_dir)

def GetJavaEnv(androidHomeTemp):
  java_env = dict(os.environ, JAVA_HOME = jdk.GetJdk11Home())
  java_env['PATH'] = java_env['PATH'] + os.pathsep + os.path.join(jdk.GetJdk11Home(), 'bin')
  java_env['GRADLE_OPTS'] = '-Xmx1g'
  java_env['ANDROID_HOME'] = androidHomeTemp
  return java_env

def setUpFakeAndroidHome(androidHomeTemp):
  # Bazel will check if 30 is present then extract android.jar from 32.
  # We copy android.jar from third_party to mimic repository structure.
  subpath = os.path.join(androidHomeTemp, "platforms")
  cmd = ["mkdir", subpath]
  subprocess.check_call(cmd)
  subpath30 = os.path.join(subpath, "android-30")
  cmd = ["mkdir", subpath30]
  subprocess.check_call(cmd)
  subpath = os.path.join(subpath, "android-32")
  cmd = ["mkdir", subpath]
  subprocess.check_call(cmd)
  dest = os.path.join(subpath, "android.jar")
  sha = os.path.join(utils.THIRD_PARTY, "android_jar", "lib-v32.tar.gz.sha1")
  utils.DownloadFromGoogleCloudStorage(sha)
  src = os.path.join(utils.THIRD_PARTY, "android_jar", "lib-v32", "android.jar")
  cmd = ["cp", src, dest]
  subprocess.check_call(cmd)

def BuildDesugaredLibrary(checkout_dir, variant, version = None):
  if not variant in MAVEN_RELEASE_TARGET_MAP:
    raise Exception('Variant ' + variant + ' is not supported')
  if variant != 'jdk8' and variant != 'jdk11_legacy' and version is None:
    raise Exception('Variant ' + variant + ' require version for undesugaring')
  if variant != 'jdk8':
    # Hack to workaround b/256723819.
    os.remove(
      os.path.join(
        checkout_dir,
        "jdk11",
        "src",
        "java.base",
        "share",
        "classes",
        "java",
        "time",
        "format",
        "DesugarDateTimeFormatterBuilder.java"))
  with utils.ChangedWorkingDirectory(checkout_dir):
    with utils.TempDir() as androidHomeTemp:
      setUpFakeAndroidHome(androidHomeTemp)
      javaEnv = GetJavaEnv(androidHomeTemp)
      bazel = os.path.join(utils.BAZEL_TOOL, 'lib', 'bazel', 'bin', 'bazel')
      cmd = [
        bazel,
        '--bazelrc=/dev/null',
        'build',
        '--spawn_strategy=local',
        '--verbose_failures',
        MAVEN_RELEASE_TARGET_MAP[variant]]
      utils.PrintCmd(cmd)
      subprocess.check_call(cmd, env=javaEnv)
      cmd = [bazel, 'shutdown']
      utils.PrintCmd(cmd)
      subprocess.check_call(cmd, env=javaEnv)

    # Locate the library jar and the maven zip with the jar from the
    # bazel build.
    if variant == 'jdk8':
      library_jar = os.path.join(
          checkout_dir, 'bazel-bin', 'src', 'share', 'classes', 'java', 'libjava.jar')
    else:
      # All JDK11 variants use the same library code.
      library_jar = os.path.join(
          checkout_dir, 'bazel-bin', 'jdk11', 'src', 'd8_java_base_selected_with_addon.jar')
    maven_zip = os.path.join(
      checkout_dir,
      'bazel-bin',
      MAVEN_RELEASE_ZIP[variant])

    if variant != 'jdk8' and variant != 'jdk11_legacy':
      # The undesugaring is temporary...
      undesugared_maven_zip = os.path.join(checkout_dir, 'undesugared_maven')
      Undesugar(variant, maven_zip, version, undesugared_maven_zip)
      undesugared_maven_zip = os.path.join(checkout_dir, 'undesugared_maven.zip')
      return (library_jar, undesugared_maven_zip)
    else:
      return (library_jar, maven_zip)

def hash_for(file, hash):
  with open(file, 'rb') as f:
    while True:
      # Read chunks of 1MB
      chunk = f.read(2 ** 20)
      if not chunk:
        break
      hash.update(chunk)
  return hash.hexdigest()

def write_md5_for(file):
  hexdigest = hash_for(file, hashlib.md5())
  with (open(file + '.md5', 'w')) as file:
    file.write(hexdigest)

def write_sha1_for(file):
  hexdigest = hash_for(file, hashlib.sha1())
  with (open(file + '.sha1', 'w')) as file:
    file.write(hexdigest)

def Undesugar(variant, maven_zip, version, undesugared_maven_zip):
  gradle.RunGradle(['testJar', 'repackageTestDeps', '-Pno_internal'])
  with utils.TempDir() as tmp:
    with zipfile.ZipFile(maven_zip, 'r') as zip_ref:
      zip_ref.extractall(tmp)
    desugar_jdk_libs_jar = os.path.join(
          tmp,
          'com',
          'android',
          'tools',
          LIBRARY_NAME_MAP[variant],
          version,
          '%s-%s.jar' % (LIBRARY_NAME_MAP[variant], version))
    print(desugar_jdk_libs_jar)
    undesugared_jar = os.path.join(tmp, 'undesugared.jar')
    buildLibs = os.path.join(defines.REPO_ROOT, 'build', 'libs')
    cmd = [jdk.GetJavaExecutable(),
      '-cp',
      '%s:%s:%s' % (os.path.join(buildLibs, 'r8_with_deps.jar'), os.path.join(buildLibs, 'r8tests.jar'), os.path.join(buildLibs, 'test_deps_all.jar')),
      'com.android.tools.r8.desugar.desugaredlibrary.jdk11.DesugaredLibraryJDK11Undesugarer',
      desugar_jdk_libs_jar,
      undesugared_jar]
    print(cmd)
    try:
      output = subprocess.check_output(cmd, stderr = subprocess.STDOUT).decode('utf-8')
    except subprocess.CalledProcessError as e:
      print(e)
      print(e.output)
      raise e
    print(output)
    # Copy the undesugared jar into place and update the checksums.
    shutil.copyfile(undesugared_jar, desugar_jdk_libs_jar)
    write_md5_for(desugar_jdk_libs_jar)
    write_sha1_for(desugar_jdk_libs_jar)
    shutil.make_archive(undesugared_maven_zip, 'zip', tmp)
    print(undesugared_maven_zip)
    output = subprocess.check_output(['ls', '-l', os.path.dirname(undesugared_maven_zip)], stderr = subprocess.STDOUT).decode('utf-8')
    print(output)

def MustBeExistingDirectory(path):
  if (not os.path.exists(path) or not os.path.isdir(path)):
    raise Exception(path + ' does not exist or is not a directory')

def BuildAndUpload(options, variant):
  desugar_jdk_libs_hash = ''
  with open(DESUGAR_JDK_LIBS_HASH_FILE, 'r') as input_hash:
    desugar_jdk_libs_hash = input_hash.readline()
  if options.build_only:
    with utils.TempDir() as checkout_dir:
      CloneDesugaredLibrary(options.github_account, checkout_dir, desugar_jdk_libs_hash)
      (library_jar, maven_zip) = BuildDesugaredLibrary(checkout_dir, variant, desugar_jdk_libs_hash)
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
    CloneDesugaredLibrary(options.github_account, checkout_dir, desugar_jdk_libs_hash)
    version = GetVersion(os.path.join(checkout_dir, VERSION_MAP[variant]))

    destination = archive.GetVersionDestination(
        'gs://', LIBRARY_NAME_MAP[variant] + '/' + version, is_main)
    if utils.cloud_storage_exists(destination) and not options.dry_run:
      raise Exception(
          'Target archive directory %s already exists' % destination)

    (library_jar, maven_zip) = BuildDesugaredLibrary(checkout_dir, variant, version)

    storage_path = LIBRARY_NAME_MAP[variant] + '/' + version
    # Upload the jar file with the library.
    destination = archive.GetUploadDestination(
        storage_path, LIBRARY_NAME_MAP[variant] + '.jar', is_main)
    Upload(options, library_jar, storage_path, destination, is_main)

    # Upload the maven zip file with the library.
    destination = archive.GetUploadDestination(
        storage_path, MAVEN_RELEASE_ZIP[variant], is_main)
    Upload(options, maven_zip, storage_path, destination, is_main)

    # Upload the jar file for accessing GCS as a maven repro.
    maven_destination = archive.GetUploadDestination(
        utils.get_maven_path(LIBRARY_NAME_MAP[variant], version),
        '%s-%s.jar' % (LIBRARY_NAME_MAP[variant], version),
        is_main)
    if options.dry_run:
      print('Dry run, not actually creating maven repo')
    else:
      utils.upload_file_to_cloud_storage(library_jar, maven_destination)
      print('Maven repo root available at: %s' % archive.GetMavenUrl(is_main))

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
  utils.DownloadFromGoogleCloudStorage(utils.DESUGAR_JDK_LIBS_11_SHA_FILE)

  for v in options.variant:
    BuildAndUpload(options, v)

if __name__ == '__main__':
  sys.exit(Main(sys.argv[1:]))
