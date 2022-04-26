#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
from os.path import join
import shutil
import subprocess
import sys

import utils
import create_maven_release

def parse_options():
  result = argparse.ArgumentParser(
    description='Local desugared library repository for JDK 11 legacy configuration')
  result.add_argument('--repo-root', '--repo_root',
                      default='/tmp/repo',
                      metavar=('<path>'),
                      help='Location for Maven repository.')
  args = result.parse_args()
  return args

def jar_or_pom_file(unzip_dir, artifact, version, extension):
  return join(
      unzip_dir,
      'com',
      'android',
      'tools',
      artifact,
      version,
      artifact + '-' + version + '.' + extension)

def jar_file(unzip_dir, artifact, version):
  return jar_or_pom_file(unzip_dir, artifact, version, 'jar')

def pom_file(unzip_dir, artifact, version):
  return jar_or_pom_file(unzip_dir, artifact, version, 'pom')

def main():
  args = parse_options()
  shutil.rmtree(args.repo_root, ignore_errors=True)
  utils.makedirs_if_needed(args.repo_root)
  with utils.TempDir() as tmp_dir:
    version = utils.desugar_configuration_version(utils.DESUGAR_CONFIGURATION_JDK11_LEGACY)

    # Checkout desugar_jdk_libs from GitHub
    checkout_dir = join(tmp_dir, 'desugar_jdk_libs')
    utils.RunCmd(['git', 'clone', 'https://github.com/google/desugar_jdk_libs.git', checkout_dir])
    with utils.ChangedWorkingDirectory(checkout_dir):
      with open('VERSION_JDK11.txt') as version_file:
        version_file_lines = version_file.readlines()
        for line in version_file_lines:
          if not line.startswith('#'):
            desugar_jdk_libs_version = line.strip()
            if (version != desugar_jdk_libs_version):
              raise Exception(
                "Version mismatch. Configuration has version '"
                + version
                + "', and desugar_jdk_libs has version '"
                + desugar_jdk_libs_version
                + "'")

    # Build desugared library configuration.
    print("Building desugared library configuration " + version)
    maven_zip = join(tmp_dir, 'desugar_configuration.zip')
    create_maven_release.generate_desugar_configuration_maven_zip(
      maven_zip,
      utils.DESUGAR_CONFIGURATION_JDK11_LEGACY,
      utils.DESUGAR_IMPLEMENTATION_JDK11)
    unzip_dir = join(tmp_dir, 'desugar_jdk_libs_configuration_unzipped')
    cmd = ['unzip', '-q', maven_zip, '-d', unzip_dir]
    utils.RunCmd(cmd)
    cmd = [
      'mvn',
      'deploy:deploy-file',
      '-Durl=file:' + args.repo_root,
      '-DrepositoryId=someName',
      '-Dfile=' + jar_file(unzip_dir, 'desugar_jdk_libs_configuration', version),
      '-DpomFile=' + pom_file(unzip_dir, 'desugar_jdk_libs_configuration', version)]
    utils.RunCmd(cmd)

    # Build desugared library.
    print("Building desugared library " + version)
    with utils.ChangedWorkingDirectory(checkout_dir):
      utils.RunCmd([
          'bazel',
          '--bazelrc=/dev/null',
          'build',
          '--spawn_strategy=local',
          '--verbose_failures',
          ':maven_release_jdk11'])
    unzip_dir = join(tmp_dir, 'desugar_jdk_libs_unzipped')
    cmd = [
        'unzip',
        '-q',
        join(checkout_dir, 'bazel-bin', 'desugar_jdk_libs_jdk11.zip'),
        '-d',
        unzip_dir]
    utils.RunCmd(cmd)
    cmd = [
      'mvn',
      'deploy:deploy-file',
      '-Durl=file:' + args.repo_root,
      '-DrepositoryId=someName',
      '-Dfile=' + jar_file(unzip_dir, 'desugar_jdk_libs', version),
      '-DpomFile=' + pom_file(unzip_dir, 'desugar_jdk_libs', version)]
    utils.RunCmd(cmd)

    print()
    print("Artifacts:")
    print("  com.android.tools:desugar_jdk_libs_configuration:" + version)
    print("  com.android.tools:desugar_jdk_libs:" + version)
    print()
    print("deployed to Maven repository at " + args.repo_root + ".")
    print()
    print("Add")
    print()
    print("  maven {")
    print("    url uri('file://" + args.repo_root + "')")
    print("  }")
    print()
    print("to dependencyResolutionManagement.repositories in settings.gradle.")
    print()
    print("Remember to run gradle with --refresh-dependencies "
      + "(./gradlew --refresh-dependencies ...) "
      + "to ensure the cache is not used when the same version is published.")

if __name__ == '__main__':
  sys.exit(main())
