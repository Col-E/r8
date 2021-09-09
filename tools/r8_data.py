# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils
import os

ANDROID_L_API = '21'

VERSIONS = {
    '1.2.11-dev': {
      'deploy': {
          'inputs': [utils.PINNED_R8_JAR],
          'pgconf': [os.path.join(utils.REPO_ROOT, 'src', 'main', 'keep.txt')],
          'libraries' : [utils.RT_JAR],
          'pgconf_extra': '-dontwarn javax.annotation.Nullable',
          'flags': '--classfile',
      },
      'proguarded': {
          'inputs': [utils.PINNED_R8_JAR],
          'libraries' : [utils.RT_JAR],
          'min-api' : ANDROID_L_API,
      }
    }
}

def GetLatestVersion():
  return '1.2.11-dev'

def GetName():
  return 'r8'

def GetMemoryData(version):
  assert version == '1.2.11-dev'
  return {
      'find-xmx-min': 128,
      'find-xmx-max': 400,
      'find-xmx-range': 16,
      'oom-threshold': 247,
  }
