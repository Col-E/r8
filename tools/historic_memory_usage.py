#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for running run_on_app.py finding minimum memory need for
# compiling a given app back in time. This utilizes the prebuilt r8 jars on
# cloud storage.
# The script find all commits that exists on cloud storage in the given range.
# It will then run the oldest and newest such commit, and gradually fill in
# the commits in between.

import historic_run
import optparse
import os
import subprocess
import sys
import utils

APPS = ['gmscore', 'nest', 'youtube', 'gmail', 'chrome']
COMPILERS = ['d8', 'r8']


def ParseOptions(argv):
    result = optparse.OptionParser()
    result.add_option('--compiler',
                      help='The compiler to use',
                      default='d8',
                      choices=COMPILERS)
    result.add_option('--app',
                      help='What app to run on',
                      default='gmail',
                      choices=APPS)
    result.add_option('--top',
                      default=historic_run.top_or_default(),
                      help='The most recent commit to test')
    result.add_option('--bottom', help='The oldest commit to test')
    result.add_option('--output',
                      default='build',
                      help='Directory where to output results')
    result.add_option('--timeout',
                      type=int,
                      default=0,
                      help='Set timeout instead of waiting for OOM.')
    return result.parse_args(argv)


def make_run_on_app_command(options):
    return lambda commit: run_on_app(options, commit)


def run_on_app(options, commit):
    app = options.app
    compiler = options.compiler
    cmd = [
        'tools/run_on_app.py', '--app', app, '--compiler', compiler,
        '--timeout',
        str(options.timeout), '--no-build', '--find-min-xmx'
    ]
    stdout = subprocess.check_output(cmd)
    output_path = options.output or 'build'
    time_commit = '%s_%s' % (commit.timestamp, commit.git_hash)
    time_commit_path = os.path.join(output_path, time_commit)
    if not os.path.exists(time_commit_path):
        os.makedirs(time_commit_path)
    stdout_path = os.path.join(time_commit_path, 'stdout')
    with open(stdout_path, 'w') as f:
        f.write(stdout)
    print('Wrote stdout to: %s' % stdout_path)


def main(argv):
    (options, args) = ParseOptions(argv)
    if not options.app:
        raise Exception('Please specify an app')
    top = historic_run.top_or_default(options.top)
    bottom = historic_run.bottom_or_default(options.bottom)
    command = make_run_on_app_command(options)
    historic_run.run(command, top, bottom)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
