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
KEEP_RULES = os.path.join(utils.REPO_ROOT, 'src/main/keep.txt')
R8LIB_JAR = os.path.join(utils.LIBS, 'r8lib.jar')
R8LIB_MAP_FILE = os.path.join(utils.LIBS, 'r8lib-map.txt')

API_LEVEL = 26
ANDROID_JAR = 'third_party/android_jar/lib-v%s/android.jar' % API_LEVEL


def build_r8lib():
  toolhelper.run(
      'r8',
      ('--release',
       '--classfile',
       '--lib', utils.RT_JAR,
       utils.R8_JAR,
       '--output', R8LIB_JAR,
       '--pg-conf', KEEP_RULES,
       '--pg-map-output', R8LIB_MAP_FILE))


def test_d8sample():
  with utils.TempDir() as path:
    args = ['java', '-cp', '%s:%s' % (R8LIB_JAR, SAMPLE_JAR),
            'com.android.tools.apiusagesample.D8ApiUsageSample',
            '--output', path,
            '--min-api', str(API_LEVEL),
            '--lib', ANDROID_JAR,
            '--classpath', utils.R8_JAR,
            '--main-dex-list', '/dev/null',
            os.path.join(utils.BUILD, 'test/examples/hello.jar')]
    utils.PrintCmd(args)
    subprocess.check_call(args)


def main():
  # Handle --help
  parser.parse_args()

  build_r8lib()
  test_d8sample()


if __name__ == '__main__':
  main()
