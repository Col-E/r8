#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Utility methods to make running on our performance tracking system easier.
import os
import sys

LINKED_THIRD_PARTY_DIRECTORIES = [
    'android_jar',
    'android_sdk',
    'benchmarks',
    'framework',
    'gmail',
    'gmscore',
    'gradle',
    'gradle-plugin',
    'openjdk',
    'opensource_apps',
    'proguard',
    'proguardsettings',
    'r8',
    'remapper',
    'retrace_benchmark',
    'sample_libraries',
    'youtube',
]

LINKED_TOOL_DIRECTORIES = [
  'linux/dx',
]

# Path to our internally updated third party
THIRD_PARTY_SOURCE = "/usr/local/google/home/golem/r8/third_party"
TOOLS_SOURCE = "/usr/local/google/home/golem/r8/tools"

def link_third_party():
  assert os.path.exists('third_party')
  for dir in LINKED_THIRD_PARTY_DIRECTORIES:
    src = os.path.join(THIRD_PARTY_SOURCE, dir)
    dest = os.path.join('third_party', dir)
    if os.path.exists(dest):
      raise Exception('Destination "{}" already exists, are you running with'
                      ' --golem locally'.format(dest))
    print('Symlinking {} to {}'.format(src, dest))
    os.symlink(src, dest)
  for dir in LINKED_TOOL_DIRECTORIES:
    src = os.path.join(TOOLS_SOURCE, dir)
    dest = os.path.join('tools', dir)
    if os.path.exists(dest):
      raise Exception('Destination "{}" already exists, are you running with'
                      ' --golem locally'.format(dest))
    print('Symlinking {} to {}'.format(src, dest))
    if '/' in dir:
      os.makedirs(os.path.dirname(dest))
    os.symlink(src, dest)

if __name__ == '__main__':
  sys.exit(link_third_party())
