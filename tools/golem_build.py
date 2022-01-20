#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Utility script to make it easier to update what golem builds.

import sys

import gradle
import retrace_benchmark
import run_benchmark
import run_on_app_dump

GRADLE_ARGS = ['--no-daemon', '-Pno_internal']

LEGACY_BUILD_TARGETS = [
  'R8',
  'D8',
  'buildExampleJars',
  'downloadAndroidCts',
  'downloadDx']

def lower(items):
  return [ item.lower() for item in items ]

def Main():
  targets = set()
  targets.update(lower(LEGACY_BUILD_TARGETS))
  targets.update(lower(retrace_benchmark.GOLEM_BUILD_TARGETS))
  targets.update(lower(run_benchmark.GOLEM_BUILD_TARGETS))
  targets.update(lower(run_on_app_dump.GOLEM_BUILD_TARGETS))
  cmd = GRADLE_ARGS + [target for target in targets]
  gradle.RunGradle(cmd)

if __name__ == '__main__':
  sys.exit(Main())
