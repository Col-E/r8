# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import utils

ANDROID_N_API = '24'
BASE = os.path.join(utils.THIRD_PARTY, 'chrome')

V200430_BASE = os.path.join(BASE, 'chrome_200430')
V200520_MINIMAL_BASE = os.path.join(
    BASE, 'monochrome_public_minimal_apks', 'chrome_200520')

VERSIONS = {
  '200430': {
    'deploy' : {
        'inputs': [os.path.join(V200430_BASE, 'program.jar')],
        'pgconf': [os.path.join(V200430_BASE, 'proguard.config')],
        'libraries': [os.path.join(V200430_BASE, 'library.jar')],
        'min-api': ANDROID_N_API,
    },
  },
  '200520-monochrome_public_minimal_apks': {
    'deploy' : {
        'inputs': [os.path.join(V200520_MINIMAL_BASE, 'program.jar')],
        'features': [
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-1.jar')] },
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-2.jar')] },
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-3.jar')] },
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-4.jar')] },
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-5.jar')] },
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-6.jar')] },
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-7.jar')] },
            { 'inputs': [os.path.join(V200520_MINIMAL_BASE, 'feature-8.jar')] }
        ],
        'pgconf': [os.path.join(V200520_MINIMAL_BASE, 'proguard.config'),
                   utils.IGNORE_WARNINGS_RULES],
        'libraries': [os.path.join(V200520_MINIMAL_BASE, 'library.jar')],
        'min-api': ANDROID_N_API
    },
  },
}

def GetLatestVersion():
  return '200520-monochrome_public_minimal_apks'

def GetName():
  return 'chrome'

def GetMemoryData(version):
  assert version == '200520-monochrome_public_minimal_apks'
  return {
      'find-xmx-min': 600,
      'find-xmx-max': 700,
      'find-xmx-range': 16,
      'oom-threshold': 625,
  }
