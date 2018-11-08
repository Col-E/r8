#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Test prebuilt AOSP jar files: compile with D8 and run dex2out to validate

from __future__ import print_function
from glob import glob
from itertools import chain
from os.path import join
import argparse
import os
import subprocess
import sys

import gradle

import dex2oat
import utils

REPLAY_SCRIPT_DIR = join(utils.REPO_ROOT, 'third_party',
    'android_cts_baseline', 'dx_replay')
REPLAY_SCRIPT = join(REPLAY_SCRIPT_DIR, 'replay_script.py')
OUT_DIR = join(REPLAY_SCRIPT_DIR, 'out')

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Run D8 (CompatDX) and dex2oat on prebuilt AOSP jars.')
  parser.add_argument('--no-build', default = False, action = 'store_true')
  return parser.parse_args()

def Main():
  args = parse_arguments()

  if not args.no_build:
    gradle.RunGradle(['CompatDx'])

  cmd = [REPLAY_SCRIPT, 'java', '-jar', utils.COMPATDX_JAR]
  utils.PrintCmd(cmd)
  subprocess.check_call(cmd)

  # collect dex files below OUT_DIR
  dex_files = (chain.from_iterable(glob(join(x[0], '*.dex'))
      for x in os.walk(OUT_DIR)))

  for dex_file in dex_files:
      dex2oat.run(dex_file)


if __name__ == '__main__':
  sys.exit(Main())
