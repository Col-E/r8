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
  cp = ":".join([os.path.join(utils.REPO_ROOT, 'build/deps/asm-6.2.1.jar'),
                 os.path.join(utils.REPO_ROOT, 'build/deps/asm-util-6.2.1.jar')])
  cmd.extend(['-cp', cp])
  cmd.append('org.objectweb.asm.util.ASMifier')
  cmd.extend(args)
  utils.PrintCmd(cmd)
  result = subprocess.check_output(cmd)
  print(result)
  return result

def main():
  build = True
  help = True
  args = []
  for arg in sys.argv[1:]:
    if arg in ("--build", "--no-build"):
      build = arg == "--build"
    elif arg == "--no-debug":
      args.append("-debug")
    elif arg in ("-help", "--help", "-debug"):
      help = True
      break
    else:
      help = False
      args.append(arg)
  if help:
    print "asmifier.py [--no-build] [--no-debug] <classfile>*"
    print "  --no-build    Don't run R8 dependencies."
    print "  --no-debug    Don't include local variable information in output."
    return
  try:
    run(args, build)
  except subprocess.CalledProcessError as e:
    # In case anything relevant was printed to stdout, normally this is already
    # on stderr.
    print(e.output)
    return e.returncode

if __name__ == '__main__':
  sys.exit(main())
