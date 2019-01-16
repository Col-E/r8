#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import optparse
import os
import subprocess
import sys

import utils

LINUX_DIR = os.path.join(utils.TOOLS_DIR, 'linux')

VERSIONS = [
  'default',
  '9.0.0',
  '8.1.0',
  '7.0.0',
  '6.0.1',
  '5.1.1',
]

DIRS = {
  'default': 'art',
  '9.0.0': 'art-9.0.0',
  '8.1.0': 'art-8.1.0',
  '7.0.0': 'art-7.0.0',
  '6.0.1': 'art-6.0.1',
  '5.1.1': 'art-5.1.1',
}

PRODUCTS = {
  'default': 'angler',
  '9.0.0': 'marlin',
  '8.1.0': 'marlin',
  '7.0.0': 'angler',
  '6.0.1': 'angler',
  '5.1.1': 'mako',
}

ARCHS = {
  'default': 'arm64',
  '9.0.0': 'arm64',
  '8.1.0': 'arm64',
  '7.0.0': 'arm64',
  '6.0.1': 'arm64',
  '5.1.1': 'arm',
}

VERBOSE_OPTIONS = [
  'verifier',
  'compiler',
  'gc',
  'jit',
  'jni',
  'class',
  'all',
]

def ParseOptions():
  parser = optparse.OptionParser()
  parser.add_option('--version',
                    help='Version of dex2oat. (defaults to latest, eg, tools/linux/art).',
                    choices=VERSIONS,
                    default='default')
  parser.add_option('--all',
                    help='Run dex2oat on all possible versions',
                    default=False,
                    action='store_true')
  parser.add_option('--output',
                    help='Where to place the output oat (defaults to no output / temp file).',
                    default=None)
  parser.add_option('--verbose',
                    help='Enable verbose dex2oat logging.',
                    choices=VERBOSE_OPTIONS,
                    default=None)
  return parser.parse_args()

def Main():
  (options, args) = ParseOptions()
  if len(args) != 1:
    print "Can only take a single dex/zip/jar/apk file as input."
    return 1
  if options.all and options.output:
    print "Can't write output when running all versions."
    return 1
  dexfile = args[0]
  oatfile = options.output
  versions = VERSIONS if options.all else [options.version]
  verbose = [options.verbose] if options.verbose else []
  if 'all' in verbose:
    verbose = [x for x in VERBOSE_OPTIONS if x is not 'all']
  for version in versions:
    run(dexfile, oatfile, version, verbose)
    print
  return 0

def run(dexfile, oatfile=None, version='default', verbose=[]):
  # dex2oat accepts non-existent dex files, check here instead
  if not os.path.exists(dexfile):
    raise Exception('DEX file not found: "{}"'.format(dexfile))
  with utils.TempDir() as temp:
    if not oatfile:
      oatfile = os.path.join(temp, "out.oat")
    base = os.path.join(LINUX_DIR, DIRS[version])
    product = PRODUCTS[version]
    arch = ARCHS[version]
    cmd = [
      os.path.join(base, 'bin', 'dex2oat'),
      '--android-root=' + os.path.join(base, 'product', product, 'system'),
      '--runtime-arg',
      '-Xnorelocate',
      '--dex-file=' + dexfile,
      '--oat-file=' + oatfile,
      '--instruction-set=' + arch,
    ]
    for flag in verbose:
      cmd += ['--runtime-arg', '-verbose:' + flag]
    env = {"LD_LIBRARY_PATH": os.path.join(base, 'lib')}
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd, env = env)

if __name__ == '__main__':
  sys.exit(Main())
