#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import sys

import gmaven
import utils

ARCHIVE_BUCKET = 'r8-releases'
REPO = 'https://github.com/google/smali'


def parse_options():
    result = argparse.ArgumentParser(description='Release Smali')
    result.add_argument('--version',
                        required=True,
                        metavar=('<version>'),
                        help='The version of smali to release.')
    result.add_argument(
        '--dry-run',
        default=False,
        action='store_true',
        help='Only perform non-commiting tasks and print others.')
    return result.parse_args()


def Main():
    options = parse_options()
    utils.check_gcert()
    gfile = ('/bigstore/r8-releases/smali/%s/smali-maven-release-%s.zip' %
             (options.version, options.version))
    release_id = gmaven.publisher_stage([gfile], options.dry_run)

    print('Staged Release ID %s.\n' % release_id)
    gmaven.publisher_stage_redir_test_info(
        release_id, 'com.android.tools.smali:smali:%s' % options.version,
        'smali.jar')

    print()
    answer = input('Continue with publishing [y/N]:')

    if answer != 'y':
        print('Aborting release to Google maven')
        sys.exit(1)

    gmaven.publisher_publish(release_id, options.dry_run)

    print()
    print('Published. Use the email workflow for approval.')


if __name__ == '__main__':
    sys.exit(Main())
