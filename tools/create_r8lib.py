#!/usr/bin/env python3
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import jdk
import utils

VERSION_EXTRACTOR = """
import com.android.tools.r8.Version;
public class VersionExtractor {
  public static void main(String[] args) {
    System.out.println(Version.LABEL);
  }
}
"""

def parse_options():
  parser = argparse.ArgumentParser(description='Tag R8 Versions')
  parser.add_argument(
      '--r8jar',
      required=True,
      help='The R8 jar to compile')
  parser.add_argument(
      '--output',
      required=True,
      help='The output path for the r8lib')
  parser.add_argument(
      '--pg-conf',
      action='append',
      help='Keep configuration')
  parser.add_argument(
      '--lib',
      action='append',
      help='Additional libraries (JDK 1.8 rt.jar already included)')
  return parser.parse_args()

def get_r8_version(r8jar):
  with utils.TempDir() as temp:
    name = os.path.join(temp, "VersionExtractor.java")
    fd = open(name, 'w')
    fd.write(VERSION_EXTRACTOR)
    fd.close()
    cmd = [jdk.GetJavacExecutable(), '-cp', r8jar, name]
    print(' '.join(cmd))
    cp_separator = ';' if utils.IsWindows() else ':'
    subprocess.check_call(cmd)
    output = subprocess.check_output([
      jdk.GetJavaExecutable(),
      '-cp',
      cp_separator.join([r8jar, os.path.dirname(name)]),
      'VersionExtractor'
    ]).decode('UTF-8').strip()
    if output == 'main':
      return subprocess.check_output(
        ['git', 'rev-parse', 'HEAD']).decode('UTF-8').strip()
    else:
      return output

def main():
  args = parse_options()
  if not os.path.exists(args.r8jar):
    print("Could not find jar: " + args.r8jar)
    return 1
  version = get_r8_version(args.r8jar)
  map_id_template = version
  source_file_template = 'R8_%MAP_ID_%MAP_HASH'
  # TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  cmd = [jdk.GetJavaExecutable(), '-Xmx8g', '-ea']
  cmd.extend(['-cp', 'build/libs/r8_with_deps.jar', 'com.android.tools.r8.R8'])
  cmd.append(args.r8jar)
  cmd.append('--classfile')
  cmd.extend(['--map-id-template', map_id_template])
  cmd.extend(['--source-file-template', source_file_template])
  cmd.extend(['--output', args.output])
  cmd.extend(['--pg-map-output', args.output + '.map'])
  cmd.extend(['--lib', 'third_party/openjdk/openjdk-rt-1.8/rt.jar'])
  if args.pg_conf:
    for pgconf in args.pg_conf:
      cmd.extend(['--pg-conf', pgconf])
  if args.lib:
    for lib in args.lib:
      cmd.extend(['--lib', lib])
  print(' '.join(cmd))
  subprocess.check_call(cmd)

if __name__ == '__main__':
  sys.exit(main())
