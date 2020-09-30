#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import archive
import argparse
import jdk
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
  return parser.parse_args()


def find_version_or_hash_from_tag(tag_or_hash):
  info = subprocess.check_output([
      'git',
      'show',
      tag_or_hash,
      '-s',
      '--format=oneline']).splitlines()[-1].split()
  # The info should be on the following form [hash,"Version",version]
  if len(info) == 3 and len(info[0]) == 40 and info[1] == "Version":
    return info[2]
  return None


def main():
  args = parse_arguments()
  if args.tag:
    hash_or_version = find_version_or_hash_from_tag(args.tag)
  else:
    hash_or_version = args.commit_hash or args.version
  return run(
      args.map,
      hash_or_version,
      args.stacktrace,
      args.commit_hash is not None,
      args.no_r8lib)

def run(map_path, hash_or_version, stacktrace, is_hash, no_r8lib):
  if hash_or_version:
    download_path = archive.GetUploadDestination(
        hash_or_version,
        'r8lib.jar.map',
        is_hash)
    if utils.file_exists_on_cloud_storage(download_path):
      map_path = tempfile.NamedTemporaryFile().name
      utils.download_file_from_cloud_storage(download_path, map_path)
    else:
      print('Could not find map file from argument: %s.' % hash_or_version)
      return 1

  retrace_args = [
    jdk.GetJavaExecutable(),
    '-cp',
    utils.R8_JAR if no_r8lib else utils.R8LIB_JAR,
    'com.android.tools.r8.retrace.Retrace',
    map_path
  ]

  if stacktrace:
    retrace_args.append(stacktrace)

  utils.PrintCmd(retrace_args)
  return subprocess.call(retrace_args)


if __name__ == '__main__':
  sys.exit(main())
