#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import gradle
import os
import subprocess
import sys
import utils

RETRACE_JAR = os.path.join(
  utils.THIRD_PARTY,
  'proguard',
  'proguard6.0.1',
  'lib',
  'retrace.jar')

EXCEPTION_LINE = 'Intentional exception for testing retrace.'
EXPECTED_LINES = [
  'com.android.tools.r8.utils.SelfRetraceTest.foo3(SelfRetraceTest.java:13)',
  'com.android.tools.r8.utils.SelfRetraceTest.foo2(SelfRetraceTest.java:17)',
  'com.android.tools.r8.utils.SelfRetraceTest.foo1(SelfRetraceTest.java:21)',
  'com.android.tools.r8.utils.SelfRetraceTest.test(SelfRetraceTest.java:26)',
  'com.android.tools.r8.R8.run(R8.java:',
]

def main():
  args = sys.argv[1:]
  if len(args) == 0:
    gradle.RunGradle(['r8lib'])
    r8lib = utils.R8LIB_JAR
  elif len(args) == 1:
    if args[0] == '--help':
      print('Usage: test_self_retrace.py [<path-to-r8lib-jar>]')
      print('If the path is missing the script builds and uses ' + utils.R8LIB_JAR)
      return
    else:
      r8lib = args[0]
  else:
    raise Exception("Only one argument is allowed, see '--help'.")

  # Run 'r8 --help' which throws an exception.
  cmd = ['java','-cp', r8lib, 'com.android.tools.r8.R8', '--help']
  os.environ["R8_THROW_EXCEPTION_FOR_TESTING_RETRACE"] = "1"
  utils.PrintCmd(cmd)
  p = subprocess.Popen(cmd, stderr=subprocess.PIPE)
  _, stacktrace = p.communicate()
  assert(p.returncode != 0)
  assert(EXCEPTION_LINE in stacktrace)
  # r8lib must be minified, original class names must not be present.
  assert('SelfRetraceTest' not in stacktrace)

  # Run the retrace tool.
  cmd = ['java', '-jar', RETRACE_JAR, r8lib + ".map"]
  utils.PrintCmd(cmd)
  p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
  retrace_stdout, _ = p.communicate(stacktrace)
  assert p.returncode == 0
  retrace_lines = retrace_stdout.splitlines()
  line_index = -1
  for line in retrace_lines:
    if line_index < 0:
      if 'java.lang.RuntimeException' in line:
        assert(EXCEPTION_LINE in line)
        line_index = 0;
    else:
      assert EXPECTED_LINES[line_index] in line
      line_index += 1
      if line_index >= len(EXPECTED_LINES):
         break
  assert(line_index >= 0)

if __name__ == '__main__':
  sys.exit(main())
