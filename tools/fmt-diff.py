#!/usr/bin/env python3
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import utils

from subprocess import Popen, PIPE

GOOGLE_JAVA_FORMAT_DIFF = os.path.join(utils.THIRD_PARTY, 'google',
                                       'google-java-format', '1.14.0',
                                       'google-java-format-1.14.0', 'scripts',
                                       'google-java-format-diff.py')

GOOGLE_YAPF = os.path.join(utils.THIRD_PARTY, 'google/yapf/20231013')


def ParseOptions():
    result = argparse.ArgumentParser()
    result.add_argument('--no-java',
                        help='Run google-java-format.',
                        action='store_true',
                        default=False)
    result.add_argument('--python',
                        help='Run YAPF.',
                        action='store_true',
                        default=False)
    return result.parse_known_args()


def FormatJava(upstream):
    git_diff_process = Popen(['git', 'diff', '-U0', upstream], stdout=PIPE)
    fmt_process = Popen([sys.executable, GOOGLE_JAVA_FORMAT_DIFF, '-p1', '-i'],
                        stdin=git_diff_process.stdout)
    git_diff_process.stdout.close()
    fmt_process.communicate()


def FormatPython(upstream):
    changed_files_cmd = ['git', 'diff', '--name-only', upstream, '*.py']
    changed_files = subprocess.check_output(changed_files_cmd).decode(
        'utf-8').splitlines()
    if not changed_files:
        return
    format_cmd = [
        sys.executable,
        os.path.join(GOOGLE_YAPF, 'yapf'), '--in-place', '--style', 'google'
    ]
    format_cmd.extend(changed_files)
    yapf_python_path = [GOOGLE_YAPF, os.path.join(GOOGLE_YAPF, 'third_party')]
    subprocess.check_call(format_cmd,
                          env={'PYTHONPATH': ':'.join(yapf_python_path)})


def main():
    (options, args) = ParseOptions()
    upstream = subprocess.check_output(['git', 'cl',
                                        'upstream']).decode('utf-8').strip()
    if not options.no_java:
        FormatJava(upstream)
    if options.python:
        FormatPython(upstream)


if __name__ == '__main__':
    sys.exit(main())
