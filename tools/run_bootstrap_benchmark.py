#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import sys

import golem
import minify_tool
import toolhelper
import utils

PINNED_R8_JAR = os.path.join(utils.REPO_ROOT, 'third_party/r8/r8.jar')
PINNED_PGR8_JAR = os.path.join(utils.REPO_ROOT, 'third_party/r8/r8-pg6.0.1.jar')

def parse_arguments(argv):
  parser = argparse.ArgumentParser(
      description = 'Run r8 bootstrap benchmarks.')
  parser.add_argument('--golem',
      help = 'Link in third party dependencies.',
      default = False,
      action = 'store_true')
  return parser.parse_args(argv)


def dex(input, output):
  return_code = toolhelper.run(
      'd8', [
        input,
        '--output', output,
        '--lib', utils.RT_JAR,
        '--min-api', '10000',
        '--no-desugaring',
      ],
      debug=False,
      build=False)
  if return_code != 0:
    sys.exit(return_code)

if __name__ == '__main__':
  options = parse_arguments(sys.argv[1:])
  if options.golem:
    golem.link_third_party()
  with utils.TempDir() as temp:
    memory_file = os.path.join(temp, 'memory.dump')
    r8_output = os.path.join(temp, 'r8.zip')
    d8_r8_output = os.path.join(temp, 'd8r8.zip')
    d8_pg_output = os.path.join(temp, 'd8pg.zip')

    return_code = minify_tool.minify_tool(
      input_jar=PINNED_R8_JAR,
      output_jar=r8_output,
      debug=False,
      build=False,
      track_memory_file=memory_file,
      benchmark_name="BootstrapR8")
    if return_code != 0:
      sys.exit(return_code)

    dex(r8_output, d8_r8_output)
    print "BootstrapR8(CodeSize):", os.path.getsize(r8_output)
    print "BootstrapR8Dex(CodeSize):", os.path.getsize(d8_r8_output)

    dex(PINNED_PGR8_JAR, d8_pg_output)
    print "BootstrapR8PG(CodeSize):", os.path.getsize(PINNED_PGR8_JAR)
    print "BootstrapR8PGDex(CodeSize):", os.path.getsize(d8_pg_output)

  sys.exit(0)
