#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from __future__ import print_function
from glob import glob
import copy
import optparse
import os
import sys
import time

import gmail_data
import gmscore_data
import golem
import toolhelper
import utils
import youtube_data
import chrome_data

TYPES = ['dex', 'deploy', 'proguarded']
APPS = ['gmscore', 'youtube', 'gmail', 'chrome']
COMPILERS = ['d8', 'r8']

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--compiler',
                    help='The compiler to use',
                    choices=COMPILERS)
  result.add_option('--app',
                    help='What app to run on',
                    choices=APPS)
  result.add_option('--run-all',
                    help='Compile all possible combinations',
                    default=False,
                    action='store_true')
  result.add_option('--type',
                    help='Default for R8: deploy, for D8: proguarded',
                    choices=TYPES)
  result.add_option('--out',
                    help='Where to place the output',
                    default=utils.BUILD)
  result.add_option('--no-build',
                    help='Run without building first',
                    default=False,
                    action='store_true')
  result.add_option('--golem',
                    help='Running on golem, do not build or download',
                    default=False,
                    action='store_true')
  result.add_option('--ignore-java-version',
                    help='Do not check java version',
                    default=False,
                    action='store_true')
  result.add_option('--no-libraries',
                    help='Do not pass in libraries, even if they exist in conf',
                    default=False,
                    action='store_true')
  result.add_option('--no-debug',
                    help='Run without debug asserts.',
                    default=False,
                    action='store_true')
  result.add_option('--version',
                    help='The version of the app to run')
  result.add_option('-k',
                    help='Override the default ProGuard keep rules')
  result.add_option('--compiler-flags',
                    help='Additional option(s) for the compiler. ' +
                         'If passing several options use a quoted string.')
  result.add_option('--r8-flags',
                    help='Additional option(s) for the compiler. ' +
                         'Same as --compiler-flags, keeping it for backward'
                         ' compatibility. ' +
                         'If passing several options use a quoted string.')
  # TODO(tamaskenez) remove track-memory-to-file as soon as we updated golem
  # to use --print-memoryuse instead
  result.add_option('--track-memory-to-file',
                    help='Track how much memory the jvm is using while ' +
                    ' compiling. Output to the specified file.')
  result.add_option('--profile',
                    help='Profile R8 run.',
                    default=False,
                    action='store_true')
  result.add_option('--dump-args-file',
                    help='Dump a file with the arguments for the specified ' +
                    'configuration. For use as a @<file> argument to perform ' +
                    'the run.')
  result.add_option('--print-runtimeraw',
                    metavar='BENCHMARKNAME',
                    help='Print the line \'<BENCHMARKNAME>(RunTimeRaw):' +
                        ' <elapsed> ms\' at the end where <elapsed> is' +
                        ' the elapsed time in milliseconds.')
  result.add_option('--print-memoryuse',
                    metavar='BENCHMARKNAME',
                    help='Print the line \'<BENCHMARKNAME>(MemoryUse):' +
                        ' <mem>\' at the end where <mem> is the peak' +
                        ' peak resident set size (VmHWM) in bytes.')
  result.add_option('--print-dexsegments',
                    metavar='BENCHMARKNAME',
                    help='Print the sizes of individual dex segments as ' +
                        '\'<BENCHMARKNAME>-<segment>(CodeSize): <bytes>\'')
  return result.parse_args(argv)

# Most apps have the -printmapping, -printseeds and -printusage in the
# Proguard configuration. However we don't want to write these files
# in the locations specified. Instead generate an auxiliary Proguard
# configuration placing these two output files together with the dex
# output.
def GenerateAdditionalProguardConfiguration(temp, outdir):
  name = "output.config"
  with open(os.path.join(temp, name), 'w') as f:
    f.write('-printmapping ' + os.path.join(outdir, 'proguard.map') + "\n")
    f.write('-printseeds ' + os.path.join(outdir, 'proguard.seeds') + "\n")
    f.write('-printusage ' + os.path.join(outdir, 'proguard.usage') + "\n")
    return os.path.abspath(f.name)

# Please add bug number for disabled permutations and please explicitly
# do Bug: #BUG in the commit message of disabling to ensure re-enabling
DISABLED_PERMUTATIONS = [
    ('gmail', '180826.15', 'proguarded'), # b/116840276
]

def get_permutations():
  data_providers = {
      'gmscore': gmscore_data,
      'youtube': youtube_data,
      'chrome': chrome_data,
      'gmail': gmail_data
  }
  # Check to ensure that we add all variants here.
  assert len(APPS) == len(data_providers)
  for app, data in data_providers.iteritems():
    for version in data.VERSIONS:
      for type in data.VERSIONS[version]:
        if (app, version, type) not in DISABLED_PERMUTATIONS:
          yield app, version, type

def run_all(options, args):
  # Args will be destroyed
  assert len(args) == 0
  for name, version, type in get_permutations():
    print('Execution %s %s %s' % (name, version, type))
    compiler = 'r8' if type == 'deploy' else 'd8'
    fixed_options = copy.copy(options)
    fixed_options.app = name
    fixed_options.version = version
    fixed_options.compiler = compiler
    fixed_options.type = type
    exit_code = run_with_options(fixed_options, [])
    if exit_code != 0:
      print('Failed %s %s %s with %s' % (name, version, type, compiler))
      exit(exit_code)

def main(argv):
  (options, args) = ParseOptions(argv)
  if not options.ignore_java_version:
    utils.check_java_version()

  if options.run_all:
    return run_all(options, args)
  return run_with_options(options, args)

def run_with_options(options, args):
  app_provided_pg_conf = False;
  if options.golem:
    golem.link_third_party()
    options.out = os.getcwd()
  outdir = options.out
  data = None
  if options.app == 'gmscore':
    options.version = options.version or 'v9'
    data = gmscore_data
  elif options.app == 'youtube':
    options.version = options.version or '12.22'
    data = youtube_data
  elif options.app == 'chrome':
    options.version = options.version or 'default'
    data = chrome_data
  elif options.app == 'gmail':
    options.version = options.version or '170604.16'
    data = gmail_data
  else:
    raise Exception("You need to specify '--app={}'".format('|'.join(APPS)))

  if options.compiler not in COMPILERS:
    raise Exception("You need to specify '--compiler={}'"
        .format('|'.join(COMPILERS)))

  if not options.version in data.VERSIONS.keys():
    print('No version {} for application {}'
        .format(options.version, options.app))
    print('Valid versions are {}'.format(data.VERSIONS.keys()))
    return 1

  version = data.VERSIONS[options.version]

  if not options.type:
    options.type = 'deploy' if options.compiler == 'r8' \
        else 'proguarded'

  if options.type not in version:
    print('No type {} for version {}'.format(options.type, options.version))
    print('Valid types are {}'.format(version.keys()))
    return 1
  values = version[options.type]
  inputs = None
  # For R8 'deploy' the JAR is located using the Proguard configuration
  # -injars option. For chrome we don't have the injars in the proguard files.
  if 'inputs' in values and (options.compiler != 'r8'
                             or options.type != 'deploy'
                             or options.app == 'chrome'):
    inputs = values['inputs']

  args.extend(['--output', outdir])
  if 'min-api' in values:
    args.extend(['--min-api', values['min-api']])

  if 'main-dex-list' in values:
    args.extend(['--main-dex-list', values['main-dex-list']])

  if options.compiler == 'r8':
    if 'pgconf' in values and not options.k:
      for pgconf in values['pgconf']:
        args.extend(['--pg-conf', pgconf])
        app_provided_pg_conf = True
    if options.k:
      args.extend(['--pg-conf', options.k])
    if 'maindexrules' in values:
      for rules in values['maindexrules']:
        args.extend(['--main-dex-rules', rules])

  if not options.no_libraries and 'libraries' in values:
    for lib in values['libraries']:
      args.extend(['--lib', lib])

  if not outdir.endswith('.zip') and not outdir.endswith('.jar') \
      and not os.path.exists(outdir):
    os.makedirs(outdir)

  # Additional flags for the compiler from the configuration file.
  if 'flags' in values:
    args.extend(values['flags'].split(' '))
  if options.compiler == 'r8':
    if 'r8-flags' in values:
      args.extend(values['r8-flags'].split(' '))

  # Additional flags for the compiler from the command line.
  if options.compiler_flags:
    args.extend(options.compiler_flags.split(' '))
  if options.r8_flags:
    args.extend(options.r8_flags.split(' '))

  if inputs:
    args.extend(inputs)

  t0 = time.time()
  if options.dump_args_file:
    with open(options.dump_args_file, 'w') as args_file:
      args_file.writelines([arg + os.linesep for arg in args])
  else:
    with utils.TempDir() as temp:
      if options.print_memoryuse and not options.track_memory_to_file:
        options.track_memory_to_file = os.path.join(temp,
            utils.MEMORY_USE_TMP_FILE)
      if options.compiler == 'r8' and app_provided_pg_conf:
        # Ensure that output of -printmapping and -printseeds go to the output
        # location and not where the app Proguard configuration places them.
        if outdir.endswith('.zip') or outdir.endswith('.jar'):
          pg_outdir = os.path.dirname(outdir)
        else:
          pg_outdir = outdir
        additional_pg_conf = GenerateAdditionalProguardConfiguration(
            temp, os.path.abspath(pg_outdir))
        args.extend(['--pg-conf', additional_pg_conf])
      build = not options.no_build and not options.golem
      exit_code = toolhelper.run(options.compiler, args,
                     build=build,
                     debug=not options.no_debug,
                     profile=options.profile,
                     track_memory_file=options.track_memory_to_file)
      if exit_code != 0:
        return exit_code

      if options.print_memoryuse:
        print('{}(MemoryUse): {}'
            .format(options.print_memoryuse,
                utils.grep_memoryuse(options.track_memory_to_file)))

  if options.print_runtimeraw:
    print('{}(RunTimeRaw): {} ms'
        .format(options.print_runtimeraw, 1000.0 * (time.time() - t0)))

  if options.print_dexsegments:
    dex_files = glob(os.path.join(outdir, '*.dex'))
    utils.print_dexsegments(options.print_dexsegments, dex_files)
  return 0

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
