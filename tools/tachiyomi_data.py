# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import utils

ANDROID_J_API = '16'
BASE = os.path.join(utils.THIRD_PARTY, 'tachiyomi')

VERSIONS = {
  'b15d2fe16864645055af6a745a62cc5566629798': {
    'deploy' : {
      'inputs': [os.path.join(BASE, 'program.jar')],
      'pgconf': [os.path.join(BASE, 'proguard.config')],
      'libraries': [os.path.join(BASE, 'library.jar')],
      'min-api' : ANDROID_J_API,
    },
  },
}
