#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os.path import join
from glob import glob
from itertools import chain
from subprocess import check_call
import argparse
import multiprocessing
import os
import sys

import gradle
import utils

AOSP_HELPER_SH = join(utils.REPO_ROOT, 'scripts', 'aosp_helper.sh')

DEFAULT_LUNCH = 'aosp_x86-eng'

J_DEFAULT = multiprocessing.cpu_count() - 2

EXIT_FAILURE = 1

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
  parser.add_argument('--tool',
                      choices = ['jack', 'd8', 'default'],
                      default = 'd8',
                      help='Compiler tool to use. Defaults to d8.')
  parser.add_argument('--clean-dex',
      action = 'store_true',
      help = 'Remove all dex files before the build. By default they'
      " are removed only if '--tool=d8' and if they are older than the D8 tool")
  parser.add_argument('-j',
                      help='Projects to fetch simultaneously. ' +
                           'Defaults to ' + str(J_DEFAULT) + '.',
                      type=int,
                      default=J_DEFAULT)
  return parser.parse_args()

def setup_and_clean_dex(aosp_root, tool, clean_dex):
  out = join(aosp_root, 'out')
  utils.makedirs_if_needed(out)

  # remove dex files older than the current d8 tool
  counter = 0
  if tool == 'd8' or clean_dex:
    if not clean_dex:
      d8jar_mtime = os.path.getmtime(utils.D8_JAR)
    dex_files = (chain.from_iterable(glob(join(x[0], '*.dex'))
      for x in os.walk(out)))
    for f in dex_files:
      if clean_dex or os.path.getmtime(f) <= d8jar_mtime:
        os.remove(f)
        counter += 1
  if counter > 0:
    print('Removed {} dex files.'.format(counter))

def build_aosp(aosp_root, lunch, tool, concurrency):
  jack_option = 'ANDROID_COMPILE_WITH_JACK=' \
      + ('true' if tool == 'jack' else 'false')

  # DX_ALT_JAR need to be cleared if not set, for 'make' to work properly
  alt_jar_option = 'DX_ALT_JAR='
  if tool == 'd8':
    alt_jar_option += utils.COMPATDX_JAR

  j_option = '-j' + str(concurrency);
  print("-- Building Android image with 'make {} {} {}'." \
    .format(j_option, jack_option, alt_jar_option))
  check_call([AOSP_HELPER_SH, lunch, 'make', j_option,
              jack_option, alt_jar_option], cwd = aosp_root)

def Main():
  args = parse_arguments()

  # Build the required tools.
  if args.tool == 'd8':
    gradle.RunGradle(['d8', 'd8logger', 'compatdx'])

  setup_and_clean_dex(args.aosp_root, args.tool, args.clean_dex)

  build_aosp(args.aosp_root, args.lunch, args.tool, args.j)

if __name__ == '__main__':
  sys.exit(Main())
