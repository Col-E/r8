#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

'''
Build r8lib.jar with both R8 and ProGuard and print a size comparison.

By default, inlining is disabled in both R8 and ProGuard to make
method-by-method comparison much easier. Pass --inlining to enable inlining.

By default, only shows methods where R8's DEX output is 5 or more instructions
larger than ProGuard+D8's output. Pass --threshold 0 to display all methods.
'''

import argparse
import build_r8lib
import os
import subprocess
import toolhelper
import utils


parser = argparse.ArgumentParser(description=__doc__.strip(),
                                 formatter_class=argparse.RawTextHelpFormatter)
parser.add_argument('-t', '--tmpdir',
                    help='Store auxiliary files in given directory')
parser.add_argument('-i', '--inlining', action='store_true',
                    help='Enable inlining')
parser.add_argument('--threshold')

R8_RELOCATIONS = [
  ('com.google.common', 'com.android.tools.r8.com.google.common'),
  ('com.google.gson', 'com.android.tools.r8.com.google.gson'),
  ('com.google.thirdparty', 'com.android.tools.r8.com.google.thirdparty'),
  ('joptsimple', 'com.android.tools.r8.joptsimple'),
  ('org.apache.commons', 'com.android.tools.r8.org.apache.commons'),
  ('org.objectweb.asm', 'com.android.tools.r8.org.objectweb.asm'),
  ('it.unimi.dsi.fastutil', 'com.android.tools.r8.it.unimi.dsi.fastutil'),
]


def is_output_newer(input, output):
  if not os.path.exists(output):
    return False
  return os.stat(input).st_mtime < os.stat(output).st_mtime


def check_call(args, **kwargs):
  utils.PrintCmd(args)
  return subprocess.check_call(args, **kwargs)


def main(tmpdir=None, inlining=True,
         run_jarsizecompare=True, threshold=None):
  if tmpdir is None:
    with utils.TempDir() as tmpdir:
      return main(tmpdir, inlining)

  inline_suffix = '-inline' if inlining else '-noinline'

  pg_config = utils.R8LIB_KEEP_RULES
  r8lib_jar = os.path.join(utils.LIBS, 'r8lib%s.jar' % inline_suffix)
  r8lib_map = os.path.join(utils.LIBS, 'r8lib%s-map.txt' % inline_suffix)
  r8lib_args = None
  if not inlining:
    r8lib_args = ['-Dcom.android.tools.r8.disableinlining=1']
    pg_config = os.path.join(tmpdir, 'keep-noinline.txt')
    with open(pg_config, 'w') as new_config:
      with open(utils.R8LIB_KEEP_RULES) as old_config:
        new_config.write(old_config.read().rstrip('\n') +
                         '\n-optimizations !method/inlining/*\n')

  if not is_output_newer(utils.R8_JAR, r8lib_jar):
    r8lib_memory = os.path.join(tmpdir, 'r8lib%s-memory.txt' % inline_suffix)
    build_r8lib.build_r8lib(
        output_path=r8lib_jar, output_map=r8lib_map,
        extra_args=r8lib_args, track_memory_file=r8lib_memory)

  pg_output = os.path.join(tmpdir, 'r8lib-pg%s.jar' % inline_suffix)
  pg_memory = os.path.join(tmpdir, 'r8lib-pg%s-memory.txt' % inline_suffix)
  pg_map = os.path.join(tmpdir, 'r8lib-pg%s-map.txt' % inline_suffix)
  pg_args = ['tools/track_memory.sh', pg_memory,
             'third_party/proguard/proguard6.0.2/bin/proguard.sh',
             '@' + pg_config,
             '-lib', utils.RT_JAR,
             '-injar', utils.R8_JAR,
             '-printmapping', pg_map,
             '-outjar', pg_output]
  for library_name, relocated_package in utils.R8_RELOCATIONS:
    pg_args.extend(['-dontwarn', relocated_package + '.**',
                    '-dontnote', relocated_package + '.**'])
  check_call(pg_args)
  if threshold is None:
    threshold = 5
  toolhelper.run('jarsizecompare',
                 ['--threshold', str(threshold),
                  '--lib', utils.RT_JAR,
                  '--input', 'input', utils.R8_JAR,
                  '--input', 'r8', r8lib_jar, r8lib_map,
                  '--input', 'pg', pg_output, pg_map])


if __name__ == '__main__':
  main(**vars(parser.parse_args()))
