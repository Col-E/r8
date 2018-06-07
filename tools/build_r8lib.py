#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

'''
Build r8lib.jar using src/main/keep.txt and test that d8_api_usage_sample.jar
works with the minified R8.
'''

import argparse
import os
import subprocess
import toolhelper
import utils

parser = argparse.ArgumentParser(description=__doc__.strip(),
                                 formatter_class=argparse.RawTextHelpFormatter)

SAMPLE_JAR = os.path.join(utils.REPO_ROOT, 'tests/d8_api_usage_sample.jar')
R8LIB_JAR = os.path.join(utils.LIBS, 'r8lib.jar')
R8LIB_MAP_FILE = os.path.join(utils.LIBS, 'r8lib-map.txt')

API_LEVEL = 26
ANDROID_JAR = 'third_party/android_jar/lib-v%s/android.jar' % API_LEVEL


def build_r8lib(output_path=None, output_map=None, **kwargs):
  if output_path is None:
    output_path = R8LIB_JAR
  if output_map is None:
    output_map = R8LIB_MAP_FILE
  toolhelper.run(
      'r8',
      ('--release',
       '--classfile',
       '--lib', utils.RT_JAR,
       utils.R8_JAR,
       '--output', output_path,
       '--pg-conf', utils.R8LIB_KEEP_RULES,
       '--pg-map-output', output_map),
      **kwargs)


def test_d8sample():
  with utils.TempDir() as path:
    args = ['java', '-cp', '%s:%s' % (SAMPLE_JAR, R8LIB_JAR),
            'com.android.tools.apiusagesample.D8ApiUsageSample',
            '--output', path,
            '--min-api', str(API_LEVEL),
            '--lib', ANDROID_JAR,
            '--classpath', utils.R8_JAR,
            '--main-dex-list', '/dev/null',
            os.path.join(utils.BUILD, 'test/examples/hello.jar')]
    utils.PrintCmd(args)
    subprocess.check_call(args)


def test_r8command():
  with utils.TempDir() as path:
    # SAMPLE_JAR and R8LIB_JAR should not have any classes in common, since e.g.
    # R8CommandParser should have been minified in R8LIB_JAR.
    # Just in case R8CommandParser is also present in R8LIB_JAR, we put
    # SAMPLE_JAR first on the classpath to use its version of R8CommandParser.
    args = ['java', '-cp', '%s:%s' % (SAMPLE_JAR, R8LIB_JAR),
            'com.android.tools.r8.R8CommandParser',
            '--output', path + "/output.zip",
            '--min-api', str(API_LEVEL),
            '--lib', ANDROID_JAR,
            '--main-dex-list', '/dev/null',
            os.path.join(utils.BUILD, 'test/examples/hello.jar')]
    utils.PrintCmd(args)
    subprocess.check_call(args)


def test_r8cfcommand():
  with utils.TempDir() as path:
    # SAMPLE_JAR and R8LIB_JAR should not have any classes in common, since e.g.
    # R8CommandParser should have been minified in R8LIB_JAR.
    # Just in case R8CommandParser is also present in R8LIB_JAR, we put
    # SAMPLE_JAR first on the classpath to use its version of R8CommandParser.
    args = ['java', '-cp', '%s:%s' % (SAMPLE_JAR, R8LIB_JAR),
            'com.android.tools.r8.R8CommandParser',
            '--classfile',
            '--output', path + "/output.jar",
            '--lib', utils.RT_JAR,
            os.path.join(utils.BUILD, 'test/examples/hello.jar')]
    utils.PrintCmd(args)
    subprocess.check_call(args)


def main():
  # Handle --help
  parser.parse_args()

  build_r8lib()
  test_d8sample()
  test_r8command()
  test_r8cfcommand()


if __name__ == '__main__':
  main()
