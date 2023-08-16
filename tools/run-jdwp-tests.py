#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import subprocess
import sys
import utils

DEFAULT_TEST = 'org.apache.harmony.jpda.tests.share.AllTests'

TEST_RUNNER = 'org.junit.runner.JUnitCore'
TEST_PACKAGE = 'org.apache.harmony.jpda.tests.jdwp'

VERSIONS = [
  'default',
  '10.0.0',
  '9.0.0',
  '8.1.0',
  '7.0.0',
  '6.0.1',
  '5.1.1',
  '4.4.4'
]

JUNIT_HOSTDEX = os.path.join(
  utils.REPO_ROOT,
  'third_party', 'jdwp-tests', 'junit-hostdex.jar')

JDWP_TESTS_HOSTDEX = os.path.join(
  utils.REPO_ROOT,
  'third_party', 'jdwp-tests', 'apache-harmony-jdwp-tests-hostdex.jar')

IMAGE='/system/non/existent/jdwp/image.art'

# Timeout in ms
TIMEOUT=10000

DEBUGGER_EXTRA_FLAGS = [
  '-Xjnigreflimit:2000',
  '-Duser.language=en',
  '-Duser.region=US',
  '-Djpda.settings.verbose=true',
  '-Djpda.settings.transportAddress=127.0.0.1:55107',
  '-Djpda.settings.timeout=%d' % TIMEOUT,
  '-Djpda.settings.waitingTime=%d' % TIMEOUT
]

DEBUGGEE_EXTRA_FLAGS = [
]

def get_art_dir(version):
  if version == '4.4.4':
    art_dir = 'dalvik'
  else:
    art_dir = version == 'default' and 'art' or 'art-%s' % version
  return os.path.join(utils.REPO_ROOT, 'tools', 'linux', art_dir)

def get_lib_dir(version):
  return os.path.join(get_art_dir(version), 'lib')

def get_fw_dir(version):
  return os.path.join(get_art_dir(version), 'framework')

def get_vm(version):
  return os.path.join(get_art_dir(version), 'bin', 'dalvikvm')

def setup_environment(version):
  art_dir = get_art_dir(version)
  lib_dir = get_lib_dir(version)
  android_data = os.path.join(utils.REPO_ROOT, 'build', 'tmp', version)
  if not os.path.isdir(android_data):
    os.makedirs(android_data)
  if version == '4.4.4':
    # Dalvik expects that the dalvik-cache dir already exists.
    dalvik_cache_dir = os.path.join(android_data, 'dalvik-cache')
    if not os.path.isdir(dalvik_cache_dir):
      os.makedirs(dalvik_cache_dir)
  os.environ['ANDROID_DATA'] = android_data
  os.environ['ANDROID_ROOT'] = art_dir
  os.environ['LD_LIBRARY_PATH'] = lib_dir
  os.environ['DYLD_LIBRARY_PATH'] = lib_dir
  os.environ['LD_USE_LOAD_BIAS'] = '1'

def get_boot_libs(version):
  boot_libs = []
  if version == '4.4.4':
    # Dalvik
    boot_libs.extend(['core-hostdex.jar'])
  else:
    # ART
    boot_libs.extend(['core-libart-hostdex.jar'])
    if version != '5.1.1' and version != '6.0.1':
      boot_libs.extend(['core-oj-hostdex.jar'])
  boot_libs.extend(['apache-xml-hostdex.jar'])
  return [os.path.join(get_fw_dir(version), lib) for lib in boot_libs]

def get_common_flags(version):
  flags = []
  flags.extend(['-Xbootclasspath:%s' % ':'.join(get_boot_libs(version))])
  if version != '4.4.4':
    flags.extend(['-Ximage:%s' % IMAGE])
    if version != '5.1.1':
      flags.extend(['-Xcompiler-option', '--debuggable'])
  if version == '9.0.0' or version == '10.0.0':
    flags.extend(['-XjdwpProvider:internal'])
  return flags

def get_debuggee_flags(version):
  return get_common_flags(version) + DEBUGGEE_EXTRA_FLAGS

def get_debugger_flags(version):
  return get_common_flags(version) + DEBUGGER_EXTRA_FLAGS

def runDebuggee(version, args):
  art_dir = get_art_dir(version)
  lib_dir = get_lib_dir(version)
  fw_dir = get_fw_dir(version)
  cmd = [get_vm(version)]
  cmd.extend(get_debuggee_flags(version))
  cmd.extend(args)
  setup_environment(version)
  print("Running debuggee as: %s" % cmd)
  return subprocess.check_call(cmd)

def runDebugger(version, classpath, args):
  art_dir = get_art_dir(version)
  lib_dir = get_lib_dir(version)
  fw_dir = get_fw_dir(version)
  dalvikvm = os.path.join(art_dir, 'bin', 'dalvikvm')
  cmd = [dalvikvm]
  cmd.extend(['-classpath', '%s:%s' % (classpath, JUNIT_HOSTDEX)])
  cmd.extend(get_debugger_flags(version))
  cmd.append('-Djpda.settings.debuggeeJavaPath=%s %s' %\
             (dalvikvm, ' '.join(get_debuggee_flags(version))))
  cmd.extend(args)
  setup_environment(version)
  print("Running debugger as: " % cmd)
  return subprocess.check_call(cmd)

def usage():
  print("Usage: %s [--debuggee] [--version=<version>] [--classpath=<classpath>] <args>" % (sys.argv[0]))
  print("where <version> is one of:", ', '.join(VERSIONS))
  print("  and <classpath> is optional classpath (default: %s)" % JDWP_TESTS_HOSTDEX)
  print("  and <args> will be passed on as arguments to the art runtime.")

def main():
  version = 'default'
  debuggee = False
  args = []
  classpath = JDWP_TESTS_HOSTDEX
  for arg in sys.argv[1:]:
    if arg == '--help':
      usage()
      return 0
    elif arg.startswith('--version='):
      version = arg[len('--version='):]
    elif arg.startswith('--classpath='):
      classpath = arg[len('--classpath='):]
    else:
      args.append(arg)
  if version not in VERSIONS:
    print("Invalid version %s" % version)
    usage()
    return 1
  if not debuggee and len(args) == 0:
    args.append(DEFAULT_TEST)
  if debuggee:
    return runDebuggee(version, args)
  else:
    if len(args) == 0:
      args.append(DEFAULT_TEST)
    elif len(args) == 1:
      args = [TEST_RUNNER, args[0]]
    return runDebugger(version, classpath, args)

if __name__ == '__main__':
  sys.exit(main())
