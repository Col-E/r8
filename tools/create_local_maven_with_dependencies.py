#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os.path
import shutil
import subprocess
import sys

import utils

# The local_maven_repository_generator orders the repositories by name, so
# prefix with X- to control the order, as many dependencies are present
# in several repositories. Save A- for additional local repository.
REPOSITORIES = [
    'B-Google=https://maven.google.com/',
    'C-Maven Central=https://repo1.maven.org/maven2/',
    "D-Gradle Plugins=https://plugins.gradle.org/m2/",
]

ANDRDID_SUPPORT_VERSION = '25.4.0'
ASM_VERSION = '9.5'
ESPRESSO_VERSION = '3.0.0'
FASTUTIL_VERSION = '7.2.1'
KOTLIN_METADATA_VERSION = '0.7.0'
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
    'com.google.code.gson:gson:{version}'.format(version=GSON_VERSION),
    'com.google.guava:guava:{version}'.format(version=GUAVA_VERSION),
    'it.unimi.dsi:fastutil:{version}'.format(version=FASTUTIL_VERSION),
    'org.jetbrains.kotlinx:kotlinx-metadata-jvm:{version}'.format(
        version=KOTLIN_METADATA_VERSION),
    'org.ow2.asm:asm:{version}'.format(version=ASM_VERSION),
    'org.ow2.asm:asm-util:{version}'.format(version=ASM_VERSION),
    'org.ow2.asm:asm-commons:{version}'.format(version=ASM_VERSION),
]

TEST_DEPENDENCIES = [
    'junit:junit:{version}'.format(version=JUNIT_VERSION),
    'com.android.tools.smali:smali:{version}'.format(version=SMALI_VERSION),
    'com.android.tools.smali:smali-util:{version}'.format(
        version=SMALI_VERSION),
    'com.google.errorprone:error_prone_core:{version}'.format(
        version=ERROR_PRONE_VERSION),
    'org.javassist:javassist:{version}'.format(version=JAVASSIST_VERSION),
    'org.jetbrains.kotlin:kotlin-stdlib:{version}'.format(
        version=KOTLIN_VERSION),
    'org.jetbrains.kotlin:kotlin-reflect:{version}'.format(
        version=KOTLIN_VERSION),
    'org.mockito:mockito-core:{version}'.format(version=MOCKITO_VERSION),
    'org.testng:testng:{version}'.format(version=TESTNG_VERSION),
]

NEW_DEPENDENCIES = [
    'com.google.guava:guava:{version}'.format(version=GUAVA_VERSION_NEW),
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
    'com.android.tools.build:aapt2-proto:{version}'.format(
        version=AAPT2_PROTO_VERSION),
    'com.android.tools.layoutlib:layoutlib-api:{version}'.format(
        version=STUDIO_SDK_VERSION),
    'com.android.tools:common:{version}'.format(version=STUDIO_SDK_VERSION),
    'com.android.tools:sdk-common:{version}'.format(version=STUDIO_SDK_VERSION),
    'com.google.protobuf:protobuf-java:{version}'.format(
        version=PROTOBUF_VERSION),
]

PLUGIN_DEPENDENCIES = [
  'org.spdx.sbom:org.spdx.sbom.gradle.plugin:0.2.0-r8-patch01',
  # See https://github.com/FasterXML/jackson-core/issues/999.
  'ch.randelshofer:fastdoubleparser:0.8.0',
]

def dependencies_tar(dependencies_path):
    return os.path.join(os.path.dirname(dependencies_path),
                        os.path.basename(dependencies_path) + '.tar.gz')


def dependencies_tar_sha1(dependencies_path):
    return os.path.join(os.path.dirname(dependencies_path),
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


def create_local_maven_repository(args, dependencies_path, repositories,
                                  dependencies):
    with utils.ChangedWorkingDirectory(args.studio):
        cmd = [
            os.path.join('tools', 'base', 'bazel', 'bazel'), 'run',
            '//tools/base/bazel:local_maven_repository_generator_cli', '--',
            '--repo-path', dependencies_path, '--fetch'
        ]
        for repository in repositories:
            cmd.extend(['--remote-repo', repository])
        for dependency in dependencies:
            cmd.append(dependency)
        subprocess.check_call(cmd)


def parse_options():
    result = argparse.ArgumentParser(
        description='Create local Maven repository woth dependencies')
    result.add_argument(
        '--studio',
        metavar=('<path>'),
        required=True,
        help='Path to a studio-main checkout (to get the tool '
        '//tools/base/bazel:local_maven_repository_generator_cli)')
    result.add_argument(
        '--plugin-deps',
        '--plugin_deps',
        default=False,
        action='store_true',
        help='Build repository Gradle plugin dependncies')
    result.add_argument(
        '--include-maven-local',
        '--include_maven_local',
        metavar=('<path>'),
        default=False,
        help='Path to maven local repository to include as dependency source')
    result.add_argument(
        '--no-upload',
        '--no_upload',
        default=False,
        action='store_true',
        help="Don't upload to Google CLoud Storage")
    return result.parse_args()


def set_utime(path):
    os.utime(path, (0, 0))
    for root, dirs, files in os.walk(path):
      for f in files:
          os.utime(os.path.join(root, f), (0, 0))
      for d in dirs:
          os.utime(os.path.join(root, d), (0, 0))

def main():
    args = parse_options()

    repositories = REPOSITORIES
    if args.include_maven_local:
        # Add the local repository as the first for it to take precedence.
        # Use A- prefix as current local_maven_repository_generator orderes by name.
        repositories = ['A-Local=file://%s' % args.include_maven_local] + REPOSITORIES

    dependencies = []

    if args.plugin_deps:
        dependencies_plugin_path = os.path.join(utils.THIRD_PARTY, 'dependencies_plugin')
        remove_local_maven_repository(dependencies_plugin_path)
        print("Downloading to " + dependencies_plugin_path)
        create_local_maven_repository(
            args, dependencies_plugin_path, repositories, PLUGIN_DEPENDENCIES)
        set_utime(dependencies_plugin_path)
        dependencies.append('dependencies_plugin')
    else:
        dependencies_path = os.path.join(utils.THIRD_PARTY, 'dependencies')
        remove_local_maven_repository(dependencies_path)
        print("Downloading to " + dependencies_path)
        create_local_maven_repository(
            args, dependencies_path, repositories, BUILD_DEPENDENCIES + TEST_DEPENDENCIES)
        set_utime(dependencies_path)
        dependencies.append('dependencies')
        dependencies_new_path = os.path.join(utils.THIRD_PARTY, 'dependencies_new')
        remove_local_maven_repository(dependencies_new_path)
        print("Downloading to " + dependencies_new_path)
        create_local_maven_repository(
           args, dependencies_new_path, repositories, NEW_DEPENDENCIES)
        set_utime(dependencies_new_path)
        dependencies.append('dependencies_new')

    upload_cmds = []
    for dependency in dependencies:
        upload_cmds.append([
            'upload_to_google_storage.py',
            '-a',
            '--bucket',
            'r8-deps',
            dependency])

    if not args.no_upload:
        print("Uploading to Google Cloud Storage:")
        with utils.ChangedWorkingDirectory(utils.THIRD_PARTY):
            for cmd in upload_cmds:
                subprocess.check_call(cmd)
    else:
        print("Not uploading to Google Cloud Storage. "
            + "Run the following commands in %s to do so manually" % utils.THIRD_PARTY)
        for cmd in upload_cmds:
            print(" ".join(cmd))


if __name__ == '__main__':
    sys.exit(main())
