# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import glob
import gradle
import os
import subprocess
import utils

def run(tool, args, build=None, debug=True,
        profile=False, track_memory_file=None, extra_args=None,
        stderr=None, stdout=None):
  if build is None:
    build, args = extract_build_from_args(args)
  if build:
    gradle.RunGradle(['r8lib' if tool.startswith('r8lib') else 'r8'])
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
  if tool == 'r8lib-d8':
    cmd.extend(['-cp', utils.R8LIB_JAR, 'com.android.tools.r8.D8'])
  elif tool == 'r8lib-r8':
    cmd.extend(['-cp', utils.R8LIB_JAR, 'com.android.tools.r8.R8'])
  else:
    cmd.extend(['-jar', utils.R8_JAR, tool])
  lib, args = extract_lib_from_args(args)
  if lib:
    cmd.extend(["--lib", lib])
  cmd.extend(args)
  utils.PrintCmd(cmd)
  return subprocess.call(cmd, stdout=stdout, stderr=stderr)

def run_in_tests(tool, args, build=None, debug=True, extra_args=None):
  if build is None:
    build, args = extract_build_from_args(args)
  if build:
    gradle.RunGradle([
      'copyMavenDeps',
      'compileTestJava',
    ])
  cmd = []
  cmd.append('java')
  if extra_args:
    cmd.extend(extra_args)
  if debug:
    cmd.append('-ea')
  cmd.extend(['-cp', ':'.join([
    utils.BUILD_MAIN_DIR,
    utils.BUILD_TEST_DIR,
  ] + glob.glob('%s/*.jar' % utils.BUILD_DEPS_DIR))])
  cmd.extend([tool])
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

def extract_lib_from_args(input_args):
  lib = None
  args = []
  for arg in input_args:
    if arg == '--lib-android':
      lib = utils.get_android_jar(28)
    elif arg == '--lib-java':
      lib = utils.RT_JAR
    else:
      args.append(arg)
  return lib, args
