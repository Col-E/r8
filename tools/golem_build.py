#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Utility script to make it easier to update what golem builds.

import gradle
import sys

GRADLE_ARGS = ['--no-daemon']
BUILD_TARGETS = ['R8', 'D8', 'R8LibApiOnly', 'buildExampleJars', 'CompatDx',
                 'downloadAndroidCts', 'downloadDx']

def Main():
  gradle.RunGradle(GRADLE_ARGS + BUILD_TARGETS)

if __name__ == '__main__':
  sys.exit(Main())
