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
      choices = ['jack', 'd8', 'r8', 'default'],
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
  print "Running AOSP build in " + aosp_root

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

PROGUARD_SCRIPT = """#!/bin/sh
#
# Start-up script for ProGuard -- free class file shrinker, optimizer,
# obfuscator, and preverifier for Java bytecode.
#
# Note: when passing file names containing spaces to this script,
#       you\'ll have to add escaped quotes around them, e.g.
#       "\"/My Directory/My File.txt\""

# Account for possibly missing/basic readlink.
# POSIX conformant (dash/ksh/zsh/bash).
PROGUARD=`readlink -f "$0" 2>/dev/null`
if test "$PROGUARD" = \'\'
then
  PROGUARD=`readlink "$0" 2>/dev/null`
  if test "$PROGUARD" = \'\'
  then
    PROGUARD="$0"
  fi
fi

PROGUARD_HOME=`dirname "$PROGUARD"`/..

# BEGIN android-changed Added -Xmx2G for Mac builds
java -Xmx2G -jar "$PROGUARD_HOME/lib/proguard.jar" "$@"
# END android-changed
"""

def prepare_for_proguard(aosp_root):
  # Write the default proguard.sh script.
  proguard_script = join(aosp_root, 'external', 'proguard', 'bin', 'proguard.sh')
  with open(proguard_script, 'w') as f:
    f.write(PROGUARD_SCRIPT)

  os.chmod(proguard_script, S_IRWXU)

def prepare_for_r8(aosp_root):
  # Write the proguard.sh script invoking R8.
  compat_proguard_jar = join(
      utils.REPO_ROOT, 'build', 'libs', 'compatproguard.jar')
  proguard_script = join(aosp_root, 'external', 'proguard', 'bin', 'proguard.sh')
  with open(proguard_script, 'w') as f:
    f.write('java -jar ' + compat_proguard_jar + ' "$@" --min-api 10000')
  os.chmod(proguard_script, S_IRWXU)

def build_aosp(aosp_root, lunch, tool, concurrency):
  jack_option = 'ANDROID_COMPILE_WITH_JACK=' \
      + ('true' if tool == 'jack' else 'false')

  # DX_ALT_JAR need to be cleared if not set, for 'make' to work properly
  alt_jar_option = 'DX_ALT_JAR='
  if tool == 'd8':
    alt_jar_option += utils.COMPATDX_JAR

  if tool == 'r8':
    prepare_for_r8(aosp_root)
    # Use D8 compatdx dexer with hack for forwarding the R8 dex file.
    alt_jar_option += utils.COMPATDX_JAR
  else:
    prepare_for_proguard(aosp_root)

  j_option = '-j' + str(concurrency);
  print("-- Building Android image with 'make {} {} {}'." \
    .format(j_option, jack_option, alt_jar_option))

  utils_aosp.run_through_aosp_helper(lunch,
      ['make', j_option, jack_option, alt_jar_option], aosp_root)

def Main():
  args = parse_arguments()

  # Build the required tools.
  if args.tool == 'd8' or args.tool == 'r8':
    gradle.RunGradle(['d8', 'r8', 'compatdx', 'compatproguard'])

  setup_and_clean_dex(args.aosp_root, args.tool, args.clean_dex)

  build_aosp(args.aosp_root, args.lunch, args.tool, args.j)

if __name__ == '__main__':
  sys.exit(Main())
