#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import apk_masseur
import apk_utils
import golem
import gradle
import jdk
import json
import os
import optparse
import shutil
import subprocess
import sys
import time
import utils
import zipfile

import as_utils
import create_maven_release
import update_prebuilds_in_android

SHRINKERS = ['r8', 'r8-full', 'r8-nolib', 'r8-nolib-full', 'pg']
WORKING_DIR = os.path.join(utils.BUILD, 'opensource_apps')

if ('R8_BENCHMARK_DIR' in os.environ
    and os.path.isdir(os.environ['R8_BENCHMARK_DIR'])):
  WORKING_DIR = os.environ['R8_BENCHMARK_DIR']

# For running on Golem all APPS are bundled as an x20-dependency and then copied
# to WORKING_DIR. To update the app-bundle use 'run_on_as_app_x20_packager.py'.
APPS = {
  # 'app-name': {
  #     'git_repo': ...
  #     'revision': ...,
  #     'app_module': ... (default app)
  #     'archives_base_name': ... (default same as app_module)
  #     'flavor': ... (default no flavor)
  #     'releaseTarget': ... (default <app_module>:assemble<flavor>Release
  # },
  'AnExplorer': {
      'app_id': 'dev.dworks.apps.anexplorer.pro',
      'git_repo': 'https://github.com/christofferqa/AnExplorer',
      'revision': '365927477b8eab4052a1882d5e358057ae3dee4d',
      'flavor': 'googleMobilePro',
      'signed-apk-name': 'AnExplorer-googleMobileProRelease-4.0.3.apk',
      'min_sdk': 17
  },
  'AntennaPod': {
      'app_id': 'de.danoeh.antennapod',
      'git_repo': 'https://github.com/christofferqa/AntennaPod.git',
      'revision': '77e94f4783a16abe9cc5b78dc2d2b2b1867d8c06',
      'flavor': 'play',
      'min_sdk': 14,
      'compile_sdk': 26
  },
  'apps-android-wikipedia': {
      'app_id': 'org.wikipedia',
      'git_repo': 'https://github.com/christofferqa/apps-android-wikipedia',
      'revision': '686e8aa5682af8e6a905054b935dd2daa57e63ee',
      'flavor': 'prod',
      'signed-apk-name': 'app-prod-universal-release.apk',
  },
  'chanu': {
      'app_id': 'com.chanapps.four.activity',
      'git_repo': 'https://github.com/mkj-gram/chanu.git',
      'revision': '04ade1e9c33d707f0850d5eb9d6fa5e8af814a26',
  },
  'friendlyeats-android': {
      'app_id': 'com.google.firebase.example.fireeats',
      'git_repo': 'https://github.com/christofferqa/friendlyeats-android.git',
      'revision': '10091fa0ec37da12e66286559ad1b6098976b07b',
  },
  'Instabug-Android': {
      'app_id': 'com.example.instabug',
      'git_repo': 'https://github.com/christofferqa/Instabug-Android.git',
      'revision': 'b8df78c96630a6537fbc07787b4990afc030cc0f'
  },
  'KISS': {
      'app_id': 'fr.neamar.kiss',
      'git_repo': 'https://github.com/christofferqa/KISS',
      'revision': '093da9ee0512e67192f62951c45a07a616fc3224',
  },
  'materialistic': {
      'app_id': 'io.github.hidroh.materialistic',
      'git_repo': 'https://github.com/christofferqa/materialistic',
      'revision': '2b2b2ee25ce9e672d5aab1dc90a354af1522b1d9',
  },
  'Minimal-Todo': {
      'app_id': 'com.avjindersinghsekhon.minimaltodo',
      'git_repo': 'https://github.com/christofferqa/Minimal-Todo',
      'revision': '9d8c73746762cd376b718858ec1e8783ca07ba7c',
  },
  'NewPipe': {
      'app_id': 'org.schabi.newpipe',
      'git_repo': 'https://github.com/christofferqa/NewPipe',
      'revision': 'ed543099c7823be00f15d9340f94bdb7cb37d1e6',
  },
  'rover-android': {
      'app_id': 'io.rover.app.debug',
      'app_module': 'debug-app',
      'git_repo': 'https://github.com/mkj-gram/rover-android.git',
      'revision': '859af82ba56fe9035ae9949156c7a88e6012d930',
  },
  'Signal-Android': {
      'app_id': 'org.thoughtcrime.securesms',
      'app_module': '',
      'flavor': 'play',
      'git_repo': 'https://github.com/mkj-gram/Signal-Android.git',
      'main_dex_rules': 'multidex-config.pro',
      'revision': 'a45d0c1fed20fa39e8b9445fe7790326f46b3166',
      'releaseTarget': 'assemblePlayRelease',
      'signed-apk-name': 'Signal-play-release-4.32.7.apk',
  },
  'Simple-Calendar': {
      'app_id': 'com.simplemobiletools.calendar.pro',
      'git_repo': 'https://github.com/christofferqa/Simple-Calendar',
      'revision': '82dad8c203eea5a0f0ddb513506d8f1de986ef2b',
      'signed-apk-name': 'calendar-release.apk'
  },
  'sqldelight': {
      'app_id': 'com.example.sqldelight.hockey',
      'git_repo': 'https://github.com/christofferqa/sqldelight.git',
      'revision': '2e67a1126b6df05e4119d1e3a432fde51d76cdc8',
      'app_module': 'sample/android',
      'archives_base_name': 'android',
      'min_sdk': 14,
      'compile_sdk': 28,
  },
  'tachiyomi': {
      'app_id': 'eu.kanade.tachiyomi',
      'git_repo': 'https://github.com/sgjesse/tachiyomi.git',
      'revision': 'b15d2fe16864645055af6a745a62cc5566629798',
      'flavor': 'standard',
      'releaseTarget': 'app:assembleRelease',
      'min_sdk': 16
  },
  'tivi': {
      'app_id': 'app.tivi',
      'git_repo': 'https://github.com/sgjesse/tivi.git',
      'revision': '25c52e3593e7c98da4e537b49b29f6f67f88754d',
      'min_sdk': 23,
      'compile_sdk': 28,
  },
  'Tusky': {
      'app_id': 'com.keylesspalace.tusky',
      'git_repo': 'https://github.com/mkj-gram/Tusky.git',
      'revision': 'b794f3ab90388add98461ffe70edb65c39351c33',
      'flavor': 'blue'
  },
  'Vungle-Android-SDK': {
      'app_id': 'com.publisher.vungle.sample',
      'git_repo': 'https://github.com/mkj-gram/Vungle-Android-SDK.git',
      'revision': '3e231396ea7ce97b2655e03607497c75730e45f6',
  },
  # This does not build yet.
  'muzei': {
      'git_repo': 'https://github.com/sgjesse/muzei.git',
      'revision': 'bed2a5f79c6e08b0a21e3e3f9242232d0848ef74',
      'app_module': 'main',
      'archives_base_name': 'muzei',
      'skip': True,
  },
}

# TODO(christofferqa): Do not rely on 'emulator-5554' name
emulator_id = 'emulator-5554'

def ComputeSizeOfDexFilesInApk(apk):
  dex_size = 0
  z = zipfile.ZipFile(apk, 'r')
  for filename in z.namelist():
    if filename.endswith('.dex'):
      dex_size += z.getinfo(filename).file_size
  return dex_size

def ExtractMarker(apk, temp_dir, options):
  r8_jar = os.path.join(temp_dir, 'r8.jar')

  # Use the copy of r8.jar if it is there.
  if os.path.isfile(r8_jar):
    cmd = [jdk.GetJavaExecutable(), '-ea', '-jar', r8_jar, 'extractmarker', apk]
  else:
    script = os.path.join(utils.TOOLS_DIR, 'extractmarker.py')
    cmd = ['python', script, apk]

  utils.PrintCmd(cmd, quiet=options.quiet)
  stdout = subprocess.check_output(cmd)

  # Return the last line.
  lines = stdout.strip().splitlines()
  assert len(lines) >= 1
  return lines[-1]

def CheckIsBuiltWithExpectedR8(apk, temp_dir, options):
  marker = ExtractMarker(apk, temp_dir, options)
  expected_version = (
      options.version
      if options.version
      else create_maven_release.determine_version())
  if marker.startswith('~~R8'):
    actual_version = json.loads(marker[4:]).get('version')
    if actual_version == expected_version:
      return True
  raise Exception(
      'Expected APK to be built with R8 version {} (was: {})'.format(
          expected_version, marker))

def IsMinifiedR8(shrinker):
  return 'nolib' not in shrinker

def IsTrackedByGit(file):
  return subprocess.check_output(['git', 'ls-files', file]).strip() != ''

def GitClone(git_url, revision, checkout_dir, quiet):
  result = subprocess.check_output(
      ['git', 'clone', git_url, checkout_dir]).strip()
  head_rev = utils.get_HEAD_sha1_for_checkout(checkout_dir)
  if revision == head_rev:
    return result
  warn('Target revision is not head in {}.'.format(checkout_dir))
  with utils.ChangedWorkingDirectory(checkout_dir, quiet=quiet):
    subprocess.check_output(['git', 'reset', '--hard', revision])
  return result

def GitCheckout(file):
  return subprocess.check_output(['git', 'checkout', file]).strip()

def InstallApkOnEmulator(apk_dest, options):
  cmd = ['adb', '-s', emulator_id, 'install', '-r', '-d', apk_dest]
  if options.quiet:
    with open(os.devnull, 'w') as devnull:
      subprocess.check_call(cmd, stdout=devnull)
  else:
    subprocess.check_call(cmd)

def PercentageDiffAsString(before, after):
  if after < before:
    return '-' + str(round((1.0 - after / before) * 100)) + '%'
  else:
    return '+' + str(round((after - before) / before * 100)) + '%'

def UninstallApkOnEmulator(app, config, options):
  app_id = config.get('app_id')
  process = subprocess.Popen(
      ['adb', '-s', emulator_id, 'uninstall', app_id],
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

def GetResultsForApp(app, config, options, temp_dir):
  # Checkout and build in the build directory.
  checkout_dir = os.path.join(WORKING_DIR, app)

  result = {}

  if not os.path.exists(checkout_dir) and not options.golem:
    with utils.ChangedWorkingDirectory(WORKING_DIR, quiet=options.quiet):
      GitClone(
          config['git_repo'], config['revision'], checkout_dir, options.quiet)

  checkout_rev = utils.get_HEAD_sha1_for_checkout(checkout_dir)
  if config['revision'] != checkout_rev:
    msg = 'Checkout is not target revision for {} in {}.'.format(
        app, checkout_dir)
    if options.ignore_versions:
      warn(msg)
    else:
      raise Exception(msg)

  result['status'] = 'success'

  result_per_shrinker = BuildAppWithSelectedShrinkers(
      app, config, options, checkout_dir, temp_dir)
  for shrinker, shrinker_result in result_per_shrinker.iteritems():
    result[shrinker] = shrinker_result

  return result

def BuildAppWithSelectedShrinkers(app, config, options, checkout_dir, temp_dir):
  result_per_shrinker = {}

  with utils.ChangedWorkingDirectory(checkout_dir, quiet=options.quiet):
    for shrinker in options.shrinker:
      apk_dest = None

      result = {}
      try:
        out_dir = os.path.join(checkout_dir, 'out', shrinker)
        (apk_dest, profile_dest_dir, proguard_config_file) = \
            BuildAppWithShrinker(app, config, shrinker, checkout_dir, out_dir,
                temp_dir, options)
        dex_size = ComputeSizeOfDexFilesInApk(apk_dest)
        result['apk_dest'] = apk_dest
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
          (apk_dest, profile_dest_dir, ext_proguard_config_file) = \
              BuildAppWithShrinker(app, config, shrinker, checkout_dir, out_dir,
                  temp_dir, options, keepRuleSynthesisForRecompilation=True)
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
              assert(
                  sum(1 for line in new
                      if line.strip() and '-printconfiguration' not in line)
                  >
                  sum(1 for line in old
                      if line.strip() and '-printconfiguration' not in line))

          # Extract min-sdk and target-sdk
          (min_sdk, compile_sdk) = as_utils.GetMinAndCompileSdk(app, config,
              checkout_dir, apk_dest)

          # Now rebuild generated apk.
          previous_apk = apk_dest

          # We may need main dex rules when re-compiling with R8 as standalone.
          main_dex_rules = None
          if config.get('main_dex_rules'):
            main_dex_rules = os.path.join(
                checkout_dir, config.get('main_dex_rules'))

          for i in range(1, options.r8_compilation_steps):
            try:
              recompiled_apk_dest = os.path.join(
                  checkout_dir, 'out', shrinker, 'app-release-{}.apk'.format(i))
              RebuildAppWithShrinker(
                  app, previous_apk, recompiled_apk_dest,
                  ext_proguard_config_file, shrinker, min_sdk, compile_sdk,
                  options, temp_dir, main_dex_rules)
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

  if not options.app:
    print('')
    LogResultsForApp(app, result_per_shrinker, options)
    print('')

  return result_per_shrinker

def BuildAppWithShrinker(
    app, config, shrinker, checkout_dir, out_dir, temp_dir, options,
    keepRuleSynthesisForRecompilation=False):
  print('Building {} with {}{}'.format(
      app,
      shrinker,
      ' for recompilation' if keepRuleSynthesisForRecompilation else ''))

  # Add settings.gradle file if it is not present to prevent gradle from finding
  # the settings.gradle file in the r8 root when apps are placed under
  # $R8/build.
  as_utils.add_settings_gradle(checkout_dir, app)

  # Add 'r8.jar' to top-level build.gradle.
  as_utils.add_r8_dependency(checkout_dir, temp_dir, IsMinifiedR8(shrinker))

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

  env = {}
  env['ANDROID_HOME'] = utils.ANDROID_HOME
  env['JAVA_OPTS'] = '-ea:com.android.tools.r8...'

  releaseTarget = config.get('releaseTarget')
  if not releaseTarget:
    releaseTarget = app_module.replace('/', ':') + ':' + 'assemble' + (
        flavor.capitalize() if flavor else '') + 'Release'

  # Value for property android.enableR8.
  enableR8 = 'r8' in shrinker
  # Value for property android.enableR8.fullMode.
  enableR8FullMode = shrinker == 'r8-full' or shrinker == 'r8-nolib-full'
  # Build gradlew command line.
  cmd = ['./gradlew', '--no-daemon', 'clean', releaseTarget,
         '--profile', '--stacktrace',
         '-Pandroid.enableR8=' + str(enableR8).lower(),
         '-Pandroid.enableR8.fullMode=' + str(enableR8FullMode).lower()]
  if keepRuleSynthesisForRecompilation:
    cmd.append('-Dcom.android.tools.r8.keepRuleSynthesisForRecompilation=true')
  if options.gradle_flags:
    cmd.extend(options.gradle_flags.split(' '))

  stdout = utils.RunCmd(cmd, env, quiet=options.quiet)

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
      apk_utils.sign_with_apksigner(
          unsigned_apk,
          signed_apk,
          options.keystore,
          options.keystore_password,
          quiet=options.quiet)

  if os.path.isfile(signed_apk):
    apk_dest = os.path.join(out_dir, signed_apk_name)
    as_utils.MoveFile(signed_apk, apk_dest, quiet=options.quiet)
  else:
    apk_dest = os.path.join(out_dir, unsigned_apk_name)
    as_utils.MoveFile(unsigned_apk, apk_dest, quiet=options.quiet)

  assert ('r8' not in shrinker
      or CheckIsBuiltWithExpectedR8(apk_dest, temp_dir, options))

  profile_dest_dir = os.path.join(out_dir, 'profile')
  as_utils.MoveProfileReportTo(profile_dest_dir, stdout, quiet=options.quiet)

  return (apk_dest, profile_dest_dir, proguard_config_dest)

def RebuildAppWithShrinker(
    app, apk, apk_dest, proguard_config_file, shrinker, min_sdk, compile_sdk,
    options, temp_dir, main_dex_rules):
  assert 'r8' in shrinker
  assert apk_dest.endswith('.apk')

  print('Rebuilding {} with {}'.format(app, shrinker))

  # Compile given APK with shrinker to temporary zip file.
  android_jar = utils.get_android_jar(compile_sdk)
  r8_jar = os.path.join(
      temp_dir, 'r8lib.jar' if IsMinifiedR8(shrinker) else 'r8.jar')
  zip_dest = apk_dest[:-4] + '.zip'

  # TODO(christofferqa): Entry point should be CompatProguard if the shrinker
  # is 'r8'.
  entry_point = 'com.android.tools.r8.R8'

  cmd = [jdk.GetJavaExecutable(), '-ea:com.android.tools.r8...', '-cp', r8_jar,
      entry_point, '--release', '--min-api', str(min_sdk), '--pg-conf',
      proguard_config_file, '--lib', android_jar, '--output', zip_dest, apk]

  for android_optional_jar in utils.get_android_optional_jars(compile_sdk):
    cmd.append('--lib')
    cmd.append(android_optional_jar)

  if main_dex_rules:
    cmd.append('--main-dex-rules')
    cmd.append(main_dex_rules)

  utils.RunCmd(cmd, quiet=options.quiet)

  # Make a copy of the given APK, move the newly generated dex files into the
  # copied APK, and then sign the APK.
  apk_masseur.masseur(
      apk, dex=zip_dest, resources='META-INF/services/*', out=apk_dest,
      quiet=options.quiet)

def RunMonkey(app, config, options, apk_dest):
  if not WaitForEmulator():
    return False

  UninstallApkOnEmulator(app, config, options)
  InstallApkOnEmulator(apk_dest, options)

  app_id = config.get('app_id')
  number_of_events_to_generate = options.monkey_events

  # Intentionally using a constant seed such that the monkey generates the same
  # event sequence for each shrinker.
  random_seed = 42

  cmd = ['adb', 'shell', 'monkey', '-p', app_id, '-s', str(random_seed),
      str(number_of_events_to_generate)]

  try:
    stdout = utils.RunCmd(cmd, quiet=options.quiet)
    succeeded = (
        'Events injected: {}'.format(number_of_events_to_generate) in stdout)
  except subprocess.CalledProcessError as e:
    succeeded = False

  UninstallApkOnEmulator(app, config, options)

  return succeeded

def LogResultsForApps(result_per_shrinker_per_app, options):
  print('')
  for app, result_per_shrinker in sorted(
      result_per_shrinker_per_app.iteritems(), key=lambda s: s[0].lower()):
    LogResultsForApp(app, result_per_shrinker, options)

def LogResultsForApp(app, result_per_shrinker, options):
  if options.print_dexsegments:
    LogSegmentsForApp(app, result_per_shrinker, options)
  else:
    LogComparisonResultsForApp(app, result_per_shrinker, options)

def LogSegmentsForApp(app, result_per_shrinker, options):
  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    result = result_per_shrinker[shrinker];
    benchmark_name = '{}-{}'.format(options.print_dexsegments, app)
    utils.print_dexsegments(benchmark_name, [result.get('apk_dest')])
    duration = sum(result.get('profile').values())
    print('%s-Total(RunTimeRaw): %s ms' % (benchmark_name, duration * 1000))
    print('%s-Total(CodeSize): %s' % (benchmark_name, result.get('dex_size')))


def LogComparisonResultsForApp(app, result_per_shrinker, options):
  print(app + ':')

  if result_per_shrinker.get('status', 'success') != 'success':
    error_message = result_per_shrinker.get('error_message')
    print('  skipped ({})'.format(error_message))
    return

  proguard_result = result_per_shrinker.get('pg', {})
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
  result.add_option('--download-only', '--download_only',
                    help='Whether to download apps without any compilation',
                    default=False,
                    action='store_true')
  result.add_option('--golem',
                    help='Running on golem, do not download',
                    default=False,
                    action='store_true')
  result.add_option('--gradle-flags', '--gradle_flags',
                    help='Flags to pass in to gradle')
  result.add_option('--ignore-versions', '--ignore_versions',
                    help='Allow checked-out app to differ in revision from '
                         'pinned',
                    default=False,
                    action='store_true')
  result.add_option('--keystore',
                    help='Path to app.keystore',
                    default='app.keystore')
  result.add_option('--keystore-password', '--keystore_password',
                    help='Password for app.keystore',
                    default='android')
  result.add_option('--monkey',
                    help='Whether to install and run app(s) with monkey',
                    default=False,
                    action='store_true')
  result.add_option('--monkey_events',
                    help='Number of events that the monkey should trigger',
                    default=250,
                    type=int)
  result.add_option('--no-build', '--no_build',
                    help='Run without building ToT first (only when using ToT)',
                    default=False,
                    action='store_true')
  result.add_option('--quiet',
                    help='Disable verbose logging',
                    default=False,
                    action='store_true')
  result.add_option('--print-dexsegments',
                    metavar='BENCHMARKNAME',
                    help='Print the sizes of individual dex segments as ' +
                         '\'<BENCHMARKNAME>-<APP>-<segment>(CodeSize): '
                         '<bytes>\'')
  result.add_option('--r8-compilation-steps', '--r8_compilation_steps',
                    help='Number of times R8 should be run on each app',
                    default=2,
                    type=int)
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
  if options.shrinker:
    for shrinker in options.shrinker:
      assert shrinker in SHRINKERS
  else:
    options.shrinker = [shrinker for shrinker in SHRINKERS]
  if options.version:
    # No need to build R8 if a specific release version should be used.
    options.no_build = True
    if 'r8-nolib' in options.shrinker:
      warn('Skipping shrinker r8-nolib because a specific release version '
          + 'of r8 was specified')
      options.shrinker.remove('r8-nolib')
    if 'r8-nolib-full' in options.shrinker:
      warn('Skipping shrinker r8-nolib-full because a specific release version '
          + 'of r8 was specified')
      options.shrinker.remove('r8-nolib-full')
  return (options, args)

def download_apps(quiet):
  # Download apps and place in build
  with utils.ChangedWorkingDirectory(WORKING_DIR):
    for app, config in APPS.iteritems():
      app_dir = os.path.join(WORKING_DIR, app)
      if not os.path.exists(app_dir):
        GitClone(config['git_repo'], config['revision'], app_dir, quiet)


def main(argv):
  (options, args) = ParseOptions(argv)

  if options.golem:
    golem.link_third_party()
    if os.path.exists(WORKING_DIR):
      shutil.rmtree(WORKING_DIR)
    shutil.copytree(utils.OPENSOURCE_APPS_FOLDER, WORKING_DIR)

  if not os.path.exists(WORKING_DIR):
    os.makedirs(WORKING_DIR)

  if options.download_only:
    download_apps(options.quiet)
    return

  with utils.TempDir() as temp_dir:
    if not options.no_build or options.golem:
      gradle.RunGradle(['r8', 'r8lib'])

    if options.version:
      # Download r8-<version>.jar from
      # http://storage.googleapis.com/r8-releases/raw/.
      target = 'r8-{}.jar'.format(options.version)
      update_prebuilds_in_android.download_version(
          temp_dir, 'com/android/tools/r8/' + options.version, target)
      as_utils.MoveFile(
          os.path.join(temp_dir, target), os.path.join(temp_dir, 'r8lib.jar'),
          quiet=options.quiet)
    else:
      # Make a copy of r8.jar and r8lib.jar such that they stay the same for
      # the entire execution of this script.
      if 'r8-nolib' in options.shrinker:
        assert os.path.isfile(utils.R8_JAR), 'Cannot build without r8.jar'
        shutil.copyfile(utils.R8_JAR, os.path.join(temp_dir, 'r8.jar'))
      if 'r8' in options.shrinker:
        assert os.path.isfile(utils.R8LIB_JAR), 'Cannot build without r8lib.jar'
        shutil.copyfile(utils.R8LIB_JAR, os.path.join(temp_dir, 'r8lib.jar'))

    result_per_shrinker_per_app = {}

    if options.app:
      result_per_shrinker_per_app[options.app] = GetResultsForApp(
          options.app, APPS.get(options.app), options, temp_dir)
    else:
      for app, config in sorted(APPS.iteritems(), key=lambda s: s[0].lower()):
        if not config.get('skip', False):
          result_per_shrinker_per_app[app] = GetResultsForApp(
              app, config, options, temp_dir)

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
