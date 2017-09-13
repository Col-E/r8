#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import sys

import utils
import utils_aosp

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Checkout the AOSP source tree.')
  utils_aosp.add_common_arguments(parser)
  return parser.parse_args()

def emulator_aosp(aosp_root, lunch):
  print "Running AOSP emulator in " + aosp_root

  utils_aosp.run_through_aosp_helper(lunch, ['emulator_fg',
      '-partition-size', '4096', '-wipe-data'], cwd = aosp_root)

def Main():
  args = parse_arguments()

  emulator_aosp(args.aosp_root, args.lunch)

if __name__ == '__main__':
  sys.exit(Main())
