#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import sys
import toolhelper

if __name__ == '__main__':
  sys.exit(toolhelper.run('disasm', sys.argv[1:]))
