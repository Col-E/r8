#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script that automatically pulls and uploads all upstream direct and indirect
# branches into the current branch.
#
# Example:
#
#   $ git branch -vv
#   * feature_final    xxxxxxxxx [feature_prereq_c: ...] ...
#     feature_prereq_c xxxxxxxxx [feature_prereq_b: ...] ...
#     feature_prereq_b xxxxxxxxx [feature_prereq_a: ...] ...
#     feature_prereq_a xxxxxxxxx [main: ...] ...
#     main             xxxxxxxxx [origin/main] ...
#
# Executing `git_sync_cl_chain.py -m <message>` causes the following chain of
# commands to be executed:
#
#   $ git checkout feature_prereq_a; git pull; git cl upload -m <message>
#   $ git checkout feature_prereq_b; git pull; git cl upload -m <message>
#   $ git checkout feature_prereq_c; git pull; git cl upload -m <message>
#   $ git checkout feature_final;    git pull; git cl upload -m <message>

import optparse
import os
import sys

import defines
import utils

REPO_ROOT = defines.REPO_ROOT


class Repo(object):

    def __init__(self, name, is_current, upstream):
        self.name = name
        self.is_current = is_current
        self.upstream = upstream


def ParseOptions(argv):
    result = optparse.OptionParser()
    result.add_option('--bypass-hooks',
                      help='Bypass presubmit hooks',
                      action='store_true')
    result.add_option('--delete',
                      '-d',
                      help='Delete closed branches',
                      choices=['y', 'n', 'ask'],
                      default='ask')
    result.add_option('--from_branch',
                      '-f',
                      help='Uppermost upstream to sync from',
                      default='main')
    result.add_option(
        '--leave_upstream',
        '--leave-upstream',
        help='To not update the upstream of the first open branch',
        action='store_true')
    result.add_option('--message',
                      '-m',
                      help='Message for patchset',
                      default='Sync')
    result.add_option('--rebase',
                      help='To use `git pull --rebase` instead of `git pull`',
                      action='store_true')
    result.add_option('--no_upload',
                      '--no-upload',
                      help='Disable uploading to Gerrit',
                      action='store_true')
    result.add_option('--skip_main',
                      '--skip-main',
                      help='Disable syncing for main',
                      action='store_true')
    (options, args) = result.parse_args(argv)
    options.upload = not options.no_upload
    assert options.delete != 'y' or not options.leave_upstream, (
        'Inconsistent options: cannot leave the upstream of the first open ' +
        'branch (--leave_upstream) and delete the closed branches at the same '
        + 'time (--delete).')
    assert options.message, 'A message for the patchset is required.'
    assert len(args) == 0
    return options


def main(argv):
    options = ParseOptions(argv)
    with utils.ChangedWorkingDirectory(REPO_ROOT, quiet=True):
        branches = [
            parse(line)
            for line in utils.RunCmd(['git', 'branch', '-vv'], quiet=True)
        ]

        current_branch = None
        for branch in branches:
            if branch.is_current:
                current_branch = branch
                break
        assert current_branch is not None

        if is_root_branch(current_branch, options):
            print('Nothing to sync')
            return

        stack = []
        while current_branch:
            stack.append(current_branch)
            if is_root_branch(current_branch, options):
                break
            current_branch = get_branch_with_name(current_branch.upstream,
                                                  branches)

        closed_branches = []
        has_seen_local_branch = False  # A branch that is not uploaded.
        has_seen_open_branch = False  # A branch that is not closed.
        while len(stack) > 0:
            branch = stack.pop()

            utils.RunCmd(['git', 'checkout', branch.name], quiet=True)

            status = get_status_for_current_branch(branch)
            print('Syncing %s (status: %s)' % (branch.name, status))

            pull_for_current_branch(branch, options)

            if branch.name == 'main':
                continue

            if status == 'closed':
                assert not has_seen_local_branch, (
                    'Unexpected closed branch %s after new branch' %
                    branch.name)
                assert not has_seen_open_branch, (
                    'Unexpected closed branch %s after open branch' %
                    branch.name)
                closed_branches.append(branch.name)
                continue

            if not options.leave_upstream:
                if not has_seen_open_branch and len(closed_branches) > 0:
                    print('Setting upstream for first open branch %s to main' %
                          branch.name)
                    set_upstream_for_current_branch_to_main()

            has_seen_open_branch = True
            has_seen_local_branch = has_seen_local_branch or (status == 'None')

            if options.upload and status != 'closed':
                if has_seen_local_branch:
                    print(
                        'Cannot upload branch %s since it comes after a local branch'
                        % branch.name)
                else:
                    upload_cmd = ['git', 'cl', 'upload', '-m', options.message]
                    if options.bypass_hooks:
                        upload_cmd.append('--bypass-hooks')
                    utils.RunCmd(upload_cmd, quiet=True)

        if get_delete_branches_option(closed_branches, options):
            delete_branches(closed_branches)

        utils.RunCmd(['git', 'cl', 'issue'])


def delete_branches(branches):
    assert len(branches) > 0
    cmd = ['git', 'branch', '-D']
    cmd.extend(branches)
    utils.RunCmd(cmd, quiet=True)


def get_branch_with_name(name, branches):
    for branch in branches:
        if branch.name == name:
            return branch
    return None


def get_delete_branches_option(closed_branches, options):
    if len(closed_branches) == 0:
        return False
    if options.leave_upstream:
        return False
    if options.delete == 'y':
        return True
    if options.delete == 'n':
        return False
    assert options.delete == 'ask'
    print('Delete closed branches: %s (Y/N)?' % ", ".join(closed_branches))
    answer = sys.stdin.read(1)
    return answer.lower() == 'y'


def get_status_for_current_branch(current_branch):
    if current_branch.name == 'main':
        return 'main'
    return utils.RunCmd(['git', 'cl', 'status', '--field', 'status'],
                        quiet=True)[0].strip()


def is_root_branch(branch, options):
    return branch.name == options.from_branch or branch.upstream is None


def pull_for_current_branch(branch, options):
    if branch.name == 'main' and options.skip_main:
        return
    rebase_args = ['--rebase'] if options.rebase else []
    utils.RunCmd(['git', 'pull'] + rebase_args, quiet=True)


def set_upstream_for_current_branch_to_main():
    utils.RunCmd(['git', 'cl', 'upstream', 'main'], quiet=True)


# Parses a line from the output of `git branch -vv`.
#
# Example output ('*' denotes the current branch):
#
#   $ git branch -vv
#   * feature_final    xxxxxxxxx [feature_prereq_c: ...] ...
#     feature_prereq_c xxxxxxxxx [feature_prereq_b: ...] ...
#     feature_prereq_b xxxxxxxxx [feature_prereq_a: ...] ...
#     feature_prereq_a xxxxxxxxx [main: ...] ...
#     main             xxxxxxxxx [origin/main] ...
def parse(line):
    is_current = False
    if line.startswith('*'):
        is_current = True
        line = line[1:].lstrip()
    else:
        line = line.lstrip()

    name_end_index = line.index(' ')
    name = line[:name_end_index]
    line = line[name_end_index:].lstrip()

    if '[' in line:
        line = line[line.index('[') + 1:]

        if ':' in line:
            upstream = line[:line.index(':')]
            return Repo(name, is_current, upstream)

        if ']' in line:
            upstream = line[:line.index(']')]
            return Repo(name, is_current, upstream)

    return Repo(name, is_current, None)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
