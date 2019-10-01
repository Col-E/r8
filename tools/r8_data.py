# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils
import os

VERSIONS = {
    'cf': {
      'deploy': {
          'inputs': [utils.PINNED_R8_JAR],
          'pgconf': [os.path.join(utils.REPO_ROOT, 'src', 'main', 'keep.txt')],
          'libraries' : [utils.RT_JAR],
          'flags': '--classfile',
      }
    }
}
