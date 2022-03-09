#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import gradle
import jdk
import utils

NONLIB_BUILD_TARGET = 'R8WithRelocatedDeps'
NONLIB_TEST_BUILD_TARGETS = [utils.R8_TESTS_TARGET, utils.R8_TESTS_DEPS_TARGET]

R8LIB_BUILD_TARGET = utils.R8LIB
R8LIB_TEST_BUILD_TARGETS = [utils.R8LIB_TESTS_TARGET, utils.R8LIB_TESTS_DEPS_TARGET]

# The r8lib target is always the golem target.
GOLEM_BUILD_TARGETS = [R8LIB_BUILD_TARGET] + R8LIB_TEST_BUILD_TARGETS

def get_golem_resource_path(benchmark):
  return os.path.join('benchmarks', benchmark)

def get_jdk_home(options, benchmark):
  if options.golem:
    return os.path.join(get_golem_resource_path(benchmark), 'linux')
  return None

def parse_options(argv):
  result = argparse.ArgumentParser(description = 'Run test-based benchmarks.')
  result.add_argument('--golem',
                      help='Indicate this as a run on golem',
                      default=False,
                      action='store_true')
  result.add_argument('--benchmark',
                      help='The test benchmark to run',
                      required=True)
  result.add_argument('--target',
                      help='The test target to run',
                      required=True,
                      # These should 1:1 with benchmarks/BenchmarkTarget.java
                      choices=['d8', 'r8-full', 'r8-force', 'r8-compat'])
  result.add_argument('--nolib', '--no-lib', '--no-r8lib',
                      help='Run the non-lib R8 build (default false)',
                      default=False,
                      action='store_true')
  result.add_argument('--no-build', '--no_build',
                      help='Run without building first (default false)',
                      default=False,
                      action='store_true')
  result.add_argument('--enable-assertions', '--enable_assertions', '-ea',
                      help='Enable assertions when running',
                      default=False,
                      action='store_true')
  result.add_argument('--print-times',
                      help='Print timing information from r8',
                      default=False,
                      action='store_true')
  result.add_argument('--temp',
                      help='A directory to use for temporaries and outputs.',
                      default=None)
  return result.parse_known_args(argv)

def main(argv):
  (options, args) = parse_options(argv)

  if options.golem:
    options.no_build = True
    if options.nolib:
      print("Error: golem should always run r8lib")
      return 1

  if options.nolib:
    buildTargets = [NONLIB_BUILD_TARGET] + NONLIB_TEST_BUILD_TARGETS
    r8jar = utils.R8_WITH_RELOCATED_DEPS_JAR
    testjars = [utils.R8_TESTS_DEPS_JAR, utils.R8_TESTS_JAR]
  else:
    buildTargets = GOLEM_BUILD_TARGETS
    r8jar = utils.R8LIB_JAR
    testjars = [utils.R8LIB_TESTS_DEPS_JAR, utils.R8LIB_TESTS_JAR]

  if not options.no_build:
    gradle.RunGradle(buildTargets + ['-Pno_internal'])

  return run(options, r8jar, testjars)

def run(options, r8jar, testjars):
  jdkhome = get_jdk_home(options, options.benchmark)
  cmd = [jdk.GetJavaExecutable(jdkhome)]
  if options.enable_assertions:
    cmd.append('-ea')
  if options.print_times:
    cmd.append('-Dcom.android.tools.r8.printtimes=1')
  cmd.extend(['-cp', ':'.join([r8jar] + testjars)])
  cmd.extend([
    'com.android.tools.r8.benchmarks.BenchmarkMainEntryRunner',
    options.benchmark,
    options.target,
    'golem' if options.golem else 'local',
    ])
  return subprocess.check_call(cmd)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
