# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import glob
import time
import subprocess
from threading import Timer

import gradle
import jdk
import utils


def run(tool, args, build=None, debug=True,
        profile=False, track_memory_file=None, extra_args=None,
        stderr=None, stdout=None, return_stdout=False, timeout=0, quiet=False,
        cmd_prefix=None, jar=None, main=None, time_consumer=None,
        debug_agent=None, worker_id=None):
  cmd = []
  if cmd_prefix:
    cmd.extend(cmd_prefix)
  if build is None:
    build, args = extract_build_from_args(args)
  if build:
    gradle.RunGradle([
        utils.GRADLE_TASK_R8LIB if tool.startswith('r8lib')
        else utils.GRADLE_TASK_R8], new_gradle=True)
  if track_memory_file:
    cmd.extend(['tools/track_memory.sh', track_memory_file])
  cmd.append(jdk.GetJavaExecutable())
  if extra_args:
    cmd.extend(extra_args)
  if debug_agent is None:
    debug_agent, args = extract_debug_agent_from_args(args)
  if debug_agent:
    cmd.append(
        '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005')
  if debug:
    cmd.append('-ea')
  if profile:
    cmd.append('-agentlib:hprof=cpu=samples,interval=1,depth=8')
  if jar:
    cmd.extend(['-cp', jar, main])
  elif tool == 'r8lib-d8':
    cmd.extend(['-cp', utils.R8LIB_JAR, 'com.android.tools.r8.D8'])
  elif tool == 'r8lib-l8':
    cmd.extend(['-cp', utils.R8LIB_JAR, 'com.android.tools.r8.L8'])
  elif tool == 'r8lib-r8':
    cmd.extend(['-cp', utils.R8LIB_JAR, 'com.android.tools.r8.R8'])
  elif tool == 'r8lib-tracereferences':
    cmd.extend(['-cp', utils.R8LIB_JAR, 'com.android.tools.r8.tracereferences.TraceReferences'])
  else:
    cmd.extend(['-jar', utils.R8_JAR, tool])
  lib, args = extract_lib_from_args(args)
  if lib:
    cmd.extend(["--lib", lib])
  cmd.extend(args)
  utils.PrintCmd(cmd, quiet=quiet, worker_id=worker_id)
  start = time.time()
  if timeout > 0:
    kill = lambda process: process.kill()
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    timer = Timer(timeout, kill, [proc])
    try:
      timer.start()
      stdout, stderr = proc.communicate()
    finally:
      timer.cancel()
    result = stdout.decode('utf-8') if return_stdout else proc.returncode
  else:
    result = (
        subprocess.check_output(cmd).decode('utf-8')
        if return_stdout
        else subprocess.call(cmd, stdout=stdout, stderr=stderr))
  duration = int((time.time() - start) * 1000)
  if time_consumer:
    time_consumer(duration)
  return result

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

def extract_debug_agent_from_args(input_args):
  agent = False
  args = []
  for arg in input_args:
    if arg in ('--debug-agent', '--debug_agent'):
      agent = True
    else:
      args.append(arg)
  return agent, args
