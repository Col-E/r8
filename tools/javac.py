#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import jdk
import utils
import subprocess
import sys

def run(args):
  cmd = [jdk.GetJavacExecutable()] + args
  utils.PrintCmd(cmd)
  result = subprocess.check_output(cmd)
  print result
  return result

def main():
  try:
    run(sys.argv[1:])
  except subprocess.CalledProcessError as e:
    # In case anything relevant was printed to stdout, normally this is already
    # on stderr.
    print e.output
    return e.returncode

if __name__ == '__main__':
  sys.exit(main())
