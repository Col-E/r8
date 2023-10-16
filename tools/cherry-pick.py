#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys
import utils

VERSION_FILE = 'src/main/java/com/android/tools/r8/Version.java'
VERSION_PREFIX = 'String LABEL = "'


def parse_options():
    parser = argparse.ArgumentParser(description='Release r8')
    parser.add_argument('--branch',
                        metavar=('<branch>'),
                        help='Branch to cherry-pick to')
    parser.add_argument('--current-checkout',
                        '--current_checkout',
                        default=False,
                        action='store_true',
                        help='Perform cherry picks into the current checkout')
    parser.add_argument('--no-upload',
                        '--no_upload',
                        default=False,
                        action='store_true',
                        help='Do not upload to Gerrit')
    parser.add_argument('hashes',
                        metavar='<hash>',
                        nargs='+',
                        help='Hashed to merge')

    return parser.parse_args()


def run(args):
    # Checkout the branch.
    subprocess.check_output(['git', 'checkout', args.branch])

    if (args.current_checkout):
        for i in range(len(args.hashes) + 1):
            branch = 'cherry-%d' % (i + 1)
            print('Deleting branch %s' % branch)
            subprocess.run(['git', 'branch', branch, '-D'])

    bugs = set()

    count = 1
    for hash in args.hashes:
        branch = 'cherry-%d' % count
        print('Cherry-picking %s in %s' % (hash, branch))
        if (count == 1):
            subprocess.run([
                'git', 'new-branch', branch, '--upstream',
                'origin/%s' % args.branch
            ])
        else:
            subprocess.run(['git', 'new-branch', branch, '--upstream-current'])

        subprocess.run(['git', 'cherry-pick', hash])
        confirm_and_upload(branch, args, bugs)
        count = count + 1

    branch = 'cherry-%d' % count
    subprocess.run(['git', 'new-branch', branch, '--upstream-current'])

    old_version = 'unknown'
    for line in open(VERSION_FILE, 'r'):
        index = line.find(VERSION_PREFIX)
        if index > 0:
            index += len(VERSION_PREFIX)
            subline = line[index:]
            old_version = subline[:subline.index('"')]
            break

    new_version = 'unknown'
    if old_version.find('.') > 0:
        split_version = old_version.split('.')
        new_version = '.'.join(split_version[:-1] +
                               [str(int(split_version[-1]) + 1)])
        subprocess.run([
            'sed', '-i',
            's/%s/%s/' % (old_version, new_version), VERSION_FILE
        ])
    else:
        editor = os.environ.get('VISUAL')
        if not editor:
            editor = os.environ.get('EDITOR')
        if not editor:
            editor = 'vi'
        else:
            print("Opening %s for version update with %s" %
                  (VERSION_FILE, editor))
            subprocess.run([editor, VERSION_FILE])

    message = ("Version %s\n\n" % new_version)
    for bug in sorted(bugs):
        message += 'Bug: b/%s\n' % bug

    subprocess.run(['git', 'commit', '-a', '-m', message])
    confirm_and_upload(branch, args, None)
    if (not args.current_checkout):
        while True:
            try:
                answer = input(
                    "Type 'delete' to finish and delete checkout in %s: " %
                    os.getcwd())
                if answer == 'delete':
                    break
            except KeyboardInterrupt:
                pass


def confirm_and_upload(branch, args, bugs):
    question = ('Ready to continue (cwd %s, will not upload to Gerrit)' %
                os.getcwd() if args.no_upload else
                'Ready to upload %s (cwd %s)' % (branch, os.getcwd()))

    while True:
        try:
            answer = input(question + ' [yes/abort]? ')
            if answer == 'yes':
                break
            if answer == 'abort':
                print('Aborting new branch for %s' % branch)
                sys.exit(1)
        except KeyboardInterrupt:
            pass

    # Compute the set of bug refs from the commit message after confirmation.
    # If done before a conflicting cherry-pick status will potentially include
    # references that are orthogonal to the pick.
    if bugs != None:
        commit_message = subprocess.check_output(
            ['git', 'log', '--format=%B', '-n', '1', 'HEAD'])
        commit_lines = [
            l.strip() for l in commit_message.decode('UTF-8').split('\n')
        ]
        for line in commit_lines:
            if line.startswith('Bug: '):
                normalized = line.replace('Bug: ', '').replace('b/', '')
                if len(normalized) > 0:
                    bugs.add(normalized)

    if (not args.no_upload):
        subprocess.run(['git', 'cl', 'upload', '--bypass-hooks'])


def main():
    args = parse_options()

    if (not args.current_checkout):
        with utils.TempDir() as temp:
            print("Performing cherry-picking in %s" % temp)
            subprocess.check_call(['git', 'clone', utils.REPO_SOURCE, temp])
            with utils.ChangedWorkingDirectory(temp):
                run(args)
    else:
        # Run in current directory.
        print("Performing cherry-picking in %s" % os.getcwd())
        subprocess.check_output(['git', 'fetch', 'origin'])
        run(args)


if __name__ == '__main__':
    sys.exit(main())
