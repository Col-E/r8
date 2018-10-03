# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import utils

THIRD_PARTY = os.path.join(utils.REPO_ROOT, 'third_party')
ANDROID_L_API = '21'
BASE = os.path.join(THIRD_PARTY, 'nest')

V20180926_BASE = os.path.join(BASE, 'nest_20180926_7c6cfb')

# NOTE: we always use android.jar for SDK v25, later we might want to revise it
#       to use proper android.jar version for each of youtube version separately.
ANDROID_JAR = os.path.join(THIRD_PARTY, 'android_jar', 'lib-v25', 'android.jar')

VERSIONS = {
  '20180926': {
    'dex' : {
      'inputs': [os.path.join(V20180926_BASE, 'obsidian-development-debug.apk')],
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      'inputs': [os.path.join(V20180926_BASE, 'obsidian-development-debug.jar')],
      'libraries' : [ANDROID_JAR],
      'pgconf': [
          os.path.join(V20180926_BASE, 'proguard', 'proguard.cfg'),
          os.path.join(V20180926_BASE, 'proguard', 'proguard-no-optimizations.cfg'),
          os.path.join(V20180926_BASE, 'proguard', 'proguard-ignore-warnings.cfg')],
      # Build for native multi dex
      'min-api' : ANDROID_L_API,
    }
  },
}
