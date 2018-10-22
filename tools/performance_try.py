#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import subprocess
import sys
import utils

SCRIPT = '/google/data/ro/teams/dart/golem/bin/golem4.dart'
DART = os.path.join(utils.THIRD_PARTY, 'dart-sdk', 'bin', 'dart')

def Main():
  args = sys.argv[1:]
  if len(args) != 1:
    print('Performance tracking takes exactly one argument, the name for display')
    return 1
  subprocess.check_call([DART, SCRIPT, args[0]])

if __name__ == '__main__':
  sys.exit(Main())
