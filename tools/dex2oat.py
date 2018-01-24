#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import sys
import utils

def main():
  args = sys.argv[1:]
  if len(args) != 1:
    print("dex2oat takes exactly one argument, the file to run dex2oat on")
    return 1
  utils.verify_with_dex2oat(args[0])

if __name__ == '__main__':
  sys.exit(main())
