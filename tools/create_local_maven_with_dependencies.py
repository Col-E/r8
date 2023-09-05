#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os.path
import subprocess
import shutil
import sys

import utils

REPOSITORIES = [
  'Maven Central=https://repo1.maven.org/maven2/',
  'Google=https://maven.google.com/',
  "Gradle Plugins=https://plugins.gradle.org/m2/",
]

ANDRDID_SUPPORT_VERSION = '25.4.0'
ASM_VERSION = '9.5'
ESPRESSO_VERSION = '3.0.0'
FASTUTIL_VERSION = '7.2.1'
KOTLIN_METADATA_VERSION = '0.6.2'
KOTLIN_VERSION = '1.8.0'
GUAVA_VERSION = '31.1-jre'
GUAVA_VERSION_NEW = '32.1.2-jre'
GSON_VERSION = '2.10.1'
JAVASSIST_VERSION = '3.29.2-GA'
JUNIT_VERSION = '4.13-beta-2'
MOCKITO_VERSION = '2.10.0'
SMALI_VERSION = '3.0.3'
ERROR_PRONE_VERSION = '2.18.0'
TESTNG_VERSION = '6.10'

# Resource shrinker dependency versions
AAPT2_PROTO_VERSION = '8.2.0-alpha10-10154469'
PROTOBUF_VERSION = '3.19.3'
STUDIO_SDK_VERSION = '31.2.0-alpha10'

BUILD_DEPENDENCIES = [
  'com.google.code.gson:gson:{version}'.format(version = GSON_VERSION),
  'com.google.guava:guava:{version}'.format(version = GUAVA_VERSION),
  'it.unimi.dsi:fastutil:{version}'.format(version = FASTUTIL_VERSION),
  'org.jetbrains.kotlinx:kotlinx-metadata-jvm:{version}'.format(version = KOTLIN_METADATA_VERSION),
  'org.ow2.asm:asm:{version}'.format(version = ASM_VERSION),
  'org.ow2.asm:asm-util:{version}'.format(version = ASM_VERSION),
  'org.ow2.asm:asm-commons:{version}'.format(version = ASM_VERSION),
]

TEST_DEPENDENCIES = [
  'junit:junit:{version}'.format(version = JUNIT_VERSION),
  'com.android.tools.smali:smali:{version}'.format(version = SMALI_VERSION),
  'com.google.errorprone:error_prone_core:{version}'.format(version = ERROR_PRONE_VERSION),
  'org.javassist:javassist:{version}'.format(version = JAVASSIST_VERSION),
  'org.jetbrains.kotlin:kotlin-stdlib:{version}'.format(version = KOTLIN_VERSION),
  'org.jetbrains.kotlin:kotlin-reflect:{version}'.format(version = KOTLIN_VERSION),
  'org.mockito:mockito-core:{version}'.format(version = MOCKITO_VERSION),
  'org.testng:testng:{version}'.format(version = TESTNG_VERSION),
]

NEW_DEPENDENCIES = [
  'com.google.guava:guava:{version}'.format(version = GUAVA_VERSION_NEW),
  'org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:4.0.6',
  'org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.8.10',
  'org.jetbrains.kotlin:kotlin-gradle-plugin-idea:1.8.10',
  'org.jetbrains.kotlin:kotlin-reflect:1.6.10',
  'org.jetbrains.kotlin:kotlin-reflect:1.8.10',
  'org.jetbrains.kotlin:kotlin-script-runtime:1.8.10',
  'org.jetbrains.kotlin:kotlin-tooling-core:1.8.10',
  'net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin:3.0.1',
  'com.google.errorprone:javac:9+181-r4173-1',
  # Gradle 8.3
  'org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:4.1.0',
  'org.jetbrains.kotlin:kotlin-assignment-compiler-plugin-embeddable:1.9.0',
  'org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.9.0',
  'org.jetbrains.kotlin:kotlin-reflect:1.9.0',
  'org.jetbrains.kotlin:kotlin-script-runtime:1.9.0',
  'org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin-embeddable:1.9.0',
  # Resource shrinker
  'com.android.tools.build:aapt2-proto:{version}'.format(version = AAPT2_PROTO_VERSION),
  'com.android.tools.layoutlib:layoutlib-api:{version}'.format(version = STUDIO_SDK_VERSION),
  'com.android.tools:common:{version}'.format(version = STUDIO_SDK_VERSION),
  'com.android.tools:sdk-common:{version}'.format(version = STUDIO_SDK_VERSION),
  'com.google.protobuf:protobuf-java:{version}'.format(version = PROTOBUF_VERSION),
]

def dependencies_tar(dependencies_path):
  return os.path.join(
      os.path.dirname(dependencies_path),
      os.path.basename(dependencies_path) + '.tar.gz')

def dependencies_tar_sha1(dependencies_path):
  return os.path.join(
      os.path.dirname(dependencies_path),
      os.path.basename(dependencies_path) + '.tar.gz.sha1')

def remove_local_maven_repository(dependencies_path):
  if os.path.exists(dependencies_path):
    shutil.rmtree(dependencies_path)
  tar = dependencies_tar(dependencies_path)
  if os.path.exists(tar):
    os.remove(tar)
  sha1 = dependencies_tar_sha1(dependencies_path)
  if os.path.exists(sha1):
    os.remove(sha1)

def create_local_maven_repository(args, dependencies_path, repositories, dependencies):
  with utils.ChangedWorkingDirectory(args.studio):
    cmd = [
        os.path.join('tools', 'base', 'bazel', 'bazel'),
        'run',
        '//tools/base/bazel:local_maven_repository_generator_cli',
        '--',
        '--repo-path',
        dependencies_path,
        '--fetch']
    for repository in repositories:
      cmd.extend(['--remote-repo', repository])
    for dependency in dependencies:
      cmd.append(dependency)
    subprocess.check_call(cmd)

def parse_options():
  result = argparse.ArgumentParser(
      description='Create local Maven repository woth dependencies')
  result.add_argument('--studio',
                      metavar=('<path>'),
                      required=True,
                      help='Path to a studio-main checkout (to get the tool '
                          '//tools/base/bazel:local_maven_repository_generator_cli)')
  return result.parse_args()


def main():
  args = parse_options()

  dependencies_path = os.path.join(utils.THIRD_PARTY, 'dependencies')
  print("Downloading to " + dependencies_path)
  remove_local_maven_repository(dependencies_path)
  create_local_maven_repository(
      args, dependencies_path, REPOSITORIES, BUILD_DEPENDENCIES + TEST_DEPENDENCIES)

  dependencies_new_path = os.path.join(utils.THIRD_PARTY, 'dependencies_new')
  print("Downloading to " + dependencies_new_path)
  remove_local_maven_repository(dependencies_new_path)
  create_local_maven_repository(
      args, dependencies_new_path, REPOSITORIES, NEW_DEPENDENCIES)

  print("Uploading to Google Cloud Storage:")
  with utils.ChangedWorkingDirectory(utils.THIRD_PARTY):
    for dependency in ['dependencies', 'dependencies_new']:
      cmd = [
          'upload_to_google_storage.py',
          '-a',
          '--bucket',
          'r8-deps',
          dependency]
      subprocess.check_call(cmd)

if __name__ == '__main__':
  sys.exit(main())
