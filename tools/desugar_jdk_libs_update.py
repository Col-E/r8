#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from datetime import datetime
import argparse
import os
from os.path import join
import re
import shutil
import subprocess
import sys

import utils


def sed(pattern, replace, path):
    with open(path, "r") as sources:
        lines = sources.readlines()
    with open(path, "w") as sources:
        for line in lines:
            sources.write(re.sub(pattern, replace, line))


def GetGitHash(checkout_dir):
    return subprocess.check_output(
        ['git', '-C', checkout_dir, 'rev-parse',
         'HEAD']).decode('utf-8').strip()


def run(args):
    with utils.TempDir() as tmp_dir:
        use_existing_checkout = args.desugar_jdk_libs_checkout != None
        checkout_dir = (args.desugar_jdk_libs_checkout if use_existing_checkout
                        else join(tmp_dir, 'desugar_jdk_libs'))
        if (not use_existing_checkout):
            subprocess.check_call([
                'git', 'clone',
                'https://github.com/google/desugar_jdk_libs.git', checkout_dir
            ])
            if (args.desugar_jdk_libs_revision):
                subprocess.check_call([
                    'git', '-C', checkout_dir, 'checkout',
                    args.desugar_jdk_libs_revision
                ])
        print("Hack to workaround b/256723819")
        os.remove(
            join(checkout_dir, "jdk11", "src", "java.base", "share", "classes",
                 "java", "time", "format",
                 "DesugarDateTimeFormatterBuilder.java"))
        print("Building desugared library")
        bazel = os.path.join(utils.BAZEL_TOOL, 'lib', 'bazel', 'bin', 'bazel')
        with utils.ChangedWorkingDirectory(checkout_dir):
            for target in [
                    ':desugar_jdk_libs_jdk11', '//jdk11/src:java_base_chm_only'
            ]:
                subprocess.check_call([
                    bazel, '--bazelrc=/dev/null', 'build',
                    '--spawn_strategy=local', '--verbose_failures', target
                ])

        openjdk_dir = join('third_party', 'openjdk')
        openjdk_subdir = 'desugar_jdk_libs_11'
        dest_dir = join(openjdk_dir, openjdk_subdir)
        src_dir = join(checkout_dir, 'bazel-bin', 'jdk11', 'src')

        metadata_files = ('LICENSE', 'README.google')
        for f in metadata_files:
            shutil.copyfile(join(dest_dir, f), join(tmp_dir, f))
        shutil.rmtree(dest_dir)
        os.remove(join(openjdk_dir, openjdk_subdir + '.tar.gz'))
        os.remove(join(openjdk_dir, openjdk_subdir + '.tar.gz.sha1'))
        os.mkdir(dest_dir)
        for s in [(join(src_dir, 'd8_java_base_selected_with_addon.jar'),
                   join(dest_dir, 'desugar_jdk_libs.jar')),
                  (join(src_dir, 'java_base_chm_only.jar'),
                   join(dest_dir, 'desugar_jdk_libs_chm_only.jar'))]:
            shutil.copyfile(s[0], s[1])
        for f in metadata_files:
            shutil.copyfile(join(tmp_dir, f), join(dest_dir, f))
        desugar_jdk_libs_hash = os.path.join(dest_dir, 'desugar_jdk_libs_hash')
        with open(desugar_jdk_libs_hash, 'w') as desugar_jdk_libs_hash_writer:
            desugar_jdk_libs_hash_writer.write(GetGitHash(checkout_dir))
        sed('^Version: [0-9a-f]{40}$', 'Version: %s' % GetGitHash(checkout_dir),
            join(dest_dir, 'README.google'))
        sed('^Date: .*$', 'Date: %s' % datetime.today().strftime('%Y-%m-%d'),
            join(dest_dir, 'README.google'))

    print('Now run')
    print('  (cd %s; upload_to_google_storage.py -a --bucket r8-deps %s)' %
          (openjdk_dir, openjdk_subdir))


def main():
    args = parse_options()
    run(args)


def parse_options():
    parser = argparse.ArgumentParser(
        description='Script for updating third_party/openjdk/desugar_jdk_libs*')
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
    args = parser.parse_args()
    return args


if __name__ == '__main__':
    sys.exit(main())
