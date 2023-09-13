#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import optparse
import sys

import toolhelper
import utils

def ParseOptions(argv):
  parser = optparse.OptionParser(usage='%prog [options] -- [R8 options]')
  parser.add_option(
      '-c',
      '--commit-hash',
      '--commit_hash',
      help='Commit hash of R8 to use.',
      default=None)
  parser.add_argument(
      '--debug-agent',
      help='Enable Java debug agent and suspend compilation (default disabled)',
      default=False,
      action='store_true')
  parser.add_option(
    '--ea',
    help='Enable Java assertions when running the compiler (default disabled)',
    default=False,
    action='store_true')
  parser.add_option(
    '--lib-android',
    help='Add the android.jar for the given API level',
    default=None,
    type=int)
  parser.add_option(
    '--lib-rt',
    help='Add rt.jar from openjdk-1.8',
    default=False,
    action='store_true')
  parser.add_option(
    '--no-build', '--no_build',
    help='Do not build R8',
    default=False,
    action='store_true')
  parser.add_option(
    '--print-runtimeraw', '--print_runtimeraw',
    metavar='BENCHMARKNAME',
    help='Print the line \'<BENCHMARKNAME>(RunTimeRaw):' +
         ' <elapsed> ms\' at the end where <elapsed> is' +
         ' the elapsed time in milliseconds.')
  parser.add_option(
      '--tag',
      help='Tag of R8 to use.',
      default=None)
  parser.add_option(
      '--version',
      help='Version of R8 to use.',
      default=None)
  return parser.parse_args(argv)

def main(argv):
  (options, args) = ParseOptions(sys.argv)
  r8_args = args[1:]
  if options.lib_android:
    r8_args.extend(['--lib', utils.get_android_jar(options.lib_android)])
  if options.lib_rt:
    r8_args.extend(['--lib', utils.RT_JAR])
  time_consumer = lambda duration : print_duration(duration, options)
  return toolhelper.run(
      'r8',
      r8_args,
      build=not options.no_build,
      debug=options.ea,
      debug_agent=options.debug_agent,
      jar=utils.find_r8_jar_from_options(options),
      main='com.android.tools.r8.R8',
      time_consumer=time_consumer)

def print_duration(duration, options):
  benchmark_name = options.print_runtimeraw
  if benchmark_name:
    print('%s-Total(RunTimeRaw): %s ms' % (benchmark_name, duration))

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
