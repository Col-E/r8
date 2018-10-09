#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

'''
Build r8lib.jar using src/main/keep.txt and test that d8_api_usage_sample.jar
works with the minified R8.
'''

import argparse
import gradle
import os
import subprocess
import toolhelper
import utils

parser = argparse.ArgumentParser(description=__doc__.strip(),
                                 formatter_class=argparse.RawTextHelpFormatter)
parser.add_argument('-e', '--exclude_deps', action='store_true',
                    help='Create lib jar without dependencies')
parser.add_argument('-k', '--keep', default=utils.R8LIB_KEEP_RULES,
                    help='Keep rules file for lib')
parser.add_argument('-n', '--no_relocate', action='store_true',
                    help='Create lib jar without relocating libraries')
parser.add_argument('-o', '--out', default=None,
                    help='Output for built library')
parser.add_argument('-t', '--target', default='r8',
                    help='Compile target for library')

API_LEVEL = 26
DEPS_JAR = os.path.join(utils.LIBS, 'deps.jar')
SAMPLE_JAR = os.path.join(utils.REPO_ROOT, 'tests', 'd8_api_usage_sample.jar')

def build_r8lib(target, exclude_deps, no_relocate, keep_rules_path,
    output_path, **kwargs):
  # Clean the build directory to ensure no repackaging of any existing
  # lib or deps.
  gradle.RunGradle(['clean'])
  lib_args = [target]
  deps_args = ['repackageDeps']
  if exclude_deps:
    lib_args.append('-Pexclude_deps')
  if no_relocate:
    lib_args.append('-Plib_no_relocate')
    deps_args.append('-Plib_no_relocate')
  # Produce the r8lib target to be processed later.
  gradle.RunGradle(lib_args)
  target_lib = os.path.join(utils.LIBS, target + '.jar')
  temp_lib = os.path.join(utils.LIBS, target + '_to_process.jar')
  os.rename(target_lib, temp_lib)
  # Produce the dependencies needed for running r8 on lib.jar.
  gradle.RunGradle(deps_args)
  temp_deps = os.path.join(utils.LIBS, target + 'lib_deps.jar')
  os.rename(DEPS_JAR, temp_deps)
  # Produce R8 for compiling lib
  if output_path is None:
    output_path = target + 'lib.jar'
  output_map_path = os.path.splitext(output_path)[0] + '.map'
  toolhelper.run(
      'r8',
      ('--release',
       '--classfile',
       '--lib', utils.RT_JAR,
       '--lib', temp_deps,
       temp_lib,
       '--output', output_path,
       '--pg-conf', keep_rules_path,
       '--pg-map-output', output_map_path),
      **kwargs)
  if exclude_deps:
    return [output_path, temp_deps]
  else:
    return [output_path]


def test_d8sample(paths):
  with utils.TempDir() as path:
    args = ['java', '-cp', '%s:%s' % (SAMPLE_JAR, ":".join(paths)),
            'com.android.tools.apiusagesample.D8ApiUsageSample',
            '--output', path,
            '--min-api', str(API_LEVEL),
            '--lib', utils.get_android_jar(API_LEVEL),
            '--classpath', utils.R8_JAR,
            '--main-dex-list', '/dev/null',
            os.path.join(utils.BUILD, 'test/examples/hello.jar')]
    utils.PrintCmd(args)
    subprocess.check_call(args)


def test_r8command(paths):
  with utils.TempDir() as path:
    # SAMPLE_JAR and LIB_JAR should not have any classes in common, since e.g.
    # R8CommandParser should have been minified in LIB_JAR.
    # Just in case R8CommandParser is also present in LIB_JAR, we put
    # SAMPLE_JAR first on the classpath to use its version of R8CommandParser.
    args = ['java', '-cp', '%s:%s' % (SAMPLE_JAR, ":".join(paths)),
            'com.android.tools.r8.R8CommandParser',
            '--output', path + "/output.zip",
            '--min-api', str(API_LEVEL),
            '--lib', utils.get_android_jar(API_LEVEL),
            '--main-dex-list', '/dev/null',
            os.path.join(utils.BUILD, 'test/examples/hello.jar')]
    utils.PrintCmd(args)
    subprocess.check_call(args)


def test_r8cfcommand(paths):
  with utils.TempDir() as path:
    # SAMPLE_JAR and LIB_JAR should not have any classes in common, since e.g.
    # R8CommandParser should have been minified in LIB_JAR.
    # Just in case R8CommandParser is also present in LIB_JAR, we put
    # SAMPLE_JAR first on the classpath to use its version of R8CommandParser.
    args = ['java', '-cp', '%s:%s' % (SAMPLE_JAR, ":".join(paths)),
            'com.android.tools.r8.R8CommandParser',
            '--classfile',
            '--output', path + "/output.jar",
            '--lib', utils.RT_JAR,
            os.path.join(utils.BUILD, 'test/examples/hello.jar')]
    utils.PrintCmd(args)
    subprocess.check_call(args)


def main():
  # Handle --help
  args = parser.parse_args()
  output_paths = build_r8lib(
      args.target, args.exclude_deps, args.no_relocate, args.keep, args.out)
  if args.target == 'r8':
    gradle.RunGradle(['buildExampleJars'])
    test_r8command(output_paths)
    test_r8cfcommand(output_paths)
  if args.target == 'd8':
    gradle.RunGradle(['buildExampleJars'])
    test_d8sample(output_paths)


if __name__ == '__main__':
  main()
