#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for running a command over builds back in time. This
# utilizes the prebuilt full r8 jars on cloud storage.  The script find all
# commits that exists on cloud storage in the given range.  It will then run the
# oldest and newest such commit, and gradually fill in the commits in between.

import optparse
import os
import subprocess
import sys
import time
import utils

MASTER_COMMITS = 'gs://r8-releases/raw/master'


def ParseOptions(argv):
    result = optparse.OptionParser()
    result.add_option('--cmd', help='Command to run')
    result.add_option('--top',
                      default=top_or_default(),
                      help='The most recent commit to test')
    result.add_option('--bottom', help='The oldest commit to test')
    result.add_option(
        '--dry-run',
        help='Do not download or run the command, but print the actions',
        default=False,
        action='store_true')
    result.add_option('--output',
                      default='build',
                      help='Directory where to output results')
    return result.parse_args(argv)


class GitCommit(object):

    def __init__(self, git_hash, destination_dir, destination, timestamp):
        self.git_hash = git_hash
        self.destination_dir = destination_dir
        self.destination = destination
        self.timestamp = timestamp

    def __str__(self):
        return '%s : %s (%s)' % (self.git_hash, self.destination,
                                 self.timestamp)

    def __repr__(self):
        return self.__str__()


def git_commit_from_hash(hash):
    commit_timestamp = subprocess.check_output(
        ['git', 'show', '--no-patch', '--no-notes', '--pretty=\'%ct\'',
         hash]).strip().strip('\'')
    destination_dir = '%s/%s/' % (MASTER_COMMITS, hash)
    destination = '%s%s' % (destination_dir, 'r8.jar')
    commit = GitCommit(hash, destination_dir, destination, commit_timestamp)
    return commit


def enumerate_git_commits(top, bottom):
    output = subprocess.check_output(['git', 'rev-list', '--first-parent', top])
    found_bottom = False
    commits = []
    for c in output.splitlines():
        commit_hash = c.strip()
        commits.append(git_commit_from_hash(commit_hash))
        if commit_hash == bottom:
            found_bottom = True
            break
    if not found_bottom:
        raise Exception('Bottom not found, did you not use a merge commit')
    return commits


def get_available_commits(commits):
    cloud_commits = subprocess.check_output(['gsutil.py', 'ls',
                                             MASTER_COMMITS]).splitlines()
    available_commits = []
    for commit in commits:
        if commit.destination_dir in cloud_commits:
            available_commits.append(commit)
    return available_commits


def print_commits(commits):
    for commit in commits:
        print(commit)


def permutate_range(start, end):
    diff = end - start
    assert diff >= 0
    if diff == 1:
        return [start, end]
    if diff == 0:
        return [start]
    half = end - (diff / 2)
    numbers = [half]
    first_half = permutate_range(start, half - 1)
    second_half = permutate_range(half + 1, end)
    for index in range(len(first_half)):
        numbers.append(first_half[index])
        if index < len(second_half):
            numbers.append(second_half[index])
    return numbers


def permutate(number_of_commits):
    assert number_of_commits > 0
    numbers = permutate_range(0, number_of_commits - 1)
    assert all(n in numbers for n in range(number_of_commits))
    return numbers


def pull_r8_from_cloud(commit):
    utils.download_file_from_cloud_storage(commit.destination, utils.R8_JAR)


def benchmark(commits, command, dryrun=False):
    commit_permutations = permutate(len(commits))
    count = 0
    for index in commit_permutations:
        count += 1
        print('Running commit %s out of %s' % (count, len(commits)))
        commit = commits[index]
        if not utils.cloud_storage_exists(commit.destination):
            # We may have a directory, but no r8.jar
            continue
        if not dryrun:
            pull_r8_from_cloud(commit)
        print('Running for commit: %s' % commit.git_hash)
        command(commit)


def top_or_default(top=None):
    return top if top else utils.get_HEAD_sha1()


def bottom_or_default(bottom=None):
    # TODO(ricow): if not set, search back 1000
    if not bottom:
        raise Exception('No bottom specified')
    return bottom


def run(command, top, bottom, dryrun=False):
    commits = enumerate_git_commits(top, bottom)
    available_commits = get_available_commits(commits)
    print('Running for:')
    print_commits(available_commits)
    benchmark(available_commits, command, dryrun=dryrun)


def make_cmd(options):
    return lambda commit: run_cmd(options, commit)


def run_cmd(options, commit):
    cmd = [options.cmd, commit.git_hash]
    output_path = options.output or 'build'
    time_commit = '%s_%s' % (commit.timestamp, commit.git_hash)
    time_commit_path = os.path.join(output_path, time_commit)
    print(' '.join(cmd))
    if not options.dry_run:
        if not os.path.exists(time_commit_path):
            os.makedirs(time_commit_path)
        stdout_path = os.path.join(time_commit_path, 'stdout')
        stderr_path = os.path.join(time_commit_path, 'stderr')
        with open(stdout_path, 'w') as stdout:
            with open(stderr_path, 'w') as stderr:
                process = subprocess.Popen(cmd, stdout=stdout, stderr=stderr)
                timeout = 1000
                while process.poll() is None and timeout > 0:
                    time.sleep(1)
                    timeout -= 1
                if process.poll() is None:
                    process.kill()
                    print("Task timed out")
                    stderr.write("timeout\n")
    print('Wrote outputs to: %s' % time_commit_path)


def main(argv):
    (options, args) = ParseOptions(argv)
    if not options.cmd:
        raise Exception('Please specify a command')
    top = top_or_default(options.top)
    bottom = bottom_or_default(options.bottom)
    command = make_cmd(options)
    run(command, top, bottom, dryrun=options.dry_run)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
