#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import re
try:
    import resource
except ImportError:
    # Not a Unix system. Do what Gandalf tells you not to.
    pass
import shutil
import subprocess
import sys

import utils

ARCHIVE_BUCKET = 'r8-releases'
REPO = 'https://github.com/google/smali'
NO_DRYRUN_OUTPUT = object()


def checkout(temp):
    subprocess.check_call(['git', 'clone', REPO, temp])
    return temp


def parse_options():
    result = argparse.ArgumentParser(description='Release Smali')
    result.add_argument('--version',
                        metavar=('<version>'),
                        help='The version of smali to archive.')
    result.add_argument('--dry-run',
                        '--dry_run',
                        nargs='?',
                        help='Build only, no upload.',
                        metavar='<output directory>',
                        default=None,
                        const=NO_DRYRUN_OUTPUT)
    result.add_argument('--checkout', help='Use existing checkout.')
    return result.parse_args()


def set_rlimit_to_max():
    (soft, hard) = resource.getrlimit(resource.RLIMIT_NOFILE)
    resource.setrlimit(resource.RLIMIT_NOFILE, (hard, hard))


def Main():
    options = parse_options()
    if not utils.is_bot() and not options.dry_run:
        raise Exception('You are not a bot, don\'t archive builds. ' +
                        'Use --dry-run to test locally')
    if options.checkout and not options.dry_run:
        raise Exception('Using local checkout is only allowed with --dry-run')
    if not options.checkout and not options.version:
        raise Exception(
            'Option --version is required (when not using local checkout)')

    if utils.is_bot() and not utils.IsWindows():
        set_rlimit_to_max()

    with utils.TempDir() as temp:
        # Resolve dry run location to support relative directories.
        dry_run_output = None
        if options.dry_run and options.dry_run != NO_DRYRUN_OUTPUT:
            if not os.path.isdir(options.dry_run):
                os.mkdir(options.dry_run)
            dry_run_output = os.path.abspath(options.dry_run)

        checkout_dir = options.checkout if options.checkout else checkout(temp)
        with utils.ChangedWorkingDirectory(checkout_dir):
            if options.version:
                output = subprocess.check_output(
                    ['git', 'tag', '-l', options.version])
                if len(output) == 0:
                    raise Exception(
                        'Repository does not have a release tag for version %s'
                        % options.version)
                subprocess.check_call(['git', 'checkout', options.version])

            # Find version from `build.gradle`.
            for line in open(os.path.join('build.gradle'), 'r'):
                result = re.match(r'^version = \'(\d+)\.(\d+)\.(\d+)\'', line)
                if result:
                    break
            version = '%s.%s.%s' % (result.group(1), result.group(2),
                                    result.group(3))
            if options.version and version != options.version:
                message = 'version %s, expected version %s' % (version,
                                                               options.version)
                if (options.checkout):
                    raise Exception('Checkout %s has %s' %
                                    (options.checkout, message))
                else:
                    raise Exception('Tag % has %s' % (options.version, message))

            print('Building version: %s' % version)

            # Build release to local Maven repository.
            m2 = os.path.join(temp, 'm2')
            os.mkdir(m2)
            subprocess.check_call([
                './gradlew',
                '-Dmaven.repo.local=%s' % m2, 'release', 'test',
                'publishToMavenLocal'
            ])
            base = os.path.join('com', 'android', 'tools', 'smali')

            # Check that the local maven repository only has the single version directory in
            # each artifact directory.
            for name in [
                    'smali-util', 'smali-dexlib2', 'smali', 'smali-baksmali'
            ]:
                dirnames = next(os.walk(os.path.join(m2, base, name)),
                                (None, None, []))[1]
                if not dirnames or len(dirnames) != 1 or dirnames[0] != version:
                    raise Exception('Found unexpected directory %s in %s' %
                                    (dirnames, name))

            # Build an archive with the relevant content of the local maven repository.
            m2_filtered = os.path.join(temp, 'm2_filtered')
            shutil.copytree(
                m2,
                m2_filtered,
                ignore=shutil.ignore_patterns('maven-metadata-local.xml'))
            maven_release_archive = shutil.make_archive(
                'smali-maven-release-%s' % version, 'zip', m2_filtered, base)

            # Collect names of the fat jars.
            fat_jars = list(
                map(lambda prefix: '%s-%s-fat.jar' % (prefix, version),
                    ['smali/build/libs/smali', 'baksmali/build/libs/baksmali']))

            # Copy artifacts.
            files = [maven_release_archive]
            files.extend(fat_jars)
            if options.dry_run:
                if dry_run_output:
                    print('Dry run, not actually uploading. Copying to %s:' %
                          dry_run_output)
                    for file in files:
                        destination = os.path.join(dry_run_output,
                                                   os.path.basename(file))
                        shutil.copyfile(file, destination)
                        print("  %s" % destination)
                else:
                    print('Dry run, not actually uploading. Generated files:')
                    for file in files:
                        print("  %s" % os.path.basename(file))
            else:
                destination_prefix = 'gs://%s/smali/%s' % (ARCHIVE_BUCKET,
                                                           version)
                if utils.cloud_storage_exists(destination_prefix):
                    raise Exception(
                        'Target archive directory %s already exists' %
                        destination_prefix)
                for file in files:
                    destination = '%s/%s' % (destination_prefix,
                                             os.path.basename(file))
                    if utils.cloud_storage_exists(destination):
                        raise Exception('Target %s already exists' %
                                        destination)
                    utils.upload_file_to_cloud_storage(file, destination)
                public_url = 'https://storage.googleapis.com/%s/smali/%s' % (
                    ARCHIVE_BUCKET, version)
                print('Artifacts available at: %s' % public_url)

    print("Done!")


if __name__ == '__main__':
    sys.exit(Main())
