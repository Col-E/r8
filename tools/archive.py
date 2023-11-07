#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import jdk
import optparse
import os

import create_maven_release
import gradle

try:
    import resource
except ImportError:
    # Not a Unix system. Do what Gandalf tells you not to.
    pass
import shutil
import subprocess
import sys
import utils
import zipfile

ARCHIVE_BUCKET = 'r8-releases'


def ParseOptions():
    result = optparse.OptionParser()
    result.add_option('--dry-run',
                      '--dry_run',
                      help='Build only, no upload.',
                      default=False,
                      action='store_true')
    result.add_option('--dry-run-output',
                      '--dry_run_output',
                      help='Output directory for \'build only, no upload\'.',
                      type="string",
                      action="store")
    result.add_option(
        '--skip-gradle-build',
        '--skip_gradle_build',
        help='Skip Gradle build. Can only be used for local testing.',
        default=False,
        action='store_true')
    return result.parse_args()


def GetVersion():
    output = subprocess.check_output([
        jdk.GetJavaExecutable(), '-cp', utils.R8_JAR, 'com.android.tools.r8.R8',
        '--version'
    ]).decode('utf-8')
    r8_version = output.splitlines()[0].strip()
    return r8_version.split()[1]


def GetGitBranches():
    return subprocess.check_output(['git', 'show', '-s', '--pretty=%d', 'HEAD'])


def GetGitHash():
    return subprocess.check_output(['git', 'rev-parse',
                                    'HEAD']).decode('utf-8').strip()


def IsMain(version):
    branches = subprocess.check_output(
        ['git', 'branch', '-r', '--contains', 'HEAD']).decode('utf-8')
    # CL runs from gerrit does not have a branch, we always treat them as main
    # commits to archive these to the hash based location
    if len(branches) == 0:
        return True
    if not version == 'main':
        # Sanity check, we don't want to archive on top of release builds EVER
        # Note that even though we branch, we never push the bots to build the same
        # commit as main on a branch since we always change the version to
        # not be just 'main' (or we crash here :-)).
        if 'origin/main' in branches:
            raise Exception('We are seeing origin/main in a commit that '
                            'don\'t have \'main\' as version')
        return False
    if not 'origin/main' in branches:
        raise Exception('We are not seeing origin/main '
                        'in a commit that have \'main\' as version')
    return True


def GetStorageDestination(storage_prefix, version_or_path, file_name, is_main):
    # We archive main commits under raw/main instead of directly under raw
    version_dir = GetVersionDestination(storage_prefix, version_or_path,
                                        is_main)
    return '%s/%s' % (version_dir, file_name)


def GetVersionDestination(storage_prefix, version_or_path, is_main):
    archive_dir = 'raw/main' if is_main else 'raw'
    return '%s%s/%s/%s' % (storage_prefix, ARCHIVE_BUCKET, archive_dir,
                           version_or_path)


def GetUploadDestination(version_or_path, file_name, is_main):
    return GetStorageDestination('gs://', version_or_path, file_name, is_main)


def GetUrl(version_or_path, file_name, is_main):
    return GetStorageDestination('https://storage.googleapis.com/',
                                 version_or_path, file_name, is_main)


def GetMavenUrl(is_main):
    return GetVersionDestination('https://storage.googleapis.com/', '', is_main)


def SetRLimitToMax():
    (soft, hard) = resource.getrlimit(resource.RLIMIT_NOFILE)
    resource.setrlimit(resource.RLIMIT_NOFILE, (hard, hard))


def PrintResourceInfo():
    (soft, hard) = resource.getrlimit(resource.RLIMIT_NOFILE)
    print('INFO: Open files soft limit: %s' % soft)
    print('INFO: Open files hard limit: %s' % hard)


def Main():
    (options, args) = ParseOptions()
    Run(options)


def Run(options):
    if not utils.is_bot() and not options.dry_run:
        raise Exception('You are not a bot, don\'t archive builds. ' +
                        'Use --dry-run to test locally')
    if (options.dry_run_output and
        (not os.path.exists(options.dry_run_output) or
         not os.path.isdir(options.dry_run_output))):
        raise Exception(options.dry_run_output +
                        ' does not exist or is not a directory')
    if (options.skip_gradle_build and not options.dry_run):
        raise Exception(
            'Using --skip-gradle-build only supported with --dry-run')

    if utils.is_bot() and not utils.IsWindows():
        SetRLimitToMax()
    if not utils.IsWindows():
        PrintResourceInfo()

    with utils.TempDir() as temp:
        version_file = os.path.join(temp, 'r8-version.properties')
        with open(version_file, 'w') as version_writer:
            version_writer.write('version.sha=' + GetGitHash() + '\n')
            if not os.environ.get('SWARMING_BOT_ID') and not options.dry_run:
                raise Exception('Environment variable SWARMING_BOT_ID not set')

            releaser = \
                ("<local developer build>" if options.dry_run
                  else 'releaser=go/r8bot ('
                      + (os.environ.get('SWARMING_BOT_ID') or 'foo') + ')\n')
            version_writer.write(releaser)
            version_writer.write('version-file.version.code=1\n')

        create_maven_release.generate_r8_maven_zip(
            utils.MAVEN_ZIP_LIB,
            version_file=version_file,
            skip_gradle_build=options.skip_gradle_build)

        # Ensure all archived artifacts has been built before archiving.
        # The target tasks postfixed by 'lib' depend on the actual target task so
        # building it invokes the original task first.
        # The '-Pno_internal' flag is important because we generate the lib based on uses in tests.
        if (not options.skip_gradle_build):
            gradle.RunGradle([
                utils.GRADLE_TASK_CONSOLIDATED_LICENSE,
                utils.GRADLE_TASK_KEEP_ANNO_JAR, utils.GRADLE_TASK_R8,
                utils.GRADLE_TASK_R8LIB, utils.GRADLE_TASK_R8LIB_NO_DEPS,
                utils.GRADLE_TASK_THREADING_MODULE_BLOCKING,
                utils.GRADLE_TASK_THREADING_MODULE_SINGLE_THREADED,
                utils.GRADLE_TASK_SOURCE_JAR,
                utils.GRADLE_TASK_SWISS_ARMY_KNIFE, '-Pno_internal'
            ])

        # Create maven release of the desuage_jdk_libs configuration. This require
        # an r8.jar with dependencies to have been built.
        create_maven_release.generate_desugar_configuration_maven_zip(
            utils.DESUGAR_CONFIGURATION_MAVEN_ZIP, utils.DESUGAR_CONFIGURATION,
            utils.DESUGAR_IMPLEMENTATION,
            utils.LIBRARY_DESUGAR_CONVERSIONS_LEGACY_ZIP)
        create_maven_release.generate_desugar_configuration_maven_zip(
            utils.DESUGAR_CONFIGURATION_JDK11_LEGACY_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11_LEGACY,
            utils.DESUGAR_IMPLEMENTATION_JDK11,
            utils.LIBRARY_DESUGAR_CONVERSIONS_LEGACY_ZIP)

        create_maven_release.generate_desugar_configuration_maven_zip(
            utils.DESUGAR_CONFIGURATION_JDK11_MINIMAL_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11_MINIMAL,
            utils.DESUGAR_IMPLEMENTATION_JDK11,
            utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP)
        create_maven_release.generate_desugar_configuration_maven_zip(
            utils.DESUGAR_CONFIGURATION_JDK11_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11,
            utils.DESUGAR_IMPLEMENTATION_JDK11,
            utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP)
        create_maven_release.generate_desugar_configuration_maven_zip(
            utils.DESUGAR_CONFIGURATION_JDK11_NIO_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11_NIO,
            utils.DESUGAR_IMPLEMENTATION_JDK11,
            utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP)

        version = GetVersion()
        is_main = IsMain(version)
        if is_main:
            # On main we use the git hash to archive with
            print('On main, using git hash for archiving')
            version = GetGitHash()

        destination = GetVersionDestination('gs://', version, is_main)
        if utils.cloud_storage_exists(destination) and not options.dry_run:
            raise Exception('Target archive directory %s already exists' %
                            destination)

        # Create pom file for our maven repository that we build for testing.
        default_pom_file = os.path.join(temp, 'r8.pom')
        create_maven_release.write_default_r8_pom_file(default_pom_file,
                                                       version)
        gradle.RunGradle([
            ':main:spdxSbom',
            '-PspdxVersion=' + version,
            '-PspdxRevision=' + GetGitHash()
        ])

        for_archiving = [
            utils.R8_JAR, utils.R8LIB_JAR, utils.R8LIB_JAR + '.map',
            utils.R8LIB_JAR + '_map.zip', utils.R8_FULL_EXCLUDE_DEPS_JAR,
            utils.R8LIB_EXCLUDE_DEPS_JAR, utils.R8LIB_EXCLUDE_DEPS_JAR + '.map',
            utils.R8LIB_EXCLUDE_DEPS_JAR + '_map.zip', utils.MAVEN_ZIP_LIB,
            utils.THREADING_MODULE_BLOCKING_JAR,
            utils.THREADING_MODULE_SINGLE_THREADED_JAR,
            utils.DESUGAR_CONFIGURATION, utils.DESUGAR_CONFIGURATION_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11_LEGACY,
            utils.DESUGAR_CONFIGURATION_JDK11_LEGACY_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11_MINIMAL_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11_MAVEN_ZIP,
            utils.DESUGAR_CONFIGURATION_JDK11_NIO_MAVEN_ZIP, utils.R8_SRC_JAR,
            utils.KEEPANNO_ANNOTATIONS_JAR,
            utils.GENERATED_LICENSE,
            'd8_r8/main/build/spdx/r8.spdx.json'
        ]
        for file in for_archiving:
            file_name = os.path.basename(file)
            tagged_jar = os.path.join(temp, file_name)
            shutil.copyfile(file, tagged_jar)
            if file_name.endswith(
                    '.jar') and not file_name.endswith('-src.jar'):
                with zipfile.ZipFile(tagged_jar, 'a') as zip:
                    zip.write(version_file, os.path.basename(version_file))
            destination = GetUploadDestination(version, file_name, is_main)
            print('Uploading %s to %s' % (tagged_jar, destination))
            if options.dry_run:
                if options.dry_run_output:
                    dry_run_destination = os.path.join(options.dry_run_output,
                                                       file_name)
                    print('Dry run, not actually uploading. Copying to ' +
                          dry_run_destination)
                    shutil.copyfile(tagged_jar, dry_run_destination)
                else:
                    print('Dry run, not actually uploading')
            else:
                utils.upload_file_to_cloud_storage(tagged_jar, destination)
                print('File available at: %s' %
                      GetUrl(version, file_name, is_main))

            # Upload R8 to a maven compatible location.
            if file == utils.R8_JAR:
                maven_dst = GetUploadDestination(
                    utils.get_maven_path('r8', version), 'r8-%s.jar' % version,
                    is_main)
                maven_pom_dst = GetUploadDestination(
                    utils.get_maven_path('r8', version), 'r8-%s.pom' % version,
                    is_main)
                if options.dry_run:
                    print('Dry run, not actually creating maven repo for R8')
                else:
                    utils.upload_file_to_cloud_storage(tagged_jar, maven_dst)
                    utils.upload_file_to_cloud_storage(default_pom_file,
                                                       maven_pom_dst)
                    print('Maven repo root available at: %s' %
                          GetMavenUrl(is_main))

            # Upload desugar_jdk_libs configuration to a maven compatible location.
            if file == utils.DESUGAR_CONFIGURATION:
                jar_basename = 'desugar_jdk_libs_configuration.jar'
                jar_version_name = 'desugar_jdk_libs_configuration-%s.jar' % version
                maven_dst = GetUploadDestination(
                    utils.get_maven_path('desugar_jdk_libs_configuration',
                                         version), jar_version_name, is_main)

                with utils.TempDir() as tmp_dir:
                    desugar_jdk_libs_configuration_jar = os.path.join(
                        tmp_dir, jar_version_name)
                    create_maven_release.generate_jar_with_desugar_configuration(
                        utils.DESUGAR_CONFIGURATION,
                        utils.DESUGAR_IMPLEMENTATION,
                        utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP,
                        desugar_jdk_libs_configuration_jar)

                    if options.dry_run:
                        print('Dry run, not actually creating maven repo for ' +
                              'desugar configuration.')
                        if options.dry_run_output:
                            shutil.copyfile(
                                desugar_jdk_libs_configuration_jar,
                                os.path.join(options.dry_run_output,
                                             jar_version_name))
                    else:
                        utils.upload_file_to_cloud_storage(
                            desugar_jdk_libs_configuration_jar, maven_dst)
                        print('Maven repo root available at: %s' %
                              GetMavenUrl(is_main))
                        # Also archive the jar as non maven destination for Google3
                        jar_destination = GetUploadDestination(
                            version, jar_basename, is_main)
                        utils.upload_file_to_cloud_storage(
                            desugar_jdk_libs_configuration_jar, jar_destination)

            # TODO(b/237636871): Refactor this to avoid the duplication of what is above.
            # Upload desugar_jdk_libs JDK-11 legacyconfiguration to a maven compatible location.
            if file == utils.DESUGAR_CONFIGURATION_JDK11_LEGACY:
                jar_basename = 'desugar_jdk_libs_configuration.jar'
                jar_version_name = 'desugar_jdk_libs_configuration-%s-jdk11-legacy.jar' % version
                maven_dst = GetUploadDestination(
                    utils.get_maven_path('desugar_jdk_libs_configuration',
                                         version), jar_version_name, is_main)

                with utils.TempDir() as tmp_dir:
                    desugar_jdk_libs_configuration_jar = os.path.join(
                        tmp_dir, jar_version_name)
                    create_maven_release.generate_jar_with_desugar_configuration(
                        utils.DESUGAR_CONFIGURATION_JDK11_LEGACY,
                        utils.DESUGAR_IMPLEMENTATION_JDK11,
                        utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP,
                        desugar_jdk_libs_configuration_jar)

                    if options.dry_run:
                        print('Dry run, not actually creating maven repo for ' +
                              'desugar configuration.')
                        if options.dry_run_output:
                            shutil.copyfile(
                                desugar_jdk_libs_configuration_jar,
                                os.path.join(options.dry_run_output,
                                             jar_version_name))
                    else:
                        utils.upload_file_to_cloud_storage(
                            desugar_jdk_libs_configuration_jar, maven_dst)
                        print('Maven repo root available at: %s' %
                              GetMavenUrl(is_main))
                        # Also archive the jar as non maven destination for Google3
                        jar_destination = GetUploadDestination(
                            version, jar_basename, is_main)
                        utils.upload_file_to_cloud_storage(
                            desugar_jdk_libs_configuration_jar, jar_destination)


if __name__ == '__main__':
    sys.exit(Main())
