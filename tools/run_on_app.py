#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from __future__ import print_function
from glob import glob
import copy
import optparse
import os
import shutil
import sys
import time

import archive
import gradle
import gmail_data
import gmscore_data
import nest_data
from sanitize_libraries import SanitizeLibraries, SanitizeLibrariesInPgconf
import toolhelper
import update_prebuilds_in_android
import utils
import youtube_data
import chrome_data
import r8_data
import iosched_data

TYPES = ['dex', 'deploy', 'proguarded']
APPS = [
  'gmscore', 'nest', 'youtube', 'gmail', 'chrome', 'r8', 'iosched']
COMPILERS = ['d8', 'r8']
COMPILER_BUILDS = ['full', 'lib']

# We use this magic exit code to signal that the program OOM'ed
OOM_EXIT_CODE = 42
# According to Popen.returncode doc:
# A negative value -N indicates that the child was terminated by signal N.
TIMEOUT_KILL_CODE = -9

# Log file names
FIND_MIN_XMX_FILE = 'find_min_xmx_results'
FIND_MIN_XMX_DIR = 'find_min_xmx'

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--compiler',
                    help='The compiler to use',
                    choices=COMPILERS)
  result.add_option('--compiler-build',
                    help='Compiler build to use',
                    choices=COMPILER_BUILDS,
                    default='lib')
  result.add_option('--hash',
                    help='The version of D8/R8 to use')
  result.add_option('--app',
                    help='What app to run on',
                    choices=APPS)
  result.add_option('--run-all',
                    help='Compile all possible combinations',
                    default=False,
                    action='store_true')
  result.add_option('--expect-oom',
                    help='Expect that compilation will fail with an OOM',
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
  result.add_option('--max-memory',
                    help='The maximum memory in MB to run with',
                    type='int')
  result.add_option('--find-min-xmx',
                    help='Find the minimum amount of memory we can run in',
                    default=False,
                    action='store_true')
  result.add_option('--find-min-xmx-min-memory',
                    help='Setting the minimum memory baseline to run in',
                    type='int')
  result.add_option('--find-min-xmx-max-memory',
                    help='Setting the maximum memory baseline to run in',
                    type='int')
  result.add_option('--find-min-xmx-range-size',
                    help='Setting the size of the acceptable memory range',
                    type='int',
                    default=32)
  result.add_option('--find-min-xmx-archive',
                    help='Archive find-min-xmx results on GCS',
                    default=False,
                    action='store_true')
  result.add_option('--no-extra-pgconf', '--no_extra_pgconf',
                    help='Build without the following extra rules: ' +
                         '-printconfiguration, -printmapping, -printseeds, ' +
                         '-printusage',
                    default=False,
                    action='store_true')
  result.add_option('--timeout',
                    type='int',
                    default=0,
                    help='Set timeout instead of waiting for OOM.')
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
  result.add_option('--debug-agent',
                    help='Run with debug agent.',
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
  result.add_option('--track-time-in-memory',
                    help='Plot the times taken from memory starting point to '
                         'end-point with defined memory increment',
                    default=False,
                    action='store_true')
  result.add_option('--track-time-in-memory-max',
                    help='Setting the maximum memory baseline to run in',
                    type='int')
  result.add_option('--track-time-in-memory-min',
                    help='Setting the minimum memory baseline to run in',
                    type='int')
  result.add_option('--track-time-in-memory-increment',
                    help='Setting the increment',
                    type='int',
                    default=32)
  result.add_option('--print-times',
                    help='Include timing',
                    default=False,
                    action='store_true')
  result.add_option('--cpu-list',
                    help='Run under \'taskset\' with these CPUs. See '
                         'the \'taskset\' -c option for the format')
  result.add_option('--quiet',
                    help='Disable compiler logging',
                    default=False,
                    action='store_true')
  (options, args) = result.parse_args(argv)
  assert not options.hash or options.no_build, (
      'Argument --no-build is required when using --hash')
  assert not options.hash or options.compiler_build == 'full', (
      'Compiler build lib not yet supported with --hash')
  return (options, args)

# Most apps have -printmapping, -printseeds, -printusage and
# -printconfiguration in the Proguard configuration. However we don't
# want to write these files in the locations specified.
# Instead generate an auxiliary Proguard configuration placing these
# output files together with the dex output.
def GenerateAdditionalProguardConfiguration(temp, outdir):
  name = "output.config"
  with open(os.path.join(temp, name), 'w') as f:
    f.write('-printmapping ' + os.path.join(outdir, 'proguard.map') + "\n")
    f.write('-printseeds ' + os.path.join(outdir, 'proguard.seeds') + "\n")
    f.write('-printusage ' + os.path.join(outdir, 'proguard.usage') + "\n")
    f.write('-printconfiguration ' + os.path.join(outdir, 'proguard.config') + "\n")
    return os.path.abspath(f.name)

# Please add bug number for disabled permutations and please explicitly
# do Bug: #BUG in the commit message of disabling to ensure re-enabling
DISABLED_PERMUTATIONS = [
  # (app, version, type), e.g., ('gmail', '180826.15', 'deploy')
]

def get_permutations():
  data_providers = {
      'gmscore': gmscore_data,
      'nest': nest_data,
      'youtube': youtube_data,
      'chrome': chrome_data,
      'gmail': gmail_data,
      'r8': r8_data,
      'iosched': iosched_data,
  }
  # Check to ensure that we add all variants here.
  assert len(APPS) == len(data_providers)
  for app, data in data_providers.items():
    for version in data.VERSIONS:
      for type in data.VERSIONS[version]:
        if (app, version, type) not in DISABLED_PERMUTATIONS:
          # Only run with R8 lib to reduce cycle times.
          for use_r8lib in [True]:
            yield app, version, type, use_r8lib

def run_all(options, args):
  # Args will be destroyed
  assert len(args) == 0
  for name, version, type, use_r8lib in get_permutations():
    compiler = 'r8' if type == 'deploy' else 'd8'
    compiler_build = 'lib' if use_r8lib else 'full'
    print('Executing %s/%s with %s %s %s' % (compiler, compiler_build, name,
      version, type))

    fixed_options = copy.copy(options)
    fixed_options.app = name
    fixed_options.version = version
    fixed_options.compiler = compiler
    fixed_options.compiler_build = compiler_build
    fixed_options.type = type
    exit_code = run_with_options(fixed_options, [])
    if exit_code != 0:
      print('Failed %s %s %s with %s/%s' % (name, version, type, compiler,
        compiler_build))
      exit(exit_code)

def find_min_xmx(options, args):
  # Args will be destroyed
  assert len(args) == 0
  # If we can run in 128 MB then we are good (which we can for small examples
  # or D8 on medium sized examples)
  if options.find_min_xmx_min_memory:
    not_working = options.find_min_xmx_min_memory
  elif options.compiler == 'd8':
    not_working = 128
  else:
    not_working = 1024
  if options.find_min_xmx_max_memory:
    working = options.find_min_xmx_max_memory
  else:
    working = 1024 * 8
  exit_code = 0
  range = int(options.find_min_xmx_range_size)
  while working - not_working > range:
    next_candidate = int(working - ((working - not_working)/2))
    print('working: %s, non_working: %s, next_candidate: %s' %
          (working, not_working, next_candidate))
    extra_args = ['-Xmx%sM' % next_candidate]
    t0 = time.time()
    exit_code = run_with_options(options, [], extra_args)
    t1 = time.time()
    print('Running took: %s ms' % (1000.0 * (t1 - t0)))
    if exit_code != 0:
      if exit_code not in [OOM_EXIT_CODE, TIMEOUT_KILL_CODE]:
        print('Non OOM/Timeout error executing, exiting')
        return 2
    if exit_code == 0:
      working = next_candidate
    elif exit_code == TIMEOUT_KILL_CODE:
      print('Timeout. Continue to the next candidate.')
      not_working = next_candidate
    else:
      assert exit_code == OOM_EXIT_CODE
      not_working = next_candidate

  assert working - not_working <= range
  found_range = 'Found range: %s - %s' % (not_working, working)
  print(found_range)

  if options.find_min_xmx_archive:
    sha = utils.get_HEAD_sha1()
    (version, _) = get_version_and_data(options)
    destination = os.path.join(
        utils.R8_TEST_RESULTS_BUCKET,
        FIND_MIN_XMX_DIR,
        sha,
        options.compiler,
        options.compiler_build,
        options.app,
        version,
        get_type(options))
    gs_destination = 'gs://%s' % destination
    utils.archive_value(FIND_MIN_XMX_FILE, gs_destination, found_range + '\n')

  return 0

def print_min_xmx_ranges_for_hash(hash, compiler, compiler_build):
  app_directory = os.path.join(
      utils.R8_TEST_RESULTS_BUCKET,
      FIND_MIN_XMX_DIR,
      hash,
      compiler,
      compiler_build)
  gs_base = 'gs://%s' % app_directory
  for app in utils.ls_files_on_cloud_storage(gs_base).strip().split('\n'):
    for version in utils.ls_files_on_cloud_storage(app).strip().split('\n'):
      for type in utils.ls_files_on_cloud_storage(version).strip().split('\n'):
        gs_location = '%s%s' % (type, FIND_MIN_XMX_FILE)
        value = utils.cat_file_on_cloud_storage(gs_location, ignore_errors=True)
        print('%s\n' % value)

def track_time_in_memory(options, args):
  # Args will be destroyed
  assert len(args) == 0
  if not options.track_time_in_memory_min:
    raise Exception(
        'You have to specify --track_time_in_memory_min when running with '
        '--track-time-in-memory')
  if not options.track_time_in_memory_max:
    raise Exception(
        'You have to specify --track_time_in_memory_max when running with '
        '--track-time-in-memory')
  if not options.track_time_in_memory_increment:
    raise Exception(
        'You have to specify --track_time_in_memory_increment when running '
        'with --track-time-in-memory')
  current = options.track_time_in_memory_min
  print('Memory (KB)\tTime (ms)')
  with utils.TempDir() as temp:
    stdout = os.path.join(temp, 'stdout')
    stdout_fd = open(stdout, 'w')
    while current <= options.track_time_in_memory_max:
      extra_args = ['-Xmx%sM' % current]
      t0 = time.time()
      exit_code = run_with_options(options, [], extra_args, stdout_fd, quiet=True)
      t1 = time.time()
      total = (1000.0 * (t1 - t0)) if exit_code == 0 else -1
      print('%s\t%s' % (current, total))
      current += options.track_time_in_memory_increment

  return 0

def main(argv):
  (options, args) = ParseOptions(argv)
  if options.expect_oom and not options.max_memory:
    raise Exception(
        'You should only use --expect-oom if also specifying --max-memory')
  if options.expect_oom and options.timeout:
    raise Exception(
        'You should not use --timeout when also specifying --expect-oom')
  if options.find_min_xmx and options.track_time_in_memory:
    raise Exception(
        'You cannot both find the min xmx and track time at the same time')
  if options.run_all:
    return run_all(options, args)
  if options.find_min_xmx:
    return find_min_xmx(options, args)
  if options.track_time_in_memory:
    return track_time_in_memory(options, args)
  exit_code = run_with_options(options, args, quiet=options.quiet)
  if options.expect_oom:
    exit_code = 0 if exit_code == OOM_EXIT_CODE else 1
  return exit_code

def get_version_and_data(options):
  if options.app == 'gmscore':
    version = options.version or gmscore_data.LATEST_VERSION
    data = gmscore_data
  elif options.app == 'nest':
    version = options.version or '20180926'
    data = nest_data
  elif options.app == 'youtube':
    version = options.version or youtube_data.LATEST_VERSION
    data = youtube_data
  elif options.app == 'chrome':
    version = options.version or '180917'
    data = chrome_data
  elif options.app == 'gmail':
    version = options.version or '170604.16'
    data = gmail_data
  elif options.app == 'r8':
    version = options.version or 'cf'
    data = r8_data
  elif options.app == 'iosched':
    version = options.version or '2019'
    data = iosched_data
  else:
    raise Exception("You need to specify '--app={}'".format('|'.join(APPS)))
  return version, data

def get_type(options):
  if not options.type:
    return 'deploy' if options.compiler == 'r8' else 'proguarded'
  return options.type

def has_injars_and_libraryjars(pgconfs):
  # Check if there are -injars and -libraryjars in the configuration.
  has_injars = False
  has_libraryjars = False
  for pgconf in pgconfs:
    pgconf_dirname = os.path.abspath(os.path.dirname(pgconf))
    with open(pgconf) as pgconf_file:
      for line in pgconf_file:
        trimmed = line.strip()
        if trimmed.startswith('-injars'):
          has_injars = True
        elif trimmed.startswith('-libraryjars'):
          has_libraryjars = True
        if has_injars and has_libraryjars:
          return True
  return False

def check_no_injars_and_no_libraryjars(pgconfs):
  # Ensure that there are no -injars or -libraryjars in the configuration.
  for pgconf in pgconfs:
    pgconf_dirname = os.path.abspath(os.path.dirname(pgconf))
    with open(pgconf) as pgconf_file:
      for line in pgconf_file:
        trimmed = line.strip()
        if trimmed.startswith('-injars'):
          raise Exception("Unexpected -injars found in " + pgconf)
        elif trimmed.startswith('-libraryjars'):
          raise Exception("Unexpected -libraryjars found in " + pgconf)

def should_build(options):
  return not options.no_build

def build_desugared_library_dex(
    options,
    quiet,
    temp,
    android_java8_libs,
    desugared_lib_pg_conf,
    inputs,
    outdir):
  if not inputs:
    raise Exception(
        "If 'android_java8_libs' is specified the inputs must be explicit"
            + "(not defined using '-injars' in Proguard configuration files)")
  if outdir.endswith('.zip') or outdir.endswith('.jar'):
    raise Exception(
        "If 'android_java8_libs' is specified the output must be a directory")

  jar = None
  main = None
  if options.hash:
    jar = os.path.join(utils.LIBS, 'r8-' + options.hash + '.jar')
    main = 'com.android.tools.r8.R8'

  # Determine the l8 tool.
  assert(options.compiler_build in ['full', 'lib'])
  lib_prefix = 'r8lib-' if options.compiler_build == 'lib' else ''
  tool = lib_prefix + 'l8'

  # Prepare out directory.
  android_java8_libs_output = os.path.join(temp, 'android_java8_libs')
  os.makedirs(android_java8_libs_output)

  # Prepare arguments for L8.
  args = [
    '--desugared-lib', android_java8_libs['config'],
    '--lib', android_java8_libs['library'],
    '--output', android_java8_libs_output,
    '--pg-conf', desugared_lib_pg_conf,
    '--release',
  ]
  if 'pgconf' in android_java8_libs:
    for pgconf in android_java8_libs['pgconf']:
      args.extend(['--pg-conf', pgconf])
  args.extend(android_java8_libs['program'])

  # Run L8.
  exit_code = toolhelper.run(
      tool, args,
      build=should_build(options),
      debug=not options.no_debug,
      quiet=quiet,
      jar=jar,
      main=main)

  # Copy the desugared library DEX to the output.
  dex_file_name = (
      'classes' + str(len(glob(os.path.join(outdir, '*.dex'))) + 1) + '.dex')
  shutil.copyfile(
      os.path.join(android_java8_libs_output, 'classes.dex'),
      os.path.join(outdir, dex_file_name))

def run_with_options(options, args, extra_args=None, stdout=None, quiet=False):
  if extra_args is None:
    extra_args = []
  app_provided_pg_conf = False;
  # todo(121018500): remove when memory is under control
  if not any('-Xmx' in arg for arg in extra_args):
    if options.max_memory:
      extra_args.append('-Xmx%sM' % options.max_memory)
    else:
      extra_args.append('-Xmx8G')
  if not options.ignore_java_version:
    utils.check_java_version()

  if options.print_times:
    extra_args.append('-Dcom.android.tools.r8.printtimes=1')

  if not options.no_debug:
    extra_args.append('-Dcom.android.tools.r8.enableTestAssertions=1')

  outdir = options.out
  (version_id, data) = get_version_and_data(options)

  if options.compiler not in COMPILERS:
    raise Exception("You need to specify '--compiler={}'"
        .format('|'.join(COMPILERS)))

  if options.compiler_build not in COMPILER_BUILDS:
    raise Exception("You need to specify '--compiler-build={}'"
        .format('|'.join(COMPILER_BUILDS)))

  if not version_id in data.VERSIONS.keys():
    print('No version {} for application {}'
        .format(version_id, options.app))
    print('Valid versions are {}'.format(data.VERSIONS.keys()))
    return 1

  version = data.VERSIONS[version_id]

  type = get_type(options)

  if type not in version:
    print('No type {} for version {}'.format(type, version))
    print('Valid types are {}'.format(version.keys()))
    return 1
  values = version[type]

  args.extend(['--output', outdir])
  if 'min-api' in values:
    args.extend(['--min-api', values['min-api']])

  if 'main-dex-list' in values:
    args.extend(['--main-dex-list', values['main-dex-list']])

  inputs = values['inputs']
  libraries = values['libraries'] if 'libraries' in values else []

  if options.compiler == 'r8':
    if 'pgconf' in values and not options.k:
      if has_injars_and_libraryjars(values['pgconf']):
        sanitized_lib_path = os.path.join(
            os.path.abspath(outdir), 'sanitized_lib.jar')
        sanitized_pgconf_path = os.path.join(
            os.path.abspath(outdir), 'sanitized.config')
        SanitizeLibrariesInPgconf(
            sanitized_lib_path, sanitized_pgconf_path, values['pgconf'])
        libraries = [sanitized_lib_path]
        args.extend(['--pg-conf', sanitized_pgconf_path])
        inputs = []
      else:
        # -injars without -libraryjars or vice versa is not supported.
        check_no_injars_and_no_libraryjars(values['pgconf'])
        for pgconf in values['pgconf']:
          args.extend(['--pg-conf', pgconf])
        if 'sanitize_libraries' in values and values['sanitize_libraries']:
          sanitized_lib_path = os.path.join(
              os.path.abspath(outdir), 'sanitized_lib.jar')
          SanitizeLibraries(
            sanitized_lib_path, values['libraries'], values['inputs'])
          libraries = [sanitized_lib_path]
      app_provided_pg_conf = True
      if 'pgconf_extra' in values:
        extra_conf = os.path.join(os.path.abspath(outdir), 'pgconf_extra')
        with open(extra_conf, 'w') as extra_f:
          extra_f.write(values['pgconf_extra'])
        args.extend(['--pg-conf', extra_conf])
    if options.k:
      args.extend(['--pg-conf', options.k])
    if 'maindexrules' in values:
      for rules in values['maindexrules']:
        args.extend(['--main-dex-rules', rules])
    if 'allow-type-errors' in values:
      extra_args.append('-Dcom.android.tools.r8.allowTypeErrors=1')
    extra_args.append(
        '-Dcom.android.tools.r8.disallowClassInlinerGracefulExit=1')
    if 'system-properties' in values:
      for system_property in values['system-properties']:
        extra_args.append(system_property)

  if options.debug_agent:
    if not options.compiler_build == 'full':
      print('WARNING: Running debugging agent on r8lib is questionable...')
    extra_args.append(
      '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005')

  if not options.no_libraries:
    for lib in libraries:
      args.extend(['--lib', lib])

  if not outdir.endswith('.zip') and not outdir.endswith('.jar') \
      and not os.path.exists(outdir):
    os.makedirs(outdir)

  if options.hash:
    # Download r8-<hash>.jar from
    # https://storage.googleapis.com/r8-releases/raw/<hash>/.
    download_path = archive.GetUploadDestination(options.hash, 'r8.jar', True)
    assert utils.file_exists_on_cloud_storage(download_path), (
        'Could not find r8.jar file from provided hash: %s' % options.hash)
    destination = os.path.join(utils.LIBS, 'r8-' + options.hash + '.jar')
    utils.download_file_from_cloud_storage(
        download_path, destination, quiet=quiet)

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

  # Feature jars.
  features = values['features'] if 'features' in values else []
  for i, feature in enumerate(features, start=1):
    feature_out = os.path.join(outdir, 'feature-%d.zip' % i)
    for feature_jar in feature['inputs']:
      args.extend(['--feature', feature_jar, feature_out])

  args.extend(inputs)

  t0 = None
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
        if not options.no_extra_pgconf:
          additional_pg_conf = GenerateAdditionalProguardConfiguration(
              temp, os.path.abspath(pg_outdir))
          args.extend(['--pg-conf', additional_pg_conf])

      android_java8_libs = values.get('android_java8_libs')
      if android_java8_libs:
        desugared_lib_pg_conf = os.path.join(
            temp, 'desugared-lib-pg-conf.txt')
        args.extend(['--desugared-lib', android_java8_libs['config']])
        args.extend(
            ['--desugared-lib-pg-conf-output', desugared_lib_pg_conf])

      stderr_path = os.path.join(temp, 'stderr')
      with open(stderr_path, 'w') as stderr:
        jar = None
        main = None
        if options.compiler_build == 'full':
          tool = options.compiler
        else:
          assert(options.compiler_build == 'lib')
          tool = 'r8lib-' + options.compiler
        if options.hash:
          jar = os.path.join(utils.LIBS, 'r8-' + options.hash + '.jar')
          main = 'com.android.tools.r8.' + options.compiler.upper()
        if should_build(options):
          gradle.RunGradle(['r8lib' if tool.startswith('r8lib') else 'r8'])
        t0 = time.time()
        exit_code = toolhelper.run(tool, args,
            build=False,
            debug=not options.no_debug,
            profile=options.profile,
            track_memory_file=options.track_memory_to_file,
            extra_args=extra_args,
            stdout=stdout,
            stderr=stderr,
            timeout=options.timeout,
            quiet=quiet,
            cmd_prefix=[
                'taskset', '-c', options.cpu_list] if options.cpu_list else [],
            jar=jar,
            main=main)
      if exit_code != 0:
        with open(stderr_path) as stderr:
          stderr_text = stderr.read()
          if not quiet:
            print(stderr_text)
          if 'java.lang.OutOfMemoryError' in stderr_text:
            if not quiet:
              print('Failure was OOM')
            return OOM_EXIT_CODE
          return exit_code

      if options.print_memoryuse:
        print('{}(MemoryUse): {}'
            .format(options.print_memoryuse,
                utils.grep_memoryuse(options.track_memory_to_file)))

      if android_java8_libs:
        build_desugared_library_dex(
            options, quiet, temp, android_java8_libs,
            desugared_lib_pg_conf, inputs, outdir)


  if options.print_runtimeraw:
    print('{}(RunTimeRaw): {} ms'
        .format(options.print_runtimeraw, 1000.0 * (time.time() - t0)))

  if options.print_dexsegments:
    dex_files = glob(os.path.join(outdir, '*.dex'))
    utils.print_dexsegments(options.print_dexsegments, dex_files)
    print('{}-Total(CodeSize): {}'.format(
            options.print_dexsegments, compute_size_of_dex_files(dex_files)))
  return 0

def compute_size_of_dex_files(dex_files):
  dex_size = 0
  for dex_file in dex_files:
    dex_size += os.path.getsize(dex_file)
  return dex_size

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
