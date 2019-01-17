#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import archive
import argparse
import subprocess
import sys
import tempfile
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
      '--map',
      help='Path to r8lib map.',
      default=utils.R8LIB_JAR + '.map')
  parser.add_argument(
      '--stacktrace',
      help='Path to stacktrace file.',
      default=None)
  return parser.parse_args()


def main():
  args = parse_arguments()
  r8lib_map_path = args.map
  hashOrVersion = args.commit_hash or args.version
  if hashOrVersion:
    download_path = archive.GetUploadDestination(
        hashOrVersion,
        'r8lib.jar.map',
        args.commit_hash is not None)
    if utils.file_exists_on_cloud_storage(download_path):
      r8lib_map_path = tempfile.NamedTemporaryFile().name
      utils.download_file_from_cloud_storage(download_path, r8lib_map_path)
    else:
      print('Could not find map file from argument: %s.' % hashOrVersion)
      return 1

  retrace_args = ['java', '-jar', utils.RETRACE_JAR, r8lib_map_path]
  if args.stacktrace:
    retrace_args.append(args.stacktrace)

  return subprocess.call(retrace_args)


if __name__ == '__main__':
  sys.exit(main())
