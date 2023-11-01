#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
from enum import Enum
import os
from os.path import join
import shutil
import subprocess
import sys
import urllib.request

import gradle
import utils
import create_maven_release
import archive_desugar_jdk_libs


class Variant(Enum):
    jdk8 = 'jdk8'
    jdk11_legacy = 'jdk11_legacy'
    jdk11_minimal = 'jdk11_minimal'
    jdk11 = 'jdk11'
    jdk11_nio = 'jdk11_nio'

    def __str__(self):
        return self.value


def parse_options():
    parser = argparse.ArgumentParser(
        description=
        'Local desugared library repository for desugared library configurations'
    )
    parser.add_argument('--repo-root',
                        '--repo_root',
                        default='/tmp/repo',
                        metavar=('<path>'),
                        help='Location for Maven repository.')
    parser.add_argument(
        '--clear-repo',
        '--clear_repo',
        default=False,
        action='store_true',
        help='Clear the Maven repository so it only has one version present')
    parser.add_argument('--variant', type=Variant, choices=list(Variant))
    parser.add_argument(
        '--desugar-jdk-libs-checkout',
        '--desugar_jdk_libs_checkout',
        default=None,
        metavar=('<path>'),
        help='Use existing checkout of github.com/google/desugar_jdk_libs.')
    parser.add_argument(
        '--desugar-jdk-libs-revision',
        '--desugar_jdk_libs_revision',
        default=None,
        metavar=('<revision>'),
        help='Revision of github.com/google/desugar_jdk_libs to use.')
    parser.add_argument(
        '--release-version',
        '--release_version',
        metavar=('<version>'),
        help=
        'The desugared library release version to use. This will pull from the archived releases'
    )
    args = parser.parse_args()
    return args


def jar_or_pom_file(unzip_dir, artifact, version, extension):
    return join(unzip_dir, 'com', 'android', 'tools', artifact, version,
                artifact + '-' + version + '.' + extension)


def jar_file(unzip_dir, artifact, version):
    return jar_or_pom_file(unzip_dir, artifact, version, 'jar')


def pom_file(unzip_dir, artifact, version):
    return jar_or_pom_file(unzip_dir, artifact, version, 'pom')


def run(args):
    artifact = None
    configuration_artifact = None
    configuration = None
    conversions = None
    implementation = None
    version_file = None
    implementation_build_target = None
    implementation_maven_zip = None
    release_archive_location = None
    match args.variant:
        case Variant.jdk8:
            artifact = 'desugar_jdk_libs'
            configuration_artifact = 'desugar_jdk_libs_configuration'
            configuration = utils.DESUGAR_CONFIGURATION
            conversions = utils.LIBRARY_DESUGAR_CONVERSIONS_LEGACY_ZIP
            implementation = utils.DESUGAR_IMPLEMENTATION
            version_file = 'VERSION.txt'
            implementation_build_target = ':maven_release'
            implementation_maven_zip = 'desugar_jdk_libs.zip'
            release_archive_location = 'desugar_jdk_libs'
        case Variant.jdk11_legacy:
            artifact = 'desugar_jdk_libs'
            configuration_artifact = 'desugar_jdk_libs_configuration'
            configuration = utils.DESUGAR_CONFIGURATION_JDK11_LEGACY
            conversions = utils.LIBRARY_DESUGAR_CONVERSIONS_LEGACY_ZIP
            implementation = utils.DESUGAR_IMPLEMENTATION_JDK11
            version_file = 'VERSION_JDK11_LEGACY.txt'
            implementation_build_target = ':maven_release_jdk11_legacy'
            implementation_maven_zip = 'desugar_jdk_libs_jdk11_legacy.zip'
            release_archive_location = 'desugar_jdk_libs'
        case Variant.jdk11_minimal:
            artifact = 'desugar_jdk_libs_minimal'
            configuration_artifact = 'desugar_jdk_libs_configuration_minimal'
            configuration = utils.DESUGAR_CONFIGURATION_JDK11_MINIMAL
            conversions = utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP
            implementation = utils.DESUGAR_IMPLEMENTATION_JDK11
            version_file = 'VERSION_JDK11_MINIMAL.txt'
            implementation_build_target = ':maven_release_jdk11_minimal'
            implementation_maven_zip = 'desugar_jdk_libs_jdk11_minimal.zip'
            release_archive_location = 'desugar_jdk_libs_minimal'
        case Variant.jdk11:
            artifact = 'desugar_jdk_libs'
            configuration_artifact = 'desugar_jdk_libs_configuration'
            configuration = utils.DESUGAR_CONFIGURATION_JDK11
            conversions = utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP
            implementation = utils.DESUGAR_IMPLEMENTATION_JDK11
            version_file = 'VERSION_JDK11.txt'
            implementation_build_target = ':maven_release_jdk11'
            implementation_maven_zip = 'desugar_jdk_libs_jdk11.zip'
            release_archive_location = 'desugar_jdk_libs'
        case Variant.jdk11_nio:
            artifact = 'desugar_jdk_libs_nio'
            configuration_artifact = 'desugar_jdk_libs_configuration_nio'
            configuration = utils.DESUGAR_CONFIGURATION_JDK11_NIO
            conversions = utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP
            implementation = utils.DESUGAR_IMPLEMENTATION_JDK11
            version_file = 'VERSION_JDK11_NIO.txt'
            implementation_build_target = ':maven_release_jdk11_nio'
            implementation_maven_zip = 'desugar_jdk_libs_jdk11_nio.zip'
            release_archive_location = 'desugar_jdk_libs_nio'
    implementation_build_output = join('bazel-bin', implementation_maven_zip)
    gradle.RunGradle([utils.GRADLE_TASK_R8])
    with utils.TempDir() as tmp_dir:
        (name,
         configuration_version) = utils.desugar_configuration_name_and_version(
             configuration, False)
        if (args.release_version != None and
                args.release_version != configuration_version):
            raise Exception(
                'Configuration version %s is different for specified version %s'
                % (configuration_version, version))
        version = configuration_version
        print("Name: %s" % name)
        print("Version: %s" % version)
        # Checkout desugar_jdk_libs from GitHub
        use_existing_checkout = args.desugar_jdk_libs_checkout != None
        checkout_dir = (args.desugar_jdk_libs_checkout if use_existing_checkout
                        else join(tmp_dir, 'desugar_jdk_libs'))
        if (not args.release_version and not use_existing_checkout):
            subprocess.check_call([
                'git', 'clone',
                'https://github.com/google/desugar_jdk_libs.git', checkout_dir
            ])
            if (args.desugar_jdk_libs_revision):
                subprocess.check_call([
                    'git', '-C', checkout_dir, 'checkout',
                    args.desugar_jdk_libs_revision
                ])
            with utils.ChangedWorkingDirectory(checkout_dir):
                with open(version_file) as version_file:
                    version_file_lines = version_file.readlines()
                    for line in version_file_lines:
                        if not line.startswith('#'):
                            desugar_jdk_libs_version = line.strip()
                            if (version != desugar_jdk_libs_version):
                                raise Exception(
                                    "Version mismatch. Configuration has version '"
                                    + version +
                                    "', and desugar_jdk_libs has version '" +
                                    desugar_jdk_libs_version + "'")

        # Build desugared library configuration.
        print("Building desugared library configuration " + version)
        maven_zip = join(tmp_dir, 'desugar_configuration.zip')
        create_maven_release.generate_desugar_configuration_maven_zip(
            maven_zip, configuration, implementation, conversions)
        unzip_dir = join(tmp_dir, 'desugar_jdk_libs_configuration_unzipped')
        cmd = ['unzip', '-q', maven_zip, '-d', unzip_dir]
        subprocess.check_call(cmd)
        cmd = [
            'mvn', 'deploy:deploy-file', '-Durl=file:' + args.repo_root,
            '-DrepositoryId=someName',
            '-Dfile=' + jar_file(unzip_dir, configuration_artifact, version),
            '-DpomFile=' + pom_file(unzip_dir, configuration_artifact, version)
        ]
        subprocess.check_call(cmd)

        undesugared_if_needed = None
        if not args.release_version:
            # Build desugared library.
            print("Building desugared library " + version)
            with utils.ChangedWorkingDirectory(checkout_dir):
                subprocess.check_call([
                    'bazel', '--bazelrc=/dev/null', 'build',
                    '--spawn_strategy=local', '--verbose_failures',
                    implementation_build_target
                ])

            # Undesugar desugared library if needed.
            undesugared_if_needed = join(checkout_dir,
                                         implementation_build_output)
            if (args.variant == Variant.jdk11_minimal or
                    args.variant == Variant.jdk11 or
                    args.variant == Variant.jdk11_nio):
                undesugared_if_needed = join(tmp_dir, 'undesugared.zip')
                archive_desugar_jdk_libs.Undesugar(
                    str(args.variant),
                    join(checkout_dir, implementation_build_output), version,
                    undesugared_if_needed)
        else:
            # Download the already built and undesugared library from release archive.
            undesugared_if_needed = join(tmp_dir, implementation_maven_zip)
            urllib.request.urlretrieve(
                ('https://storage.googleapis.com/r8-releases/raw/%s/%s/%s' %
                 (release_archive_location, version, implementation_maven_zip)),
                undesugared_if_needed)

        unzip_dir = join(tmp_dir, 'desugar_jdk_libs_unzipped')
        cmd = ['unzip', '-q', undesugared_if_needed, '-d', unzip_dir]
        subprocess.check_call(cmd)
        cmd = [
            'mvn', 'deploy:deploy-file', '-Durl=file:' + args.repo_root,
            '-DrepositoryId=someName',
            '-Dfile=' + jar_file(unzip_dir, artifact, version),
            '-DpomFile=' + pom_file(unzip_dir, artifact, version)
        ]
        subprocess.check_call(cmd)

        print()
        print("Artifacts:")
        print("  com.android.tools:%s:%s" % (configuration_artifact, version))
        print("  com.android.tools:%s:%s" % (artifact, version))
        print()
        print("deployed to Maven repository at " + args.repo_root + ".")
        print()
        print("For Kotlin Script add")
        print()
        print("  maven(url = \"file:///tmp/repo\")")
        print()
        print(
            "to dependencyResolutionManagement.repositories in settings.gradle.kts, and use"
        )
        print(
            'the "changing" property of the coreLibraryDesugaring dependency:')
        print()
        print("  coreLibraryDesugaring('com.android.tools:%s:%s') {" %
              (artifact, version))
        print("    isChanging = true")
        print("  }")
        print()

        print("For Grovey add")
        print()
        print("  maven {")
        print("    url uri('file://" + args.repo_root + "')")
        print("  }")
        print()
        print(
            "to dependencyResolutionManagement.repositories in settings.gradle, and use"
        )
        print(
            'the "changing" property of the coreLibraryDesugaring dependency:')
        print()
        print("  coreLibraryDesugaring('com.android.tools:%s:%s') {" %
              (artifact, version))
        print("    changing = true")
        print("  }")
        print()
        print(
            'If not using the "changing" property remember to run gradle with ' +
            "--refresh-dependencies (./gradlew --refresh-dependencies ...) " +
            "to ensure the cache is not used when the same version is published."
            + "multiple times.")


def main():
    args = parse_options()
    if args.desugar_jdk_libs_checkout and args.release_version:
        raise Exception(
            'Options --desugar-jdk-libs-checkout and --release-version are mutually exclusive'
        )
    if args.desugar_jdk_libs_revision and args.release_version:
        raise Exception(
            'Options --desugar-jdk-libs-revision and --release-version are mutually exclusive'
        )
    if args.desugar_jdk_libs_checkout and args.desugar_jdk_libs_revision:
        raise Exception(
            'Options --desugar-jdk-libs-checkout and --desugar-jdk-libs-revision are mutually exclusive'
        )
    if args.clear_repo:
        shutil.rmtree(args.repo_root, ignore_errors=True)
    utils.makedirs_if_needed(args.repo_root)
    if (args.variant):
        run(args)
    else:
        for v in Variant:
            args.variant = v
            run(args)


if __name__ == '__main__':
    sys.exit(main())
