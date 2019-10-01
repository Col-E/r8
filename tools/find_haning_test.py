#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import sys
import urllib

def ParseOptions():
  parser = argparse.ArgumentParser(
      description = 'Find tests started but not done from bot stdout.')
  return parser.parse_known_args()

def get_started(stdout):
  # Lines look like:
  # Start executing test runBigInteger_ZERO_A01 [com.android.tools.r8.jctf.r8cf.math.BigInteger.ZERO.BigInteger_ZERO_A01]
  start_lines = []
  for line in stdout:
    if line.startswith('Start executing test'):
      split = line.split(' ')
      start_lines.append('%s %s' % (split[3], split[4].strip()))
  return start_lines

def get_ended(stdout):
  # Lines look like:
  # Done executing test runBigInteger_subtract_A01 [com.android.tools.r8.jctf.r8cf.math.BigInteger.subtractLjava_math_BigInteger.BigInteger_subtract_A01] with result: SUCCESS
  done_lines = []
  for line in stdout:
    if line.startswith('Done executing test'):
      split = line.split(' ')
      done_lines.append('%s %s' % (split[3], split[4].strip()))
  return done_lines

def Main():
  (options, args) = ParseOptions()
  if len(args) != 1:
    raise "fail"

  with open(args[0], 'r') as f:
    lines = f.readlines()
  started = get_started(lines)
  ended = get_ended(lines)
  for test in started:
    if not test in ended:
      print 'Test %s started but did not end' % test


if __name__ == '__main__':
  sys.exit(Main())
