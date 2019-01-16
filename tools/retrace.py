#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import subprocess
import sys
import utils

def main():
  # Run the retrace tool with standard r8lib arguments.
  subprocess.call(['java', '-jar', utils.RETRACE_JAR, utils.R8LIB_JAR + '.map'])

if __name__ == '__main__':
  sys.exit(main())
