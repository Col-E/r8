#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import gradle
import os
from shutil import copyfile
import sys
import utils
import archive

JAR_TARGETS_MAP = {
    'full': [(utils.R8, 'r8'),],
    'lib': [(utils.R8LIB, 'r8'),],
}

OTHER_TARGETS = ['LICENSE']
SBOM_TARGETS = ['r8.spdx.json']
KEEPANNO_JAR = 'keepanno-annotations.jar'


def parse_arguments():
    parser = argparse.ArgumentParser(
        description='Build and copy jars to an Android tree.')
    parser.add_argument('android_root', nargs=1, help='Android checkout root.')
    parser.add_argument('--commit_hash', default=None, help='Commit hash')
    parser.add_argument('--version',
                        default=None,
                        help='The version to download')
    parser.add_argument(
        '--targets',
        required=True,
        choices=['full', 'lib'],
        help="Use 'full' to download the full, non-optimized jars (legacy" +
        " behaviour) and 'lib' for the R8-processed, optimized jars.",
    )
    parser.add_argument(
        '--maps',
        action='store_true',
        help="Download proguard maps for jars, use only with '--target lib'.",
    )
    parser.add_argument(
        '--keepanno',
        action='store_true',
        help="Download keepanno-annotations library.",
    )
    parser.add_argument(
        '--java-max-memory-size',
        '--java_max_memory_size',
        help='Use a custom max memory size for the gradle java instance, eg, 4g'
    )
    return parser.parse_args()


def copy_targets(root, target_root, srcs, dests, maps=False):
    assert len(srcs) == len(dests)
    for i in range(len(srcs)):
        src = os.path.join(root, srcs[i])
        dest = os.path.join(target_root, 'prebuilts', 'r8', dests[i])
        if os.path.exists(dest):
            print('Copying: ' + src + ' -> ' + dest)
            copyfile(src, dest)
            if maps:
                print('Copying: ' + src + '.map -> ' + dest + '.map')
                copyfile(src + '.map', dest + '.map')
        else:
            print('WARNING: Not copying ' + src + ' -> ' + dest + ', as ' +
                  dest + ' does not exist already')


def copy_jar_targets(root, target_root, jar_targets, maps):
    srcs = list(map((lambda t: t[0] + '.jar'), jar_targets))
    dests = list(map((lambda t: t[1] + '.jar'), jar_targets))
    copy_targets(root, target_root, srcs, dests, maps=maps)


def copy_other_targets(root, target_root, other_targets):
    copy_targets(root, target_root, other_targets, other_targets)


def download_hash(root, commit_hash, target, quiet=False):
    download_target(root, target, commit_hash, True, quiet=quiet)


def download_version(root, version, target):
    download_target(root, target, version, False)


def download_target(root, target, hash_or_version, is_hash, quiet=False):
    download_path = os.path.join(root, target)
    url = archive.GetUploadDestination(hash_or_version, target, is_hash)
    if not quiet:
        print('Downloading: ' + url + ' -> ' + download_path)
    utils.download_file_from_cloud_storage(url, download_path, quiet=quiet)


def main_download(hash, maps, targets, target_root, version, keepanno=False):
    sbom_targets = []
    if version:
        semver = utils.check_basic_semver_version(version, allowPrerelease=True)
        # Generation of SBOM started from version 8.3.13-dev.
        if semver.larger_than(utils.SemanticVersion(8, 3, 12, 'dev')):
            sbom_targets = SBOM_TARGETS

    jar_targets = JAR_TARGETS_MAP[targets]
    final_targets = list(map(
        (lambda t: t[0] + '.jar'), jar_targets)) + OTHER_TARGETS + sbom_targets
    with utils.TempDir() as root:
        for target in final_targets:
            if hash:
                download_hash(root, hash, target)
                if maps and target not in OTHER_TARGETS:
                    download_hash(root, hash, target + '.map')
                if keepanno:
                    download_hash(root, hash, KEEPANNO_JAR)
            else:
                assert version
                download_version(root, version, target)
                if maps and target not in (OTHER_TARGETS + sbom_targets):
                    download_version(root, version, target + '.map')
                if keepanno:
                    download_version(root, version, KEEPANNO_JAR)
        copy_jar_targets(root, target_root, jar_targets, maps)
        copy_other_targets(root, target_root, OTHER_TARGETS + sbom_targets)
        if keepanno:
            copy_targets(root, target_root, [KEEPANNO_JAR], [KEEPANNO_JAR])


def main_build(maps, max_memory_size, targets, target_root):
    jar_targets = JAR_TARGETS_MAP[targets]
    gradle_args = [
        utils.GRADLE_TASK_R8LIB if targets == 'lib' else utils.GRADLE_TASK_R8
    ]
    if max_memory_size:
        gradle_args.append('-Dorg.gradle.jvmargs=-Xmx' + max_memory_size)
    gradle.RunGradle(gradle_args)
    copy_jar_targets(utils.LIBS, target_root, jar_targets, maps)
    copy_other_targets(utils.GENERATED_LICENSE_DIR, target_root, OTHER_TARGETS)


def main(args):
    if args.maps and args.targets != 'lib':
        raise Exception("Use '--maps' only with '--targets lib.")
    target_root = args.android_root[0]
    if args.commit_hash == None and args.version == None:
        main_build(args.maps, args.java_max_memory_size, args.targets,
                   target_root)
    else:
        assert args.commit_hash == None or args.version == None
        main_download(args.commit_hash, args.maps, args.targets, target_root,
                      args.version, args.keepanno)


if __name__ == '__main__':
    sys.exit(main(parse_arguments()))
