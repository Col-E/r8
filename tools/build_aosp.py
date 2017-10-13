#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os.path import join
from glob import glob
from itertools import chain
from stat import S_IRWXU
import argparse
import multiprocessing
import os
import re
import sys

import gradle
import utils
import utils_aosp

J_DEFAULT = multiprocessing.cpu_count() - 2

EXIT_FAILURE = 1

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Checkout the AOSP source tree.')
  utils_aosp.add_common_arguments(parser)
  parser.add_argument('--tool',
      choices = ['d8', 'r8', 'default'],
      default = 'd8',
      help='Compiler tool to use. Defaults to d8.')
  parser.add_argument('--mmm',
      action = 'store_true',
      help='Use mmm instead of make')
  parser.add_argument('--mmma',
      action = 'store_true',
      help='Use mmma instead of make')
  parser.add_argument('--show-commands',
      action = 'store_true',
      help='Show commands executed during build.')
  parser.add_argument('-j',
      help='Projects to fetch simultaneously. ' +
      'Defaults to ' + str(J_DEFAULT) + '.',
      type=int,
      default=-1)
  parser.add_argument('target', nargs='?')
  return parser.parse_args()

def build_aosp(aosp_root, lunch, make, tool,
               concurrency, target, show_commands):
  d8_option = 'USE_D8=false'
  if tool == 'd8' or tool == 'r8' :
    d8_option = 'USE_D8=true'

  r8_option = 'USE_R8=false'
  if tool == 'r8':
    r8_option = 'USE_R8=true'

  j_option = '-j'
  if concurrency > 0:
    j_option += str(concurrency)

  command = [make, j_option]
  if show_commands:
    command.append('showcommands')
  command.extend([d8_option, r8_option])
  if target:
    command.append(target)

  print 'Building using: ' + ' '.join(command)
  utils_aosp.run_through_aosp_helper(lunch, command, aosp_root)

def Main():
  args = parse_arguments()

  make = 'm'
  if args.mmm:
    make = 'mmm'
  if args.mmma:
    make = 'mmma'
  build_aosp(args.aosp_root, args.lunch, make, args.tool,
             args.j, args.target, args.show_commands)

if __name__ == '__main__':
  sys.exit(Main())
