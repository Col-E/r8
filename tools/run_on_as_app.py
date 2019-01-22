#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import apk_masseur
import apk_utils
import gradle
import os
import optparse
import subprocess
import sys
import tempfile
import time
import utils
import zipfile

import as_utils

SHRINKERS = ['r8', 'r8-minified', 'r8full', 'r8full-minified', 'proguard']
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
      'git_repo': 'https://github.com/christofferqa/AnExplorer',
      'flavor': 'googleMobilePro',
      'signed-apk-name': 'AnExplorer-googleMobileProRelease-4.0.3.apk',
  },
  'AntennaPod': {
      'app_id': 'de.danoeh.antennapod',
      'git_repo': 'https://github.com/christofferqa/AntennaPod.git',
      'flavor': 'play',
  },
  'apps-android-wikipedia': {
      'app_id': 'org.wikipedia',
      'git_repo': 'https://github.com/christofferqa/apps-android-wikipedia',
      'flavor': 'prod',
      'signed-apk-name': 'app-prod-universal-release.apk'
  },
  'friendlyeats-android': {
      'app_id': 'com.google.firebase.example.fireeats',
      'git_repo': 'https://github.com/christofferqa/friendlyeats-android.git'
  },
  'KISS': {
      'app_id': 'fr.neamar.kiss',
      'git_repo': 'https://github.com/christofferqa/KISS',
  },
  'materialistic': {
      'app_id': 'io.github.hidroh.materialistic',
      'git_repo': 'https://github.com/christofferqa/materialistic',
  },
  'Minimal-Todo': {
      'app_id': 'com.avjindersinghsekhon.minimaltodo',
      'git_repo': 'https://github.com/christofferqa/Minimal-Todo',
  },
  'NewPipe': {
      'app_id': 'org.schabi.newpipe',
      'git_repo': 'https://github.com/christofferqa/NewPipe',
  },
  'Simple-Calendar': {
      'app_id': 'com.simplemobiletools.calendar.pro',
      'git_repo': 'https://github.com/christofferqa/Simple-Calendar',
      'signed-apk-name': 'calendar-release.apk'
  },
  'tachiyomi': {
      'app_id': 'eu.kanade.tachiyomi',
      'git_repo': 'https://github.com/sgjesse/tachiyomi.git',
      'flavor': 'standard',
      'releaseTarget': 'app:assembleRelease',
  },
  'tivi': {
      'app_id': 'app.tivi',
      # Forked from https://github.com/chrisbanes/tivi.git removing
      # signingConfigs.
      'git_repo': 'https://github.com/sgjesse/tivi.git',
      # TODO(123047413): Fails with R8.
      'skip': True,
  },
  'Signal-Android': {
    'app_id': 'org.thoughtcrime.securesms',
    'app_module': '',
    'flavor': 'play',
    'git_repo': 'https://github.com/mkj-gram/Signal-Android.git',
    'releaseTarget': 'assemblePlayRelease',
    'signed-apk-name': 'Signal-play-release-4.32.7.apk',
  },
  'chanu': {
    'app_id': 'com.chanapps.four.activity',
    'git_repo': 'https://github.com/mkj-gram/chanu.git',
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

def PercentageDiffAsString(before, after):
  if after < before:
    return '-' + str(round((1.0 - after / before) * 100)) + '%'
  else:
    return '+' + str(round((after - before) / before * 100)) + '%'

def UninstallApkOnEmulator(app, config):
  app_id = config.get('app_id')
  process = subprocess.Popen(
      ['adb', 'uninstall', app_id],
      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  stdout, stderr = process.communicate()

  if stdout.strip() == 'Success':
    # Successfully uninstalled
    return

  if 'Unknown package: {}'.format(app_id) in stderr:
    # Application not installed
    return

  raise Exception(
      'Unexpected result from `adb uninstall {}\nStdout: {}\nStderr: {}'.format(
          app_id, stdout, stderr))

def WaitForEmulator():
  stdout = subprocess.check_output(['adb', 'devices'])
  if '{}\tdevice'.format(emulator_id) in stdout:
    return True

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
        return False
    else:
      return True

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
        out_dir = os.path.join(checkout_dir, 'out', shrinker)
        (apk_dest, profile_dest_dir, proguard_config_file) = \
            BuildAppWithShrinker(app, config, shrinker, checkout_dir, out_dir,
                options)
        dex_size = ComputeSizeOfDexFilesInApk(apk_dest)
        result['apk_dest'] = apk_dest,
        result['build_status'] = 'success'
        result['dex_size'] = dex_size
        result['profile_dest_dir'] = profile_dest_dir

        profile = as_utils.ParseProfileReport(profile_dest_dir)
        result['profile'] = {
            task_name:duration for task_name, duration in profile.iteritems()
            if as_utils.IsGradleCompilerTask(task_name, shrinker)}
      except Exception as e:
        warn('Failed to build {} with {}'.format(app, shrinker))
        if e:
          print('Error: ' + str(e))
        result['build_status'] = 'failed'

      if result.get('build_status') == 'success':
        if options.monkey:
          result['monkey_status'] = 'success' if RunMonkey(
              app, config, options, apk_dest) else 'failed'

        if 'r8' in shrinker and options.r8_compilation_steps > 1:
          recompilation_results = []

          # Build app with gradle using -D...keepRuleSynthesisForRecompilation=
          # true.
          out_dir = os.path.join(checkout_dir, 'out', shrinker + '-1')
          extra_env_vars = {
            'JAVA_OPTS': ' '.join([
              '-ea:com.android.tools.r8...',
              '-Dcom.android.tools.r8.keepRuleSynthesisForRecompilation=true'
            ])
          }
          (apk_dest, profile_dest_dir, ext_proguard_config_file) = \
              BuildAppWithShrinker(app, config, shrinker, checkout_dir, out_dir,
                  options, extra_env_vars)
          dex_size = ComputeSizeOfDexFilesInApk(apk_dest)
          recompilation_result = {
            'apk_dest': apk_dest,
            'build_status': 'success',
            'dex_size': ComputeSizeOfDexFilesInApk(apk_dest),
            'monkey_status': 'skipped'
          }
          recompilation_results.append(recompilation_result)

          # Sanity check that keep rules have changed.
          with open(ext_proguard_config_file) as new:
            with open(proguard_config_file) as old:
              assert(sum(1 for line in new) > sum(1 for line in old))

          # Now rebuild generated apk.
          previous_apk = apk_dest
          for i in range(1, options.r8_compilation_steps):
            try:
              recompiled_apk_dest = os.path.join(
                  checkout_dir, 'out', shrinker, 'app-release-{}.apk'.format(i))
              RebuildAppWithShrinker(
                  previous_apk, recompiled_apk_dest, ext_proguard_config_file,
                  shrinker)
              recompilation_result = {
                'apk_dest': recompiled_apk_dest,
                'build_status': 'success',
                'dex_size': ComputeSizeOfDexFilesInApk(recompiled_apk_dest)
              }
              if options.monkey:
                recompilation_result['monkey_status'] = 'success' if RunMonkey(
                    app, config, options, recompiled_apk_dest) else 'failed'
              recompilation_results.append(recompilation_result)
              previous_apk = recompiled_apk_dest
            except Exception as e:
              warn('Failed to recompile {} with {}'.format(app, shrinker))
              recompilation_results.append({ 'build_status': 'failed' })
              break
          result['recompilation_results'] = recompilation_results

      result_per_shrinker[shrinker] = result

  return result_per_shrinker

def BuildAppWithShrinker(
    app, config, shrinker, checkout_dir, out_dir, options, env_vars=None):
  print()
  print('Building {} with {}'.format(app, shrinker))

  # Add/remove 'r8.jar' from top-level build.gradle.
  if options.disable_tot:
    as_utils.remove_r8_dependency(checkout_dir)
  else:
    as_utils.add_r8_dependency(checkout_dir, IsMinifiedR8(shrinker))

  app_module = config.get('app_module', 'app')
  archives_base_name = config.get('archives_base_name', app_module)
  flavor = config.get('flavor')

  if not os.path.exists(out_dir):
    os.makedirs(out_dir)

  # Set -printconfiguration in Proguard rules.
  proguard_config_dest = os.path.abspath(
      os.path.join(out_dir, 'proguard-rules.pro'))
  as_utils.SetPrintConfigurationDirective(
      app, config, checkout_dir, proguard_config_dest)

  env = os.environ.copy()
  env['ANDROID_HOME'] = android_home
  env['JAVA_OPTS'] = '-ea:com.android.tools.r8...'
  if env_vars:
    env.update(env_vars)

  releaseTarget = config.get('releaseTarget')
  if not releaseTarget:
    releaseTarget = app_module + ':' + 'assemble' + (
        flavor.capitalize() if flavor else '') + 'Release'

  # Value for property android.enableR8.
  enableR8 = 'r8' in shrinker
  # Value for property android.enableR8.fullMode.
  enableR8FullMode = shrinker == 'r8full' or shrinker == 'r8full-minified'
  # Build gradlew command line.
  cmd = ['./gradlew', '--no-daemon', 'clean', releaseTarget,
         '--profile', '--stacktrace',
         '-Pandroid.enableR8=' + str(enableR8).lower(),
         '-Pandroid.enableR8.fullMode=' + str(enableR8FullMode).lower()]
  if options.gradle_flags:
    cmd.extend(options.gradle_flags.split(' '))

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
    apk_dest = os.path.join(out_dir, signed_apk_name)
    as_utils.MoveFile(signed_apk, apk_dest)
  else:
    apk_dest = os.path.join(out_dir, unsigned_apk_name)
    as_utils.MoveFile(unsigned_apk, apk_dest)

  assert IsBuiltWithR8(apk_dest) == ('r8' in shrinker), (
      'Unexpected marker in generated APK for {}'.format(shrinker))

  profile_dest_dir = os.path.join(out_dir, 'profile')
  as_utils.MoveProfileReportTo(profile_dest_dir, stdout)

  return (apk_dest, profile_dest_dir, proguard_config_dest)

def RebuildAppWithShrinker(apk, apk_dest, proguard_config_file, shrinker):
  assert 'r8' in shrinker
  assert apk_dest.endswith('.apk')

  # Compile given APK with shrinker to temporary zip file.
  api = 28 # TODO(christofferqa): Should be the one from build.gradle
  android_jar = os.path.join(utils.REPO_ROOT, utils.ANDROID_JAR.format(api=api))
  r8_jar = utils.R8LIB_JAR if IsMinifiedR8(shrinker) else utils.R8_JAR
  zip_dest = apk_dest[:-4] + '.zip'

  cmd = ['java', '-ea:com.android.tools.r8...', '-cp', r8_jar,
      'com.android.tools.r8.R8', '--release', '--pg-conf', proguard_config_file,
      '--lib', android_jar, '--output', zip_dest, apk]
  utils.PrintCmd(cmd)

  subprocess.check_output(cmd)

  # Make a copy of the given APK, move the newly generated dex files into the
  # copied APK, and then sign the APK.
  apk_masseur.masseur(apk, dex=zip_dest, out=apk_dest)

def RunMonkey(app, config, options, apk_dest):
  if not WaitForEmulator():
    return False

  UninstallApkOnEmulator(app, config)
  InstallApkOnEmulator(apk_dest)

  app_id = config.get('app_id')
  number_of_events_to_generate = options.monkey_events

  # Intentionally using a constant seed such that the monkey generates the same
  # event sequence for each shrinker.
  random_seed = 42

  cmd = ['adb', 'shell', 'monkey', '-p', app_id, '-s', str(random_seed),
      str(number_of_events_to_generate)]
  utils.PrintCmd(cmd)

  try:
    stdout = subprocess.check_output(cmd)
    succeeded = (
        'Events injected: {}'.format(number_of_events_to_generate) in stdout)
  except subprocess.CalledProcessError as e:
    succeeded = False

  UninstallApkOnEmulator(app, config)

  return succeeded

def LogResultsForApps(result_per_shrinker_per_app, options):
  for app, result_per_shrinker in result_per_shrinker_per_app.iteritems():
    LogResultsForApp(app, result_per_shrinker, options)

def LogResultsForApp(app, result_per_shrinker, options):
  print(app + ':')

  if result_per_shrinker.get('status') != 'success':
    error_message = result_per_shrinker.get('error_message')
    print('  skipped ({})'.format(error_message))
    return

  proguard_result = result_per_shrinker.get('proguard', {})
  proguard_dex_size = float(proguard_result.get('dex_size', -1))
  proguard_duration = sum(proguard_result.get('profile', {}).values())

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
      msg = '    dex size: {}'.format(dex_size)
      if dex_size != proguard_dex_size and proguard_dex_size >= 0:
        msg = '{} ({}, {})'.format(
            msg, dex_size - proguard_dex_size,
            PercentageDiffAsString(proguard_dex_size, dex_size))
        success(msg) if dex_size < proguard_dex_size else warn(msg)
      else:
        print(msg)

      profile = result.get('profile')
      duration = sum(profile.values())
      msg = '    performance: {}s'.format(duration)
      if duration != proguard_duration and proguard_duration > 0:
        msg = '{} ({}s, {})'.format(
            msg, duration - proguard_duration,
            PercentageDiffAsString(proguard_duration, duration))
        success(msg) if duration < proguard_duration else warn(msg)
      else:
        print(msg)
      if len(profile) >= 2:
        for task_name, task_duration in profile.iteritems():
          print('      {}: {}s'.format(task_name, task_duration))

      if options.monkey:
        monkey_status = result.get('monkey_status')
        if monkey_status != 'success':
          warn('    monkey: {}'.format(monkey_status))
        else:
          success('    monkey: {}'.format(monkey_status))
      recompilation_results = result.get('recompilation_results', [])
      i = 0
      for recompilation_result in recompilation_results:
        build_status = recompilation_result.get('build_status')
        if build_status != 'success':
          print('    recompilation #{}: {}'.format(i, build_status))
        else:
          dex_size = recompilation_result.get('dex_size')
          print('    recompilation #{}'.format(i))
          print('      dex size: {}'.format(dex_size))
          if options.monkey:
            monkey_status = recompilation_result.get('monkey_status')
            msg = '      monkey: {}'.format(monkey_status)
            if monkey_status == 'success':
              success(msg)
            elif monkey_status == 'skipped':
              print(msg)
            else:
              warn(msg)
        i += 1

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--app',
                    help='What app to run on',
                    choices=APPS.keys())
  result.add_option('--monkey',
                    help='Whether to install and run app(s) with monkey',
                    default=False,
                    action='store_true')
  result.add_option('--monkey_events',
                    help='Number of events that the monkey should trigger',
                    default=250,
                    type=int)
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
  result.add_option('--r8-compilation-steps', '--r8_compilation_steps',
                    help='Number of times R8 should be run on each app',
                    default=2,
                    type=int)
  result.add_option('--disable-tot', '--disable_tot',
                    help='Whether to disable the use of the ToT version of R8',
                    default=False,
                    action='store_true')
  result.add_option('--no-build', '--no_build',
                    help='Run without building ToT first (only when using ToT)',
                    default=False,
                    action='store_true')
  result.add_option('--gradle-flags', '--gradle_flags',
                    help='Flags to pass in to gradle')
  (options, args) = result.parse_args(argv)
  if options.disable_tot:
    # r8.jar is required for recompiling the generated APK
    options.r8_compilation_steps = 1
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

  LogResultsForApps(result_per_shrinker_per_app, options)

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
