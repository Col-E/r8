#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import sys
import toolhelper

if __name__ == '__main__':
  sys.exit(toolhelper.run('r8', sys.argv[1:]))
