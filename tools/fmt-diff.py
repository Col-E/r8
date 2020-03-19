#!/usr/bin/env python
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import subprocess
import sys

import utils

from subprocess import Popen, PIPE

GOOGLE_JAVA_FORMAT_DIFF = os.path.join(
    utils.THIRD_PARTY,
    'google-java-format',
    'google-java-format-google-java-format-1.7',
    'scripts',
    'google-java-format-diff.py')

def main():
  upstream = subprocess.check_output(['git', 'cl', 'upstream']).strip()
  git_diff_process = Popen(['git', 'diff', '-U0', upstream], stdout=PIPE)
  fmt_process = Popen(
      ['python', GOOGLE_JAVA_FORMAT_DIFF, '-p1', '-i'],
      stdin=git_diff_process.stdout)
  git_diff_process.stdout.close()
  fmt_process.communicate()

if __name__ == '__main__':
  sys.exit(main())
