# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import gradle
import subprocess
import utils

def run(tool, args, build=None, debug=True,
        profile=False, track_memory_file=None, extra_args=None):
  if build is None:
    build, args = extract_build_from_args(args)
  if build:
    gradle.RunGradle(['r8'])
  cmd = []
  if track_memory_file:
    cmd.extend(['tools/track_memory.sh', track_memory_file])
  cmd.append('java')
  if extra_args:
    cmd.extend(extra_args)
  if debug:
    cmd.append('-ea')
  if profile:
    cmd.append('-agentlib:hprof=cpu=samples,interval=1,depth=8')
  cmd.extend(['-jar', utils.R8_JAR, tool])
  cmd.extend(args)
  utils.PrintCmd(cmd)
  return subprocess.call(cmd)

def extract_build_from_args(input_args):
  build = True
  args = []
  for arg in input_args:
    if arg in ("--build", "--no-build"):
      build = arg == "--build"
    else:
      args.append(arg)
  return build, args
