#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import jdk
import subprocess
import sys

import utils


def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'R8lib wrapper for retrace tool.')
  parser.add_argument(
      '-c',
      '--commit_hash',
      help='Commit hash to download r8lib map file for.',
      default=None)
  parser.add_argument(
      '--version',
      help='Version to download r8lib map file for.',
      default=None)
  parser.add_argument(
      '--tag',
      help='Tag to download r8lib map file for.',
      default=None)
  parser.add_argument(
      '--map',
      help='Path to r8lib map.',
      default=utils.R8LIB_JAR + '.map')
  parser.add_argument(
      '--no-r8lib',
      default=False,
      action='store_true',
      help='Use r8.jar and not r8lib.jar.')
  parser.add_argument(
      '--stacktrace',
      help='Path to stacktrace file.',
      default=None)
  parser.add_argument(
      '--quiet',
      default=None,
      action='store_true',
      help='Disables diagnostics printing to stdout.')
  parser.add_argument(
      '--debug-agent',
      default=None,
      action='store_true',
      help='Attach a debug-agent to the retracer java process.')
  parser.add_argument(
      '--regex',
      default=None,
      help='Sets a custom regular expression used for parsing'
  )
  parser.add_argument(
      '--verbose',
      default=None,
      action='store_true',
      help='Enables verbose retracing.')
  return parser.parse_args()


def main():
  args = parse_arguments()
  map_path = utils.find_cloud_storage_file_from_options(
      'r8lib.jar.map', args, orElse=args.map)
  return run(
      map_path,
      args.stacktrace,
      args.no_r8lib,
      quiet=args.quiet,
      debug=args.debug_agent,
      regex=args.regex,
      verbose=args.verbose)

def run(map_path, stacktrace, no_r8lib, quiet=False, debug=False, regex=None, verbose=False):
  retrace_args = [jdk.GetJavaExecutable()]

  if debug:
    retrace_args.append(
        '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005')

  retrace_args += [
    '-cp',
    utils.R8_JAR if no_r8lib else utils.R8LIB_JAR,
    'com.android.tools.r8.retrace.Retrace',
    map_path
  ]

  if regex:
    retrace_args.append('--regex')
    retrace_args.append(regex)

  if quiet:
    retrace_args.append('--quiet')

  if stacktrace:
    retrace_args.append(stacktrace)

  if verbose:
    retrace_args.append('--verbose')

  utils.PrintCmd(retrace_args, quiet=quiet)
  return subprocess.call(retrace_args)


if __name__ == '__main__':
  sys.exit(main())
