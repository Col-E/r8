#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import gradle
import os
import subprocess
import sys
import utils

def run(args, build=True):
  if build:
    gradle.RunGradle(['copyMavenDeps'])
  cmd = []
  cmd.append('java')
  cmd.extend(['-cp', 'build/deps/asm-6.0.jar:build/deps/asm-util-6.0.jar'])
  cmd.append('org.objectweb.asm.util.ASMifier')
  cmd.extend(args)
  utils.PrintCmd(cmd)
  result = subprocess.check_output(cmd)
  print(result)
  return result

def main():
  build = True
  args = []
  for arg in sys.argv[1:]:
    if arg in ("--build", "--no-build"):
      build = arg == "--build"
    else:
      args.append(arg)
  try:
    run(args, build)
  except subprocess.CalledProcessError as e:
    # In case anything relevant was printed to stdout, normally this is already
    # on stderr.
    print(e.output)
    return e.returncode

if __name__ == '__main__':
  sys.exit(main())
