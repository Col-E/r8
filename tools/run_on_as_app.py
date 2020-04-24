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
import signal
import subprocess
import sys
import time
import utils
import zipfile
from xml.dom import minidom
from datetime import datetime

import as_utils
import update_prebuilds_in_android
import download_all_benchmark_dependencies

SHRINKERS = ['r8', 'r8-full', 'r8-nolib', 'r8-nolib-full', 'pg']
WORKING_DIR = os.path.join(utils.BUILD, 'opensource_apps')

GRADLE_USER_HOME = '.gradle_user_home'

if ('R8_BENCHMARK_DIR' in os.environ
    and os.path.isdir(os.environ['R8_BENCHMARK_DIR'])):
  WORKING_DIR = os.environ['R8_BENCHMARK_DIR']

class Repo(object):
  def __init__(self, fields):
    self.__dict__ = fields

    # If there is only one app in this repository, then give the app the same
    # name as the repository, if it does not already have one.
    if len(self.apps) == 1:
      app = self.apps[0]
      if not app.name:
        app.name = self.name

class App(object):
  def __init__(self, fields):
    module = fields.get('module', 'app')
    defaults = {
      'archives_base_name': module,
      'build_dir': 'build',
      'compile_sdk': None,
      'dir': None,
      'flavor': None,
      'has_instrumentation_tests': False,
      'main_dex_rules': None,
      'module': module,
      'min_sdk': None,
      'name': None,
      'releaseTarget': None,
      'signed_apk_name': None,
      'skip': False,
      'has_lint_task': True
    }
    self.__dict__ = dict(defaults.items() + fields.items())

# For running on Golem all third-party repositories are bundled as an x20-
# dependency and then copied to WORKING_DIR. To update the app-bundle use
# 'run_on_as_app_x20_packager.py'.
# For showing benchmark data, also include the app in appSegmentBenchmarks in
# the file <golem_repo>/config/r8/benchmarks.dart.
APP_REPOSITORIES = [
  # ...
  # Repo({
  #     'name': ...,
  #     'url': ...,
  #     'revision': ...,
  #     'apps': [
  #         {
  #             'id': ...,
  #             'dir': ...,
  #             'module': ... (default app)
  #             'name': ...,
  #             'archives_base_name': ... (default same as module)
  #             'flavor': ... (default no flavor)
  #             'releaseTarget': ... (default <module>:assemble<flavor>Release
  #         },
  #         ...
  #     ]
  # }),
  # ...
  Repo({
      'name': 'android-suite',
      'url': 'https://github.com/christofferqa/android-suite',
      'revision': '46c96f214711cf6cdcb72cc0c94520ef418e3739',
      'apps': [
          App({
              'id': 'com.numix.calculator',
              'dir': 'Calculator',
              'name': 'numix-calculator',
              'has_instrumentation_tests': True,
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'AnExplorer',
      'url': 'https://github.com/christofferqa/AnExplorer',
      'revision': '365927477b8eab4052a1882d5e358057ae3dee4d',
      'apps': [
          App({
              'id': 'dev.dworks.apps.anexplorer.pro',
              'flavor': 'googleMobilePro',
              'signed_apk_name': 'AnExplorer-googleMobileProRelease-4.0.3.apk',
              'min_sdk': 17
          })
      ]
  }),
  Repo({
      'name': 'AntennaPod',
      'url': 'https://github.com/christofferqa/AntennaPod.git',
      'revision': '77e94f4783a16abe9cc5b78dc2d2b2b1867d8c06',
      'apps': [
          App({
              'id': 'de.danoeh.antennapod',
              'flavor': 'play',
              'min_sdk': 14,
              'compile_sdk': 26
          })
      ]
  }),
  Repo({
    'name': 'applymapping',
    'url': 'https://github.com/mkj-gram/applymapping',
    'revision': 'e3ae14b8c16fa4718e5dea8f7ad00937701b3c48',
    'apps': [
      App({
        'id': 'com.example.applymapping',
        'has_instrumentation_tests': True
      })
    ]
  }),
  Repo({
      'name': 'apps-android-wikipedia',
      'url': 'https://github.com/christofferqa/apps-android-wikipedia',
      'revision': '686e8aa5682af8e6a905054b935dd2daa57e63ee',
      'apps': [
          App({
              'id': 'org.wikipedia',
              'flavor': 'prod',
              'signed_apk_name': 'app-prod-universal-release.apk'
          })
      ]
  }),
  Repo({
      'name': 'chanu',
      'url': 'https://github.com/mkj-gram/chanu.git',
      'revision': '6e53458f167b6d78398da60c20fd0da01a232617',
      'apps': [
          App({
              'id': 'com.chanapps.four.activity',
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'friendlyeats-android',
      'url': 'https://github.com/christofferqa/friendlyeats-android.git',
      'revision': '10091fa0ec37da12e66286559ad1b6098976b07b',
      'apps': [
          App({
              'id': 'com.google.firebase.example.fireeats'
          })
      ]
  }),
  Repo({
      'name': 'googlesamples',
      'url': 'https://github.com/christofferqa/android-sunflower.git',
      'revision': 'df0a082a0bcbeae253817e13daca3c7a7c54f67a',
      'apps': [
          App({
              'id': 'com.google.samples.apps.sunflower',
              'name': 'android-sunflower',
              'min_sdk': 19,
              'compile_sdk': 28
          })
      ]
  }),
  Repo({
      'name': 'Instabug-Android',
      'url': 'https://github.com/christofferqa/Instabug-Android.git',
      'revision': 'b8df78c96630a6537fbc07787b4990afc030cc0f',
      'apps': [
          App({
             'id': 'com.example.instabug'
          })
      ]
  }),
  Repo({
      'name': 'iosched',
      'url': 'https://github.com/christofferqa/iosched.git',
      'revision': '581cbbe2253711775dbccb753cdb53e7e506cb02',
      'apps': [
          App({
              'id': 'com.google.samples.apps.iosched',
              'module': 'mobile',
              'min_sdk': 21,
              'compile_sdk': 29,
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'KISS',
      'url': 'https://github.com/christofferqa/KISS',
      'revision': '093da9ee0512e67192f62951c45a07a616fc3224',
      'apps': [
          App({
              'id': 'fr.neamar.kiss'
          })
      ]
  }),
  Repo({
      'name': 'materialistic',
      'url': 'https://github.com/christofferqa/materialistic',
      'revision': '2b2b2ee25ce9e672d5aab1dc90a354af1522b1d9',
      'apps': [
          App({
              'id': 'io.github.hidroh.materialistic'
          })
      ]
  }),
  Repo({
      'name': 'Minimal-Todo',
      'url': 'https://github.com/christofferqa/Minimal-Todo',
      'revision': '9d8c73746762cd376b718858ec1e8783ca07ba7c',
      'apps': [
          App({
              'id': 'com.avjindersinghsekhon.minimaltodo'
          })
      ]
  }),
  Repo({
      'name': 'muzei',
      'url': 'https://github.com/sgjesse/muzei.git',
      'revision': 'a1f1d9b119faa0db09b6bbffe2318c4ec3679418',
      'apps': [
          App({
              'id': 'net.nurik.roman.muzei',
              'module': 'main',
              'archives_base_name': 'muzei',
              'compile_sdk': 28,
          })
      ]
  }),
  Repo({
      'name': 'NewPipe',
      'url': 'https://github.com/christofferqa/NewPipe',
      'revision': 'ed543099c7823be00f15d9340f94bdb7cb37d1e6',
      'apps': [
          App({
              'id': 'org.schabi.newpipe',
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'rover-android',
      'url': 'https://github.com/mkj-gram/rover-android.git',
      'revision': 'a5e155a1ed7d19b1cecd9a7b075e2852623a06bf',
      'apps': [
          App({
              'id': 'io.rover.app.debug',
              'module': 'debug-app'
          })
      ]
  }),
  Repo({
      'name': 'santa-tracker-android',
      'url': 'https://github.com/christofferqa/santa-tracker-android.git',
      'revision': '8dee74be7d9ee33c69465a07088c53087d24a6dd',
      'apps': [
          App({
              'id': 'com.google.android.apps.santatracker',
              'module': 'santa-tracker',
              'min_sdk': 21,
              'compile_sdk': 28
          })
      ]
  }),
  Repo({
      'name': 'Signal-Android',
      'url': 'https://github.com/mkj-gram/Signal-Android.git',
      'revision': 'cd542cab9bf860e71504ecb1caaf0a8476ba3989',
      'apps': [
          App({
              'id': 'org.thoughtcrime.securesms',
              'module': '',
              'flavor': 'play',
              'main_dex_rules': 'multidex-config.pro',
              'signed_apk_name': 'Signal-play-release-4.32.7.apk'
          })
      ]
  }),
  Repo({
      'name': 'Simple-Calendar',
      'url': 'https://github.com/christofferqa/Simple-Calendar',
      'revision': '82dad8c203eea5a0f0ddb513506d8f1de986ef2b',
      'apps': [
          App({
              'id': 'com.simplemobiletools.calendar.pro',
              'signed_apk_name': 'calendar-release.apk',
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'Simple-Camera',
      'url': 'https://github.com/jsjeon/Simple-Camera',
      'revision': '451fe188ab123e6956413b42e89839b44c05ac14',
      'apps': [
          App({
              'id': 'com.simplemobiletools.camera.pro',
              'signed_apk_name': 'camera-release.apk',
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'Simple-File-Manager',
      'url': 'https://github.com/jsjeon/Simple-File-Manager',
      'revision': '282b57d9e73f4d250cc844d8d73fd223509a141e',
      'apps': [
          App({
              'id': 'com.simplemobiletools.filemanager.pro',
              'signed_apk_name': 'file-manager-release.apk',
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'Simple-Gallery',
      'url': 'https://github.com/jsjeon/Simple-Gallery',
      'revision': '679125601eee7e057dfdfecd7bea6c4a6ac73ef9',
      'apps': [
          App({
              'id': 'com.simplemobiletools.gallery.pro',
              'signed_apk_name': 'gallery-release.apk',
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'sqldelight',
      'url': 'https://github.com/christofferqa/sqldelight.git',
      'revision': '2e67a1126b6df05e4119d1e3a432fde51d76cdc8',
      'apps': [
          App({
              'id': 'com.example.sqldelight.hockey',
              'module': 'sample/android',
              'archives_base_name': 'android',
              'min_sdk': 14,
              'compile_sdk': 28
          })
      ]
  }),
  Repo({
      'name': 'tachiyomi',
      'url': 'https://github.com/sgjesse/tachiyomi.git',
      'revision': 'b15d2fe16864645055af6a745a62cc5566629798',
      'apps': [
          App({
              'id': 'eu.kanade.tachiyomi',
              'flavor': 'dev',
              'min_sdk': 16,
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'tivi',
      'url': 'https://github.com/sgjesse/tivi.git',
      'revision': '25c52e3593e7c98da4e537b49b29f6f67f88754d',
      'apps': [
          App({
              'id': 'app.tivi',
              'min_sdk': 23,
              'compile_sdk': 28,
              'has_lint_task': False
          })
      ]
  }),
  Repo({
      'name': 'Tusky',
      'url': 'https://github.com/mkj-gram/Tusky.git',
      'revision': 'e7fbd190fb53bf9fde72253b816920cb6fe34518',
      'apps': [
          App({
              'id': 'com.keylesspalace.tusky',
              'flavor': 'blue'
          })
      ]
  }),
  Repo({
      'name': 'Vungle-Android-SDK',
      'url': 'https://github.com/mkj-gram/Vungle-Android-SDK.git',
      'revision': '138d3f18c027b61b195c98911f1c5ab7d87ad18b',
      'apps': [
          App({
              'id': 'com.publisher.vungle.sample',
              'skip': True, # TODO(b/144058031)
          })
      ]
  })
]

def signal_handler(signum, frame):
  subprocess.call(['pkill', 'java'])
  raise Exception('Got killed by %s' % signum)

class EnsureNoGradleAlive(object):
 def __init__(self, active):
   self.active = active

 def __enter__(self):
   if self.active:
     # If we timeout and get a sigterm we should still kill all java
     signal.signal(signal.SIGTERM, signal_handler)
     print 'Running with wrapper that will kill java after'

 def __exit__(self, *_):
   if self.active:
     subprocess.call(['pkill', 'java'])

def GetAllApps():
  apps = []
  for repo in APP_REPOSITORIES:
    for app in repo.apps:
      apps.append((app, repo))
  return apps

def GetAllAppNames():
  return [app.name for (app, repo) in GetAllApps()]

def GetAppWithName(query):
  for (app, repo) in GetAllApps():
    if app.name == query:
      return (app, repo)
  assert False

def ComputeSizeOfDexFilesInApk(apk):
  dex_size = 0
  z = zipfile.ZipFile(apk, 'r')
  for filename in z.namelist():
    if filename.endswith('.dex'):
      dex_size += z.getinfo(filename).file_size
  return dex_size

def ExtractMarker(apk, temp_dir, options):
  r8_jar = os.path.join(temp_dir, 'r8.jar')
  r8lib_jar = os.path.join(temp_dir, 'r8lib.jar')

  # Use the copy of r8.jar or r8lib.jar if one is there.
  if os.path.isfile(r8_jar):
    cmd = [jdk.GetJavaExecutable(), '-ea', '-jar', r8_jar, 'extractmarker', apk]
  elif os.path.isfile(r8lib_jar):
    cmd = [jdk.GetJavaExecutable(), '-ea', '-cp', r8lib_jar,
        'com.android.tools.r8.ExtractMarker', apk]
  else:
    script = os.path.join(utils.TOOLS_DIR, 'extractmarker.py')
    cmd = ['python', script, apk]

  utils.PrintCmd(cmd, quiet=options.quiet)
  stdout = subprocess.check_output(cmd)

  # Return the last line.
  lines = stdout.strip().splitlines()
  assert len(lines) >= 1
  return lines[-1]

def CheckIsBuiltWithExpectedR8(apk, temp_dir, shrinker, options):
  marker_raw = ExtractMarker(apk, temp_dir, options)

  # Marker should be a string on the following format (no whitespace):
  #   ~~R8{"compilation-mode":"release",
  #        "min-api":16,
  #        "pg-map-id":"767707e",
  #        "sha-1":"7111a35bae6d5185dcfb338d61074aca8426c006",
  #        "version":"1.5.14-dev"}
  if not marker_raw.startswith('~~R8'):
    raise Exception(
        'Expected marker to start with \'~~R8\' (was: {})'.format(marker_raw))

  marker = json.loads(marker_raw[4:])

  if options.hash:
    actual_hash = marker.get('sha-1')
    if actual_hash != options.hash:
      raise Exception(
          'Expected APK to be built with R8 version {} (was: {})'.format(
              expected_hash, marker_raw))
    return True

  expected_version = (
      options.version
      if options.version
      else utils.getR8Version(
          os.path.join(
              temp_dir,
              'r8lib.jar' if IsMinifiedR8(shrinker) else 'r8.jar')))
  actual_version = marker.get('version')
  if actual_version != expected_version:
    raise Exception(
        'Expected APK to be built with R8 version {} (was: {})'.format(
            expected_version, marker_raw))
  return True

def IsR8(shrinker):
  return 'r8' in shrinker

def IsR8FullMode(shrinker):
  return shrinker == 'r8-full' or shrinker == 'r8-nolib-full'

def IsLoggingEnabledFor(app, options):
  if options.no_logging:
    return False
  if options.app_logging_filter and app.name not in options.app_logging_filter:
    return False
  return True

def IsMinifiedR8(shrinker):
  return 'nolib' not in shrinker

def IsTrackedByGit(file):
  return subprocess.check_output(['git', 'ls-files', file]).strip() != ''

def GitClone(repo, checkout_dir, quiet):
  result = subprocess.check_output(
      ['git', 'clone', repo.url, checkout_dir]).strip()
  head_rev = utils.get_HEAD_sha1_for_checkout(checkout_dir)
  if repo.revision == head_rev:
    return result
  warn('Target revision is not head in {}.'.format(checkout_dir))
  with utils.ChangedWorkingDirectory(checkout_dir, quiet=quiet):
    subprocess.check_output(['git', 'reset', '--hard', repo.revision])
  return result

def GitCheckout(file):
  return subprocess.check_output(['git', 'checkout', file]).strip()

def InstallApkOnEmulator(apk_dest, options):
  cmd = ['adb', '-s', options.emulator_id, 'install', '-r', '-d', apk_dest]
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

def UninstallApkOnEmulator(app, options):
  process = subprocess.Popen(
      ['adb', '-s', options.emulator_id, 'uninstall', app.id],
      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  stdout, stderr = process.communicate()

  if stdout.strip() == 'Success':
    # Successfully uninstalled
    return

  if 'Unknown package: {}'.format(app.id) in stderr:
    # Application not installed
    return

  raise Exception(
      'Unexpected result from `adb uninstall {}\nStdout: {}\nStderr: {}'.format(
          app.id, stdout, stderr))

def WaitForEmulator(options):
  stdout = subprocess.check_output(['adb', 'devices'])
  if '{}\tdevice'.format(options.emulator_id) in stdout:
    return True

  print('Emulator \'{}\' not connected; waiting for connection'.format(
      options.emulator_id))

  time_waited = 0
  while True:
    time.sleep(10)
    time_waited += 10
    stdout = subprocess.check_output(['adb', 'devices'])
    if '{}\tdevice'.format(options.emulator_id) not in stdout:
      print('... still waiting for connection')
      if time_waited >= 5 * 60:
        return False
    else:
      return True

def GetResultsForApp(app, repo, options, temp_dir):
  # Checkout and build in the build directory.
  repo_name = repo.name
  repo_checkout_dir = os.path.join(WORKING_DIR, repo_name)

  result = {}

  if not os.path.exists(repo_checkout_dir) and not options.golem:
    with utils.ChangedWorkingDirectory(WORKING_DIR, quiet=options.quiet):
      GitClone(repo, repo_checkout_dir, options.quiet)

  checkout_rev = utils.get_HEAD_sha1_for_checkout(repo_checkout_dir)
  if repo.revision != checkout_rev:
    msg = 'Checkout is not target revision for {} in {}.'.format(
        app.name, repo_checkout_dir)
    if options.ignore_versions:
      warn(msg)
    else:
      raise Exception(msg)

  result['status'] = 'success'

  app_checkout_dir = (os.path.join(repo_checkout_dir, app.dir)
                      if app.dir else repo_checkout_dir)

  result_per_shrinker = BuildAppWithSelectedShrinkers(
      app, repo, options, app_checkout_dir, temp_dir)
  for shrinker, shrinker_result in result_per_shrinker.iteritems():
    result[shrinker] = shrinker_result

  return result

def BuildAppWithSelectedShrinkers(
    app, repo, options, checkout_dir, temp_dir):
  result_per_shrinker = {}

  with utils.ChangedWorkingDirectory(checkout_dir, quiet=options.quiet):
    for shrinker in options.shrinker:
      apk_dest = None

      result = {}
      proguard_config_file = None
      if not options.r8_compilation_steps_only:
        try:
          out_dir = os.path.join(checkout_dir, 'out', shrinker)
          (apk_dest, profile_dest_dir, res_proguard_config_file) = \
              BuildAppWithShrinker(
                  app, repo, shrinker, checkout_dir, out_dir, temp_dir,
                  options)
          proguard_config_file = res_proguard_config_file
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
          warn('Failed to build {} with {}'.format(app.name, shrinker))
          if e:
            print('Error: ' + str(e))
          result['build_status'] = 'failed'

      if result.get('build_status') == 'success':
        if options.monkey:
          result['monkey_status'] = 'success' if RunMonkey(
              app, options, apk_dest) else 'failed'

      if (result.get('build_status') == 'success'
          or options.r8_compilation_steps_only):
        if 'r8' in shrinker and options.r8_compilation_steps > 1:
          result['recompilation_results'] = \
              ComputeRecompilationResults(
                  app, repo, options, checkout_dir, temp_dir, shrinker,
                  proguard_config_file)

      if result.get('build_status') == 'success':
        if options.run_tests and app.has_instrumentation_tests:
          result['instrumentation_test_results'] = \
              ComputeInstrumentationTestResults(
                  app, options, checkout_dir, out_dir, shrinker)

      result_per_shrinker[shrinker] = result

  if len(options.apps) > 1:
    print('')
    LogResultsForApp(app, result_per_shrinker, options)
    print('')

  return result_per_shrinker

def BuildAppWithShrinker(
    app, repo, shrinker, checkout_dir, out_dir, temp_dir, options,
    keepRuleSynthesisForRecompilation=False):
  print('[{}] Building {} with {}{}'.format(
      datetime.now().strftime("%H:%M:%S"),
      app.name,
      shrinker,
      ' for recompilation' if keepRuleSynthesisForRecompilation else ''))
  print('To compile locally: '
        'tools/run_on_as_app.py --shrinker {} --r8-compilation-steps {} '
        '--app {} {}'.format(
            shrinker,
            options.r8_compilation_steps,
            app.name,
            '--r8-compilation-steps-only'
              if options.r8_compilation_steps_only else ''))
  print('HINT: use --shrinker r8-nolib --no-build if you have a local R8.jar')
  # Add settings.gradle file if it is not present to prevent gradle from finding
  # the settings.gradle file in the r8 root when apps are placed under
  # $R8/build.
  as_utils.add_settings_gradle(checkout_dir, app.name)

  # Add 'r8.jar' to top-level build.gradle.
  as_utils.add_r8_dependency(checkout_dir, temp_dir, IsMinifiedR8(shrinker))

  archives_base_name = app.archives_base_name

  if not os.path.exists(out_dir):
    os.makedirs(out_dir)

  # Set -printconfiguration in Proguard rules.
  proguard_config_dest = os.path.abspath(
      os.path.join(out_dir, 'proguard-rules.pro'))
  as_utils.SetPrintConfigurationDirective(
      app, checkout_dir, proguard_config_dest)

  env_vars = {}
  env_vars['JAVA_HOME'] = jdk.GetJdk8Home()
  env_vars['ANDROID_HOME'] = utils.getAndroidHome()
  if not options.disable_assertions:
    env_vars['JAVA_OPTS'] = '-ea:com.android.tools.r8...'

  release_target = app.releaseTarget
  if not release_target:
    app_module = app.module.replace('/', ':')
    app_flavor = (app.flavor.capitalize() if app.flavor else '') + 'Release'
    release_target = app_module + ':' + 'assemble' + app_flavor

  # Build using gradle.
  args = [release_target,
          '-g=' + os.path.join(checkout_dir, GRADLE_USER_HOME),
          '-Pandroid.enableR8=' + str(IsR8(shrinker)).lower(),
          '-Pandroid.enableR8.fullMode=' + str(IsR8FullMode(shrinker)).lower()]
  if app.has_lint_task:
    args.extend(['-x', app_module + ':lintVital' + app_flavor])
  if options.bot:
    args.extend(['--console=plain', '--info'])

  # Warm up gradle if pre_runs > 0. For posterity we generate the same sequence
  # as the benchmarking at https://github.com/madsager/santa-tracker-android.
  for i in range(0, options.gradle_pre_runs):
    if i == 0:
      utils.RunGradlew(
          ["--stop"],
          env_vars=env_vars,
          quiet=options.quiet,
          logging=IsLoggingEnabledFor(app, options),
          use_daemon=options.use_daemon)
    utils.RunGradlew(
        args,
        env_vars=env_vars,
        quiet=options.quiet,
        clean=i > 0,
        use_daemon=options.use_daemon,
        logging=IsLoggingEnabledFor(app, options))

  if keepRuleSynthesisForRecompilation:
    args.append('-Dcom.android.tools.r8.keepRuleSynthesisForRecompilation=true')
  if options.gradle_flags:
    args.extend(options.gradle_flags.split(' '))

  args.append('--profile')

  stdout = utils.RunGradlew(
      args,
      env_vars=env_vars,
      quiet=options.quiet,
      use_daemon=options.use_daemon,
      logging=IsLoggingEnabledFor(app, options))

  apk_base_name = (archives_base_name
      + (('-' + app.flavor) if app.flavor else '') + '-release')
  signed_apk_name = (
      app.signed_apk_name
      if app.signed_apk_name
      else apk_base_name + '.apk')
  unsigned_apk_name = apk_base_name + '-unsigned.apk'

  build_dir = os.path.join(app.module, app.build_dir)
  build_output_apks = os.path.join(build_dir, 'outputs', 'apk')
  if app.flavor:
    build_output_apks = os.path.join(build_output_apks, app.flavor, 'release')
  else:
    build_output_apks = os.path.join(build_output_apks, 'release')

  signed_apk = os.path.join(build_output_apks, signed_apk_name)
  unsigned_apk = os.path.join(build_output_apks, unsigned_apk_name)

  assert os.path.isfile(signed_apk) or os.path.isfile(unsigned_apk), (
      "Expected a file to be present at {} or {}, found: {}\n"
      "Standard out from compilation: {}".format(
          signed_apk,
          unsigned_apk,
          ', '.join(
              as_utils.ListFiles(build_dir, lambda x : x.endswith('.apk'))),
          stdout))

  if options.sign_apks and not os.path.isfile(signed_apk):
    assert os.path.isfile(unsigned_apk)
    if options.sign_apks:
      apk_utils.sign_with_apksigner(
          unsigned_apk,
          signed_apk,
          options.keystore,
          options.keystore_password,
          quiet=options.quiet,
          logging=IsLoggingEnabledFor(app, options))

  if os.path.isfile(signed_apk):
    apk_dest = os.path.join(out_dir, signed_apk_name)
    as_utils.MoveFile(signed_apk, apk_dest, quiet=options.quiet)
  else:
    apk_dest = os.path.join(out_dir, unsigned_apk_name)
    as_utils.MoveFile(unsigned_apk, apk_dest, quiet=options.quiet)

  assert ('r8' not in shrinker
      or CheckIsBuiltWithExpectedR8(apk_dest, temp_dir, shrinker, options))

  profile_dest_dir = os.path.join(out_dir, 'profile')
  as_utils.MoveProfileReportTo(profile_dest_dir, stdout, quiet=options.quiet)
  # Ensure that the gradle daemon is stopped if we are running with it.
  if options.use_daemon:
    utils.RunGradlew(['--stop', '-g=' + os.path.join(checkout_dir, GRADLE_USER_HOME)])

  return (apk_dest, profile_dest_dir, proguard_config_dest)

def ComputeInstrumentationTestResults(
    app, options, checkout_dir, out_dir, shrinker):
  args = ['connectedAndroidTest',
         '-Pandroid.enableR8=' + str(IsR8(shrinker)).lower(),
         '-Pandroid.enableR8.fullMode=' + str(IsR8FullMode(shrinker)).lower()]
  env_vars = { 'ANDROID_SERIAL': options.emulator_id }
  stdout = utils.RunGradlew(
      args,
      env_vars=env_vars,
      quiet=options.quiet,
      fail=False,
      logging=IsLoggingEnabledFor(app, options),
      use_daemon=options.use_daemon)

  xml_test_result_dest = os.path.join(out_dir, 'test_result')
  as_utils.MoveXMLTestResultFileTo(
      xml_test_result_dest, stdout, quiet=options.quiet)

  with open(xml_test_result_dest, 'r') as f:
    xml_test_result_contents = f.read()

  xml_document = minidom.parseString(xml_test_result_contents)
  testsuite_element = xml_document.documentElement

  return {
    'xml_test_result_dest': xml_test_result_dest,
    'tests': int(testsuite_element.getAttribute('tests')),
    'failures': int(testsuite_element.getAttribute('failures')),
    'errors': int(testsuite_element.getAttribute('errors')),
    'skipped': int(testsuite_element.getAttribute('skipped'))
  }

def ComputeRecompilationResults(
    app, repo, options, checkout_dir, temp_dir, shrinker, proguard_config_file):
  recompilation_results = []

  # Build app with gradle using -D...keepRuleSynthesisForRecompilation=true.
  out_dir = os.path.join(checkout_dir, 'out', shrinker + '-1')
  (apk_dest, profile_dest_dir, ext_proguard_config_file) = \
      BuildAppWithShrinker(
          app, repo, shrinker, checkout_dir, out_dir,
          temp_dir, options, keepRuleSynthesisForRecompilation=True)
  recompilation_result = {
    'apk_dest': apk_dest,
    'build_status': 'success',
    'dex_size': ComputeSizeOfDexFilesInApk(apk_dest),
    'monkey_status': 'skipped'
  }
  recompilation_results.append(recompilation_result)

  # Sanity check that keep rules have changed. If we are only doing
  # recompilation, the passed in proguard_config_file is None.
  if proguard_config_file:
    with open(ext_proguard_config_file) as new:
      with open(proguard_config_file) as old:
        assert(
            sum(1 for line in new
                if line.strip() and '-printconfiguration' not in line)
            >
            sum(1 for line in old
                if line.strip() and '-printconfiguration' not in line))

  # Extract min-sdk and target-sdk
  (min_sdk, compile_sdk) = \
      as_utils.GetMinAndCompileSdk(app, checkout_dir, apk_dest)

  # Now rebuild generated apk.
  previous_apk = apk_dest

  # We may need main dex rules when re-compiling with R8 as standalone.
  main_dex_rules = None
  if app.main_dex_rules:
    main_dex_rules = os.path.join(checkout_dir, app.main_dex_rules)

  for i in range(1, options.r8_compilation_steps):
    try:
      recompiled_apk_dest = os.path.join(
          checkout_dir, 'out', shrinker, 'app-release-{}.apk'.format(i))
      if not os.path.exists(os.path.dirname(recompiled_apk_dest)):
        os.makedirs(os.path.dirname(recompiled_apk_dest))
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
            app, options, recompiled_apk_dest) else 'failed'
      recompilation_results.append(recompilation_result)
      previous_apk = recompiled_apk_dest
    except Exception as e:
      warn('Failed to recompile {} with {}'.format(
          app.name, shrinker))
      recompilation_results.append({ 'build_status': 'failed' })
      break
  return recompilation_results

def RebuildAppWithShrinker(
    app, apk, apk_dest, proguard_config_file, shrinker, min_sdk, compile_sdk,
    options, temp_dir, main_dex_rules):
  assert 'r8' in shrinker
  assert apk_dest.endswith('.apk')

  print('Rebuilding {} with {}'.format(app.name, shrinker))

  # Compile given APK with shrinker to temporary zip file.
  android_jar = utils.get_android_jar(compile_sdk)
  r8_jar = os.path.join(
      temp_dir, 'r8lib.jar' if IsMinifiedR8(shrinker) else 'r8.jar')
  zip_dest = apk_dest[:-4] + '.zip'

  # TODO(christofferqa): Entry point should be CompatProguard if the shrinker
  # is 'r8'.
  entry_point = 'com.android.tools.r8.R8'

  cmd = ([jdk.GetJavaExecutable()] +
         (['-ea:com.android.tools.r8...']
          if not options.disable_assertions
          else []) +
         ['-cp', r8_jar, entry_point,
         '--release', '--min-api', str(min_sdk),
         '--pg-conf', proguard_config_file,
         '--lib', android_jar,
         '--output', zip_dest,
         apk])

  for android_optional_jar in utils.get_android_optional_jars(compile_sdk):
    cmd.append('--lib')
    cmd.append(android_optional_jar)

  if main_dex_rules:
    cmd.append('--main-dex-rules')
    cmd.append(main_dex_rules)

  utils.RunCmd(
    cmd, quiet=options.quiet, logging=IsLoggingEnabledFor(app, options))

  # Make a copy of the given APK, move the newly generated dex files into the
  # copied APK, and then sign the APK.
  apk_masseur.masseur(
      apk, dex=zip_dest, resources='META-INF/services/*', out=apk_dest,
      quiet=options.quiet, logging=IsLoggingEnabledFor(app, options),
      keystore=options.keystore)

def RunMonkey(app, options, apk_dest):
  if not WaitForEmulator(options):
    return False

  UninstallApkOnEmulator(app, options)
  InstallApkOnEmulator(apk_dest, options)

  number_of_events_to_generate = options.monkey_events

  # Intentionally using a constant seed such that the monkey generates the same
  # event sequence for each shrinker.
  random_seed = 42

  cmd = ['adb', '-s', options.emulator_id, 'shell', 'monkey', '-p', app.id,
      '-s', str(random_seed), str(number_of_events_to_generate)]

  try:
    stdout = utils.RunCmd(
        cmd, quiet=options.quiet, logging=IsLoggingEnabledFor(app, options))
    succeeded = (
        'Events injected: {}'.format(number_of_events_to_generate) in stdout)
  except subprocess.CalledProcessError as e:
    succeeded = False

  UninstallApkOnEmulator(app, options)

  return succeeded

def LogResultsForApps(result_per_shrinker_per_app, options):
  print('')
  app_errors = 0
  for (app, result_per_shrinker) in result_per_shrinker_per_app:
    app_errors += (1 if LogResultsForApp(app, result_per_shrinker, options)
                   else 0)
  return app_errors

def LogResultsForApp(app, result_per_shrinker, options):
  if options.print_dexsegments:
    LogSegmentsForApp(app, result_per_shrinker, options)
    return False
  else:
    return LogComparisonResultsForApp(app, result_per_shrinker, options)

def LogSegmentsForApp(app, result_per_shrinker, options):
  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    result = result_per_shrinker[shrinker];
    benchmark_name = '{}-{}'.format(options.print_dexsegments, app.name)
    utils.print_dexsegments(benchmark_name, [result.get('apk_dest')])
    duration = sum(result.get('profile').values())
    print('%s-Total(RunTimeRaw): %s ms' % (benchmark_name, duration * 1000))
    print('%s-Total(CodeSize): %s' % (benchmark_name, result.get('dex_size')))


def LogComparisonResultsForApp(app, result_per_shrinker, options):
  print(app.name + ':')
  app_error = False
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
    if build_status != 'success' and build_status is not None:
      app_error = True
      warn('  {}: {}'.format(shrinker, build_status))
      continue

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
    if profile:
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
        app_error = True
        warn('    monkey: {}'.format(monkey_status))
      else:
        success('    monkey: {}'.format(monkey_status))

    recompilation_results = result.get('recompilation_results', [])
    i = 0
    for recompilation_result in recompilation_results:
      build_status = recompilation_result.get('build_status')
      if build_status != 'success':
        app_error = True
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

    if options.run_tests and 'instrumentation_test_results' in result:
      instrumentation_test_results = \
          result.get('instrumentation_test_results')
      succeeded = (
          instrumentation_test_results.get('failures')
              + instrumentation_test_results.get('errors')
              + instrumentation_test_results.get('skipped')) == 0
      if succeeded:
        success('    tests: succeeded')
      else:
        app_error = True
        warn(
            '    tests: failed (failures: {}, errors: {}, skipped: {})'
            .format(
                instrumentation_test_results.get('failures'),
                instrumentation_test_results.get('errors'),
                instrumentation_test_results.get('skipped')))

  return app_error

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--app',
                    help='What app to run on',
                    choices=GetAllAppNames(),
                    action='append')
  result.add_option('--bot',
                    help='Running on bot, use third_party dependency.',
                    default=False,
                    action='store_true')
  result.add_option('--disable-assertions', '--disable_assertions',
                    help='Disable assertions when compiling',
                    default=False,
                    action='store_true')
  result.add_option('--download-only', '--download_only',
                    help='Whether to download apps without any compilation',
                    default=False,
                    action='store_true')
  result.add_option('--emulator-id', '--emulator_id',
                    help='Id of the emulator to use',
                    default='emulator-5554')
  result.add_option('--golem',
                    help='Running on golem, do not download',
                    default=False,
                    action='store_true')
  result.add_option('--gradle-flags', '--gradle_flags',
                    help='Flags to pass in to gradle')
  result.add_option('--gradle-pre-runs', '--gradle_pre_runs',
                    help='Do rounds of compilations to warm up gradle',
                    default=0,
                    type=int)
  result.add_option('--hash',
                    help='The version of R8 to use')
  result.add_option('--ignore-versions', '--ignore_versions',
                    help='Allow checked-out app to differ in revision from '
                         'pinned',
                    default=False,
                    action='store_true')
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
  result.add_option('--r8-compilation-steps-only', '--r8_compilation_steps_only',
                    help='Specify to only run compilation steps',
                    default=False,
                    action='store_true')
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
  result.add_option('--use-daemon', '--use_daemon',
                    help='Whether to use a gradle daemon',
                    default=False,
                    action='store_true')
  result.add_option('--version',
                    help='The version of R8 to use (e.g., 1.4.51)')
  (options, args) = result.parse_args(argv)
  if options.app:
    options.apps = [(app, repo) for (app, repo) in GetAllApps()
                    if app.name in options.app]
    del options.app
  else:
    options.apps = GetAllApps()
  if options.app_logging_filter:
    for app_name in options.app_logging_filter:
      assert any(app.name == app_name for (app, repo) in options.apps)
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
  assert not options.r8_compilation_steps_only \
         or options.r8_compilation_steps > 1
  return (options, args)

def clone_repositories(quiet):
  # Clone repositories into WORKING_DIR.
  with utils.ChangedWorkingDirectory(WORKING_DIR):
    for repo in APP_REPOSITORIES:
      repo_dir = os.path.join(WORKING_DIR, repo.name)
      if not os.path.exists(repo_dir):
        GitClone(repo, repo_dir, quiet)


def main(argv):
  (options, args) = ParseOptions(argv)

  if options.bot:
    utils.DownloadFromGoogleCloudStorage(utils.OPENSOURCE_APPS_SHA_FILE)
    utils.DownloadFromGoogleCloudStorage(utils.ANDROID_SDK + '.tar.gz.sha1',
                                         bucket='r8-deps-internal',
                                         auth=True)
    if os.path.exists(WORKING_DIR):
      shutil.rmtree(WORKING_DIR)
    shutil.copytree(utils.OPENSOURCE_APPS_FOLDER, WORKING_DIR)
    os.environ[utils.ANDROID_HOME_ENVIROMENT_NAME] = os.path.join(
        utils.ANDROID_SDK)
    os.environ[utils.ANDROID_TOOLS_VERSION_ENVIRONMENT_NAME] = '28.0.3'
    # TODO(b/141081520): Set to True once fixed.
    options.no_logging = False
    # TODO(b/141081520): Remove logging filter once fixed.
    options.app_logging_filter = ['sqldelight']
    options.shrinker = ['r8', 'r8-full']
    print(options.shrinker)

  if options.golem:
    golem.link_third_party()
    if os.path.exists(WORKING_DIR):
      shutil.rmtree(WORKING_DIR)
    shutil.copytree(utils.OPENSOURCE_APPS_FOLDER, WORKING_DIR)
    os.environ[utils.ANDROID_HOME_ENVIROMENT_NAME] = os.path.join(
        utils.ANDROID_SDK)
    os.environ[utils.ANDROID_TOOLS_VERSION_ENVIRONMENT_NAME] = '28.0.3'
    options.disable_assertions = True
    options.ignore_versions = True
    options.no_build = True
    options.r8_compilation_steps = 1
    options.quiet = True
    options.gradle_pre_runs = 2
    options.use_daemon = True
    options.no_logging = True

  if not os.path.exists(WORKING_DIR):
    os.makedirs(WORKING_DIR)

  if options.download_only:
    clone_repositories(options.quiet)
    return

  with utils.TempDir() as temp_dir:
    if not (options.no_build or options.golem):
      gradle.RunGradle(['r8', '-Pno_internal'])
      build_r8lib = False
      for shrinker in options.shrinker:
        if IsMinifiedR8(shrinker):
          build_r8lib = True
      if build_r8lib:
        gradle.RunGradle(['r8lib', '-Pno_internal'])

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
      # Make a copy of r8.jar and r8lib.jar such that they stay the same for
      # the entire execution of this script.
      if 'r8-nolib' in options.shrinker or 'r8-nolib-full' in options.shrinker:
        assert os.path.isfile(utils.R8_JAR), 'Cannot build without r8.jar'
        shutil.copyfile(utils.R8_JAR, os.path.join(temp_dir, 'r8.jar'))
      if 'r8' in options.shrinker or 'r8-full' in options.shrinker:
        assert os.path.isfile(utils.R8LIB_JAR), 'Cannot build without r8lib.jar'
        shutil.copyfile(utils.R8LIB_JAR, os.path.join(temp_dir, 'r8lib.jar'))

    result_per_shrinker_per_app = []
    # If we are running on golem we kill all java processes after the run
    # to ensure no hanging gradle daemons.
    with EnsureNoGradleAlive(options.golem):
      for (app, repo) in options.apps:
        if app.skip:
          continue
        result_per_shrinker_per_app.append(
            (app, GetResultsForApp(app, repo, options, temp_dir)))
    return LogResultsForApps(result_per_shrinker_per_app, options)

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
