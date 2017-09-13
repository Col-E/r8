#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os.path import join
from subprocess import check_call
import argparse
import os
import sys

import utils

AOSP_HELPER_SH = join(utils.REPO_ROOT, 'scripts', 'aosp_helper.sh')

DEFAULT_LUNCH = 'aosp_x86-eng'

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Checkout the AOSP source tree.')
  parser.add_argument('--aosp-root',
                      help='Root of the AOSP checkout. ' +
                           'Defaults to current working directory.',
                      default=os.getcwd())
  parser.add_argument('--lunch',
                      help='Build menu. ' +
                           'Defaults to ' + DEFAULT_LUNCH + '.',
                      default=DEFAULT_LUNCH)
  return parser.parse_args()

def emulator_aosp(aosp_root, lunch):
  check_call([AOSP_HELPER_SH, lunch,
      'emulator_fg', '-partition-size', '4096', '-wipe-data'], cwd = aosp_root)

def Main():
  args = parse_arguments()

  emulator_aosp(args.aosp_root, args.lunch)

if __name__ == '__main__':
  sys.exit(Main())
