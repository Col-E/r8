#!/usr/bin/env python
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import compiledump
import gradle
import jdk
import optparse
import os
import shutil
import sys
import time
import utils
import zipfile

from datetime import datetime

SHRINKERS = ['r8', 'r8-full', 'r8-nolib', 'r8-nolib-full']

class AttrDict(dict):
  def __getattr__(self, name):
    return self.get(name, None)


# To generate the files for a new app, navigate to the app source folder and
# run:
# ./gradlew clean :app:assembleRelease -Dcom.android.tools.r8.dumpinputtodirectory=<path>
# and store the dump and the apk.
# If the app has instrumented tests, adding `testBuildType "release"` and
# running:
# ./gradlew assembleAndroidTest -Dcom.android.tools.r8.dumpinputtodirectory=<path>
# will also generate dumps and apk for tests.

class App(object):
  def __init__(self, fields):
    defaults = {
      'id': None,
      'name': None,
      'dump_app': None,
      'skip': False,
      'url': None,  # url is not used but nice to have for updating apps
      'revision': None,
      'folder': None,
      'skip_recompilation': False,
    }
    # This below does not work in python3
    defaults.update(fields.items())
    self.__dict__ = defaults


APPS = [
  App({
    'id': 'com.example.applymapping',
    'name': 'applymapping',
    'dump_app': 'dump_app.zip',
    'url': 'https://github.com/mkj-gram/applymapping',
    'revision': 'e3ae14b8c16fa4718e5dea8f7ad00937701b3c48',
    'folder': 'applymapping',
    'skip_recompilation': True,
  })
]


def download_app(app_sha):
  utils.DownloadFromGoogleCloudStorage(app_sha)


def is_minified_r8(shrinker):
  return '-nolib' not in shrinker


def is_full_r8(shrinker):
  return '-full' not in shrinker


def compute_size_of_dex_files_in_package(path):
  dex_size = 0
  z = zipfile.ZipFile(path, 'r')
  for filename in z.namelist():
    if filename.endswith('.dex'):
      dex_size += z.getinfo(filename).file_size
  return dex_size


def dump_for_app(app_dir, app):
  return os.path.join(app_dir, app.dump_app)


def get_results_for_app(app, options, temp_dir):
  app_folder = app.folder if app.folder else app.name + "_" + app.revision
  app_dir = os.path.join(utils.OPENSOURCE_DUMPS_DIR, app_folder)

  if not os.path.exists(app_dir) and not options.golem:
    # Download the app from google storage.
    download_app(app_dir + ".tar.gz.sha1")

  # Ensure that the dumps are in place
  assert os.path.isfile(dump_for_app(app_dir, app)), "Could not find dump " \
                                                     "for app " + app.name

  result = {}
  result['status'] = 'success'
  result_per_shrinker = build_app_with_shrinkers(
    app, options, temp_dir, app_dir)
  for shrinker, shrinker_result in result_per_shrinker.iteritems():
    result[shrinker] = shrinker_result
  return result


def build_app_with_shrinkers(app, options, temp_dir, app_dir):
  result_per_shrinker = {}
  for shrinker in options.shrinker:
    results = []
    build_app_and_run_with_shrinker(
      app, options, temp_dir, app_dir, shrinker, results)
    result_per_shrinker[shrinker] = results
  if len(options.apps) > 1:
    print('')
    log_results_for_app(app, result_per_shrinker, options)
    print('')

  return result_per_shrinker


def is_last_build(index, compilation_steps):
  return index == compilation_steps - 1


def build_app_and_run_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                                    results):
  print('[{}] Building {} with {}'.format(
    datetime.now().strftime("%H:%M:%S"),
    app.name,
    shrinker))
  print('To compile locally: '
        'tools/run_on_as_app.py --shrinker {} --r8-compilation-steps {} '
        '--app {}'.format(
    shrinker,
    options.r8_compilation_steps,
    app.name))
  print('HINT: use --shrinker r8-nolib --no-build if you have a local R8.jar')
  recomp_jar = None
  status = 'success'
  compilation_steps = 1 if app.skip_recompilation else options.r8_compilation_steps;
  for compilation_step in range(0, compilation_steps):
    if status != 'success':
      break
    print('Compiling {} of {}'.format(compilation_step, compilation_steps))
    result = {}
    try:
      start = time.time()
      (app_jar, new_recomp_jar) = \
        build_app_with_shrinker(
          app, options, temp_dir, app_dir, shrinker, compilation_step,
          compilation_steps, recomp_jar)
      end = time.time()
      dex_size = compute_size_of_dex_files_in_package(app_jar)
      result['build_status'] = 'success'
      result['recompilation_status'] = 'success'
      result['output_jar'] = app_jar
      result['dex_size'] = dex_size
      result['duration'] = int((end - start) * 1000)  # Wall time
      if (new_recomp_jar is None
          and not is_last_build(compilation_step, compilation_steps)):
        result['recompilation_status'] = 'failed'
        warn('Failed to build {} with {}'.format(app.name, shrinker))
        results.append(result)
        break
      recomp_jar = new_recomp_jar
    except Exception as e:
      warn('Failed to build {} with {}'.format(app.name, shrinker))
      if e:
        print('Error: ' + str(e))
      result['build_status'] = 'failed'
      status = 'failed'

    results.append(result)


def build_app_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                            compilation_step_index, compilation_steps,
                            prev_recomp_jar):
  r8jar = os.path.join(
    temp_dir, 'r8lib.jar' if is_minified_r8(shrinker) else 'r8.jar')

  args = AttrDict({
    'dump': dump_for_app(app_dir, app),
    'r8_jar': r8jar,
    'ea': False if options.disable_assertions else True,
    'version': 'master',
    'compiler': 'r8full' if is_full_r8(shrinker) else 'r8',
    'debug_agent': options.debug_agent,
    'program_jar': prev_recomp_jar,
    'nolib': not is_minified_r8(shrinker)
  })

  out_jar = os.path.join(temp_dir, "out.jar")
  compile_result = compiledump.run1(temp_dir, args, [])
  app_jar = os.path.join(
    temp_dir, '{}_{}_{}_dex_out.jar'.format(
      app.name, shrinker, compilation_step_index))

  if compile_result != 0 or not os.path.isfile(out_jar):
    assert False, "Compilation of app_jar failed"
  shutil.move(out_jar, app_jar)

  recomp_jar = None
  if compilation_step_index < compilation_steps - 1:
    args['classfile'] = True
    args['min_api'] = "10000"
    compile_result = compiledump.run1(temp_dir, args, [])
    if compile_result == 0:
      recomp_jar = os.path.join(
        temp_dir, '{}_{}_{}_cf_out.jar'.format(
          app.name, shrinker, compilation_step_index))
      shutil.move(out_jar, recomp_jar)

  return (app_jar, recomp_jar)


def log_results_for_apps(result_per_shrinker_per_app, options):
  print('')
  app_errors = 0
  for (app, result_per_shrinker) in result_per_shrinker_per_app:
    app_errors += (1 if log_results_for_app(app, result_per_shrinker, options)
                   else 0)
  return app_errors


def log_results_for_app(app, result_per_shrinker, options):
  if options.print_dexsegments:
    log_segments_for_app(app, result_per_shrinker, options)
    return False
  else:
    return log_comparison_results_for_app(app, result_per_shrinker, options)


def log_segments_for_app(app, result_per_shrinker, options):
  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    for result in result_per_shrinker.get(shrinker):
      benchmark_name = '{}-{}'.format(options.print_dexsegments, app.name)
      utils.print_dexsegments(benchmark_name, [result.get('output_jar')])
      duration = result.get('duration')
      print('%s-Total(RunTimeRaw): %s ms' % (benchmark_name, duration))
      print('%s-Total(CodeSize): %s' % (benchmark_name, result.get('dex_size')))


def percentage_diff_as_string(before, after):
  if after < before:
    return '-' + str(round((1.0 - after / before) * 100)) + '%'
  else:
    return '+' + str(round((after - before) / before * 100)) + '%'


def log_comparison_results_for_app(app, result_per_shrinker, options):
  print(app.name + ':')
  app_error = False
  if result_per_shrinker.get('status', 'success') != 'success':
    error_message = result_per_shrinker.get('error_message')
    print('  skipped ({})'.format(error_message))
    return

  proguard_result = result_per_shrinker.get('pg', {})
  proguard_dex_size = float(proguard_result.get('dex_size', -1))

  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    compilation_index = 1
    for result in result_per_shrinker.get(shrinker):
      build_status = result.get('build_status')
      if build_status != 'success' and build_status is not None:
        app_error = True
        warn('  {}-#{}: {}'.format(shrinker, compilation_index, build_status))
        continue

      print('  {}-#{}:'.format(shrinker, compilation_index))
      dex_size = result.get('dex_size')
      msg = '    dex size: {}'.format(dex_size)
      if dex_size != proguard_dex_size and proguard_dex_size >= 0:
        msg = '{} ({}, {})'.format(
          msg, dex_size - proguard_dex_size,
          percentage_diff_as_string(proguard_dex_size, dex_size))
        success(msg) if dex_size < proguard_dex_size else warn(msg)
      else:
        print(msg)

      recompilation_status = result.get('recompilation_status', '')
      if recompilation_status == 'failed':
        app_error = True
        warn('    recompilation {}-#{}: failed'.format(shrinker,
                                                       compilation_index))
        continue

      compilation_index += 1

  return app_error


def parse_options(argv):
  result = optparse.OptionParser()
  result.add_option('--app',
                    help='What app to run on',
                    choices=[app.name for app in APPS],
                    action='append')
  result.add_option('--bot',
                    help='Running on bot, use third_party dependency.',
                    default=False,
                    action='store_true')
  result.add_option('--debug-agent',
                    help='Enable Java debug agent and suspend compilation '
                         '(default disabled)',
                    default=False,
                    action='store_true')
  result.add_option('--disable-assertions', '--disable_assertions',
                    help='Disable assertions when compiling',
                    default=False,
                    action='store_true')
  result.add_option('--golem',
                    help='Running on golem, do not download',
                    default=False,
                    action='store_true')
  result.add_option('--hash',
                    help='The commit of R8 to use')
  result.add_option('--keystore',
                    help='Path to app.keystore',
                    default=os.path.join(utils.TOOLS_DIR, 'debug.keystore'))
  result.add_option('--keystore-password', '--keystore_password',
                    help='Password for app.keystore',
                    default='android')
  result.add_option('--app-logging-filter', '--app_logging_filter',
                    help='The apps for which to turn on logging',
                    action='append')
  result.add_option('--monkey',
                    help='Whether to install and run app(s) with monkey',
                    default=False,
                    action='store_true')
  result.add_option('--monkey-events', '--monkey_events',
                    help='Number of events that the monkey should trigger',
                    default=250,
                    type=int)
  result.add_option('--no-build', '--no_build',
                    help='Run without building ToT first (only when using ToT)',
                    default=False,
                    action='store_true')
  result.add_option('--no-logging', '--no_logging',
                    help='Disable logging except for errors',
                    default=False,
                    action='store_true')
  result.add_option('--print-dexsegments',
                    metavar='BENCHMARKNAME',
                    help='Print the sizes of individual dex segments as ' +
                         '\'<BENCHMARKNAME>-<APP>-<segment>(CodeSize): '
                         '<bytes>\'')
  result.add_option('--quiet',
                    help='Disable verbose logging',
                    default=False,
                    action='store_true')
  result.add_option('--r8-compilation-steps', '--r8_compilation_steps',
                    help='Number of times R8 should be run on each app',
                    default=2,
                    type=int)
  result.add_option('--run-tests', '--run_tests',
                    help='Whether to run instrumentation tests',
                    default=False,
                    action='store_true')
  result.add_option('--sign-apks', '--sign_apks',
                    help='Whether the APKs should be signed',
                    default=False,
                    action='store_true')
  result.add_option('--shrinker',
                    help='The shrinkers to use (by default, all are run)',
                    action='append')
  result.add_option('--version',
                    help='The version of R8 to use (e.g., 1.4.51)')
  (options, args) = result.parse_args(argv)
  if options.app:
    options.apps = [app for app in APPS if app.name in options.app]
    del options.app
  else:
    options.apps = APPS
  if options.app_logging_filter:
    for app_name in options.app_logging_filter:
      assert any(app.name == app_name for app in options.apps)
  if options.shrinker:
    for shrinker in options.shrinker:
      assert shrinker in SHRINKERS
  else:
    options.shrinker = [shrinker for shrinker in SHRINKERS]

  if options.hash or options.version:
    # No need to build R8 if a specific version should be used.
    options.no_build = True
    if 'r8-nolib' in options.shrinker:
      warn('Skipping shrinker r8-nolib because a specific version '
           + 'of r8 was specified')
      options.shrinker.remove('r8-nolib')
    if 'r8-nolib-full' in options.shrinker:
      warn('Skipping shrinker r8-nolib-full because a specific version '
           + 'of r8 was specified')
      options.shrinker.remove('r8-nolib-full')
  return (options, args)


def main(argv):
  (options, args) = parse_options(argv)

  if options.bot:
    options.no_logging = True
    options.shrinker = ['r8', 'r8-full']
    print(options.shrinker)

  if options.golem:
    golem.link_third_party()
    options.disable_assertions = True
    options.no_build = True
    options.r8_compilation_steps = 1
    options.quiet = True
    options.no_logging = True


  with utils.TempDir() as temp_dir:
    if options.hash:
      # Download r8-<hash>.jar from
      # https://storage.googleapis.com/r8-releases/raw/.
      target = 'r8-{}.jar'.format(options.hash)
      update_prebuilds_in_android.download_hash(
        temp_dir, 'com/android/tools/r8/' + options.hash, target)
      as_utils.MoveFile(
        os.path.join(temp_dir, target), os.path.join(temp_dir, 'r8lib.jar'),
        quiet=options.quiet)
    elif options.version:
      # Download r8-<version>.jar from
      # https://storage.googleapis.com/r8-releases/raw/.
      target = 'r8-{}.jar'.format(options.version)
      update_prebuilds_in_android.download_version(
        temp_dir, 'com/android/tools/r8/' + options.version, target)
      as_utils.MoveFile(
        os.path.join(temp_dir, target), os.path.join(temp_dir, 'r8lib.jar'),
        quiet=options.quiet)
    else:
      if not (options.no_build or options.golem):
        gradle.RunGradle(['r8', '-Pno_internal'])
        build_r8lib = False
        for shrinker in options.shrinker:
          if is_minified_r8(shrinker):
            build_r8lib = True
        if build_r8lib:
          gradle.RunGradle(['r8lib', '-Pno_internal'])
      # Make a copy of r8.jar and r8lib.jar such that they stay the same for
      # the entire execution of this script.
      if 'r8-nolib' in options.shrinker or 'r8-nolib-full' in options.shrinker:
        assert os.path.isfile(utils.R8_JAR), 'Cannot build without r8.jar'
        shutil.copyfile(utils.R8_JAR, os.path.join(temp_dir, 'r8.jar'))
      if 'r8' in options.shrinker or 'r8-full' in options.shrinker:
        assert os.path.isfile(utils.R8LIB_JAR), 'Cannot build without r8lib.jar'
        shutil.copyfile(utils.R8LIB_JAR, os.path.join(temp_dir, 'r8lib.jar'))

    result_per_shrinker_per_app = []
    for app in options.apps:
      if app.skip:
        continue
      result_per_shrinker_per_app.append(
        (app, get_results_for_app(app, options, temp_dir)))
    return log_results_for_apps(result_per_shrinker_per_app, options)


def success(message):
  CGREEN = '\033[32m'
  CEND = '\033[0m'
  print(CGREEN + message + CEND)


def warn(message):
  CRED = '\033[91m'
  CEND = '\033[0m'
  print(CRED + message + CEND)


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
