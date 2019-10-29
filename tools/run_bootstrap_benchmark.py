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

MEMORY_XMX_LIMIT_BENCHMARK = 270

def parse_arguments(argv):
  parser = argparse.ArgumentParser(
      description = 'Run r8 bootstrap benchmarks.')
  parser.add_argument('--golem',
      help = 'Link in third party dependencies.',
      default = False,
      action = 'store_true')
  parser.add_argument('--limit-memory-runtime-test',
      help = 'Run in a specific memory limit.',
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

    run_memory_test = options.limit_memory_runtime_test

    java_args = (['-Xmx%sM' % MEMORY_XMX_LIMIT_BENCHMARK]
                 if run_memory_test else [])

    benchmark_name = "MemoryR8Pinned" if run_memory_test else "BootstrapR8"

    return_code = minify_tool.minify_tool(
      input_jar=utils.PINNED_R8_JAR,
      output_jar=r8_output,
      debug=False,
      build=False,
      track_memory_file=memory_file,
      benchmark_name=benchmark_name,
      java_args=java_args)

    if return_code != 0:
      sys.exit(return_code)

    if run_memory_test:
      # We are not tracking code-size, so return early.
      sys.exit(0)

    dex(r8_output, d8_r8_output)
    print "BootstrapR8(CodeSize):", utils.uncompressed_size(r8_output)
    print "BootstrapR8Dex(CodeSize):", utils.uncompressed_size(d8_r8_output)

    dex(utils.PINNED_PGR8_JAR, d8_pg_output)
    print "BootstrapR8PG(CodeSize):", utils.uncompressed_size(
        utils.PINNED_PGR8_JAR)
    print "BootstrapR8PGDex(CodeSize):", utils.uncompressed_size(d8_pg_output)

    r8_notreeshaking_output = os.path.join(temp, 'r8-notreeshaking.zip')
    return_code = minify_tool.minify_tool(
      input_jar=utils.PINNED_R8_JAR,
      output_jar=r8_notreeshaking_output,
      debug=False,
      build=False,
      benchmark_name="BootstrapR8NoTreeShaking",
      additional_args=["--no-tree-shaking"])
    if return_code != 0:
      sys.exit(return_code)

  sys.exit(0)
