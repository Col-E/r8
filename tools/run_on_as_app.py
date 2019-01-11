#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import apk_utils
import gradle
import os
import optparse
import subprocess
import sys
import time
import utils
import zipfile

import as_utils

SHRINKERS = ['r8', 'r8full', 'r8-minified', 'r8full-minified', 'proguard']
WORKING_DIR = utils.BUILD

if 'R8_BENCHMARK_DIR' in os.environ and os.path.isdir(os.environ['R8_BENCHMARK_DIR']):
  WORKING_DIR = os.environ['R8_BENCHMARK_DIR']

APPS = {
  # 'app-name': {
  #     'git_repo': ...
  #     'app_module': ... (default app)
  #     'archives_base_name': ... (default same as app_module)
  #     'flavor': ... (default no flavor)
  #     'releaseTarget': ... (default <app_module>:assemble<flavor>Release
  # },
  'AnExplorer': {
      'app_id': 'dev.dworks.apps.anexplorer.pro',
      'git_repo': 'https://github.com/1hakr/AnExplorer',
      'flavor': 'googleMobilePro',
      'signed-apk-name': 'AnExplorer-googleMobileProRelease-4.0.3.apk',
  },
  'AntennaPod': {
      'app_id': 'de.danoeh.antennapod',
      'git_repo': 'https://github.com/AntennaPod/AntennaPod.git',
      'flavor': 'play',
  },
  'apps-android-wikipedia': {
      'app_id': 'org.wikipedia',
      'git_repo': 'https://github.com/wikimedia/apps-android-wikipedia',
      'flavor': 'prod',
      'signed-apk-name': 'app-prod-universal-release.apk'
  },
  'friendlyeats-android': {
      'app_id': 'com.google.firebase.example.fireeats',
      'git_repo': 'https://github.com/firebase/friendlyeats-android.git'
  },
  'KISS': {
      'app_id': 'fr.neamar.kiss',
      'git_repo': 'https://github.com/Neamar/KISS',
  },
  'materialistic': {
      'app_id': 'io.github.hidroh.materialistic',
      'git_repo': 'https://github.com/hidroh/materialistic',
  },
  'Minimal-Todo': {
      'app_id': 'com.avjindersinghsekhon.minimaltodo',
      'git_repo': 'https://github.com/avjinder/Minimal-Todo',
  },
  'NewPipe': {
      'app_id': 'org.schabi.newpipe',
      'git_repo': 'https://github.com/TeamNewPipe/NewPipe',
  },
  'Simple-Calendar': {
      'app_id': 'com.simplemobiletools.calendar.pro',
      'git_repo': 'https://github.com/SimpleMobileTools/Simple-Calendar',
      'signed-apk-name': 'calendar-release.apk'
  },
  'tachiyomi': {
      'app_id': 'eu.kanade.tachiyomi',
      'git_repo': 'https://github.com/sgjesse/tachiyomi.git',
      'flavor': 'standard',
      'releaseTarget': 'app:assembleRelease',
  },
  # This does not build yet.
  'muzei': {
      'git_repo': 'https://github.com/sgjesse/muzei.git',
      'app_module': 'main',
      'archives_base_name': 'muzei',
      'skip': True,
  },
}

# Common environment setup.
user_home = os.path.expanduser('~')
android_home = os.path.join(user_home, 'Android', 'Sdk')
android_build_tools_version = '28.0.3'
android_build_tools = os.path.join(
    android_home, 'build-tools', android_build_tools_version)

# TODO(christofferqa): Do not rely on 'emulator-5554' name
emulator_id = 'emulator-5554'

def ComputeSizeOfDexFilesInApk(apk):
  dex_size = 0
  z = zipfile.ZipFile(apk, 'r')
  for filename in z.namelist():
    if filename.endswith('.dex'):
      dex_size += z.getinfo(filename).file_size
  return dex_size

def IsBuiltWithR8(apk):
  script = os.path.join(utils.TOOLS_DIR, 'extractmarker.py')
  return '~~R8' in subprocess.check_output(['python', script, apk]).strip()

def IsMinifiedR8(shrinker):
  return shrinker == 'r8-minified' or shrinker == 'r8full-minified'

def IsTrackedByGit(file):
  return subprocess.check_output(['git', 'ls-files', file]).strip() != ''

def GitClone(git_url):
  return subprocess.check_output(['git', 'clone', git_url]).strip()

def GitPull():
  # Use --no-edit to accept the auto-generated merge message, if any.
  return subprocess.call(['git', 'pull', '--no-edit']) == 0

def GitCheckout(file):
  return subprocess.check_output(['git', 'checkout', file]).strip()

def InstallApkOnEmulator(apk_dest):
  subprocess.check_call(
      ['adb', '-s', emulator_id, 'install', '-r', '-d', apk_dest])

def WaitForEmulator():
  stdout = subprocess.check_output(['adb', 'devices'])
  if '{}\tdevice'.format(emulator_id) in stdout:
    return

  print('Emulator \'{}\' not connected; waiting for connection'.format(
      emulator_id))

  time_waited = 0
  while True:
    time.sleep(10)
    time_waited += 10
    stdout = subprocess.check_output(['adb', 'devices'])
    if '{}\tdevice'.format(emulator_id) not in stdout:
      print('... still waiting for connection')
      if time_waited >= 5 * 60:
        raise Exception('No emulator connected for 5 minutes')
    else:
      return

def GetResultsForApp(app, config, options):
  git_repo = config['git_repo']

  # Checkout and build in the build directory.
  checkout_dir = os.path.join(WORKING_DIR, app)

  result = {}

  if not os.path.exists(checkout_dir):
    with utils.ChangedWorkingDirectory(WORKING_DIR):
      GitClone(git_repo)
  elif options.pull:
    with utils.ChangedWorkingDirectory(checkout_dir):
      # Checkout build.gradle to avoid merge conflicts.
      if IsTrackedByGit('build.gradle'):
        GitCheckout('build.gradle')

      if not GitPull():
        result['status'] = 'failed'
        result['error_message'] = 'Unable to pull from remote'
        return result

  result['status'] = 'success'

  result_per_shrinker = BuildAppWithSelectedShrinkers(
      app, config, options, checkout_dir)
  for shrinker, shrinker_result in result_per_shrinker.iteritems():
    result[shrinker] = shrinker_result

  return result

def BuildAppWithSelectedShrinkers(app, config, options, checkout_dir):
  result_per_shrinker = {}

  with utils.ChangedWorkingDirectory(checkout_dir):
    for shrinker in SHRINKERS:
      if options.shrinker and shrinker not in options.shrinker:
        continue

      apk_dest = None
      result = {}
      try:
        (apk_dest, profile_dest_dir) = BuildAppWithShrinker(
          app, config, shrinker, checkout_dir, options)
        dex_size = ComputeSizeOfDexFilesInApk(apk_dest)
        result['apk_dest'] = apk_dest,
        result['build_status'] = 'success'
        result['dex_size'] = dex_size
        result['profile_dest_dir'] = profile_dest_dir
      except Exception as e:
        warn('Failed to build {} with {}'.format(app, shrinker))
        if e:
          print('Error: ' + str(e))
        result['build_status'] = 'failed'

      if options.monkey:
        if result.get('build_status') == 'success':
          result['monkey_status'] = 'success' if RunMonkey(
              app, config, apk_dest) else 'failed'

      result_per_shrinker[shrinker] = result

    if IsTrackedByGit('gradle.properties'):
      GitCheckout('gradle.properties')

  return result_per_shrinker

def BuildAppWithShrinker(app, config, shrinker, checkout_dir, options):
  print('Building {} with {}'.format(app, shrinker))

  if options.disable_tot:
    as_utils.remove_r8_dependency(checkout_dir)
  else:
    as_utils.add_r8_dependency(checkout_dir, IsMinifiedR8(shrinker))

  app_module = config.get('app_module', 'app')
  archives_base_name = config.get(' archives_base_name', app_module)
  flavor = config.get('flavor')

  # Ensure that gradle.properties are not modified before modifying it to
  # select shrinker.
  if IsTrackedByGit('gradle.properties'):
    GitCheckout('gradle.properties')
  with open("gradle.properties", "a") as gradle_properties:
    if 'r8' in shrinker:
      gradle_properties.write('\nandroid.enableR8=true\n')
      if shrinker == 'r8full' or shrinker == 'r8full-minified':
        gradle_properties.write('android.enableR8.fullMode=true\n')
    else:
      assert shrinker == 'proguard'
      gradle_properties.write('\nandroid.enableR8=false\n')

  out = os.path.join(checkout_dir, 'out', shrinker)
  if not os.path.exists(out):
    os.makedirs(out)

  env = os.environ.copy()
  env['ANDROID_HOME'] = android_home
  env['JAVA_OPTS'] = '-ea'
  releaseTarget = config.get('releaseTarget')
  if not releaseTarget:
    releaseTarget = app_module + ':' + 'assemble' + (
        flavor.capitalize() if flavor else '') + 'Release'

  cmd = ['./gradlew', '--no-daemon', 'clean', releaseTarget, '--profile',
      '--stacktrace']

  utils.PrintCmd(cmd)
  build_process = subprocess.Popen(cmd, env=env, stdout=subprocess.PIPE)
  stdout = []
  while True:
    line = build_process.stdout.readline()
    if line != b'':
      stripped = line.rstrip()
      stdout.append(stripped)
      print(stripped)
    else:
      break

  apk_base_name = (archives_base_name
      + (('-' + flavor) if flavor else '') + '-release')
  signed_apk_name = config.get('signed-apk-name', apk_base_name + '.apk')
  unsigned_apk_name = apk_base_name + '-unsigned.apk'

  build_dir = config.get('build_dir', 'build')
  build_output_apks = os.path.join(app_module, build_dir, 'outputs', 'apk')
  if flavor:
    build_output_apks = os.path.join(build_output_apks, flavor, 'release')
  else:
    build_output_apks = os.path.join(build_output_apks, 'release')

  signed_apk = os.path.join(build_output_apks, signed_apk_name)
  unsigned_apk = os.path.join(build_output_apks, unsigned_apk_name)

  if options.sign_apks and not os.path.isfile(signed_apk):
    assert os.path.isfile(unsigned_apk)
    if options.sign_apks:
      keystore = 'app.keystore'
      keystore_password = 'android'
      apk_utils.sign_with_apksigner(
          android_build_tools,
          unsigned_apk,
          signed_apk,
          keystore,
          keystore_password)

  if os.path.isfile(signed_apk):
    apk_dest = os.path.join(out, signed_apk_name)
    as_utils.MoveFile(signed_apk, apk_dest)
  else:
    apk_dest = os.path.join(out, unsigned_apk_name)
    as_utils.MoveFile(unsigned_apk, apk_dest)

  assert IsBuiltWithR8(apk_dest) == ('r8' in shrinker), (
      'Unexpected marker in generated APK for {}'.format(shrinker))

  profile_dest_dir = os.path.join(out, 'profile')
  as_utils.MoveProfileReportTo(profile_dest_dir, stdout)

  return (apk_dest, profile_dest_dir)

def RunMonkey(app, config, apk_dest):
  WaitForEmulator()
  InstallApkOnEmulator(apk_dest)

  app_id = config.get('app_id')
  number_of_events_to_generate = 50

  stdout = subprocess.check_output(['adb', 'shell', 'monkey', '-p', app_id,
      str(number_of_events_to_generate)])
  return 'Events injected: {}'.format(number_of_events_to_generate) in stdout

def LogResults(result_per_shrinker_per_app, options):
  for app, result_per_shrinker in result_per_shrinker_per_app.iteritems():
    print(app + ':')

    if result_per_shrinker.get('status') != 'success':
      error_message = result_per_shrinker.get('error_message')
      print('  skipped ({})'.format(error_message))
      continue

    baseline = float(
        result_per_shrinker.get('proguard', {}).get('dex_size', -1))
    for shrinker in SHRINKERS:
      if shrinker not in result_per_shrinker:
        continue
      result = result_per_shrinker.get(shrinker)
      build_status = result.get('build_status')
      if build_status != 'success':
        warn('  {}: {}'.format(shrinker, build_status))
      else:
        print('  {}:'.format(shrinker))
        dex_size = result.get('dex_size')
        if dex_size != baseline and baseline >= 0:
          if dex_size < baseline:
            success('    dex size: {} ({}, -{}%)'.format(
              dex_size, dex_size - baseline,
              round((1.0 - dex_size / baseline) * 100), 1))
          elif dex_size >= baseline:
            warn('    dex size: {} ({}, +{}%)'.format(
              dex_size, dex_size - baseline,
              round((baseline - dex_size) / dex_size * 100, 1)))
        else:
          print('    dex size: {}'.format(dex_size))
        if options.monkey:
          monkey_status = result.get('monkey_status')
          if monkey_status != 'success':
            warn('    monkey: {}'.format(monkey_status))
          else:
            success('    monkey: {}'.format(monkey_status))

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--app',
                    help='What app to run on',
                    choices=APPS.keys())
  result.add_option('--monkey',
                    help='Whether to install and run app(s) with monkey',
                    default=False,
                    action='store_true')
  result.add_option('--pull',
                    help='Whether to pull the latest version of each app',
                    default=False,
                    action='store_true')
  result.add_option('--sign-apks', '--sign_apks',
                    help='Whether the APKs should be signed',
                    default=False,
                    action='store_true')
  result.add_option('--shrinker',
                    help='The shrinkers to use (by default, all are run)',
                    action='append')
  result.add_option('--disable-tot', '--disable_tot',
                    help='Whether to disable the use of the ToT version of R8',
                    default=False,
                    action='store_true')
  result.add_option('--no-build', '--no_build',
                    help='Run without building ToT first (only when using ToT)',
                    default=False,
                    action='store_true')
  (options, args) = result.parse_args(argv)
  if options.shrinker:
    for shrinker in options.shrinker:
      assert shrinker in SHRINKERS
  return (options, args)

def main(argv):
  global SHRINKERS

  (options, args) = ParseOptions(argv)
  assert options.disable_tot or os.path.isfile(utils.R8_JAR), (
      'Cannot build from ToT without r8.jar')
  assert options.disable_tot or os.path.isfile(utils.R8LIB_JAR), (
      'Cannot build from ToT without r8lib.jar')

  if options.disable_tot:
    # Cannot run r8 lib without adding r8lib.jar as an dependency
    SHRINKERS = [
        shrinker for shrinker in SHRINKERS
        if 'minified' not in shrinker]

  if not options.no_build and not options.disable_tot:
    gradle.RunGradle(['r8', 'r8lib'])

  result_per_shrinker_per_app = {}

  if options.app:
    result_per_shrinker_per_app[options.app] = GetResultsForApp(
        options.app, APPS.get(options.app), options)
  else:
    for app, config in APPS.iteritems():
      if not config.get('skip', False):
        result_per_shrinker_per_app[app] = GetResultsForApp(
            app, config, options)

  LogResults(result_per_shrinker_per_app, options)

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
