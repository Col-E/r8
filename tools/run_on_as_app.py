#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import apk_utils
import os
import optparse
import subprocess
import sys
import utils
import zipfile

import as_utils

SHRINKERS = ['r8', 'r8full', 'proguard']
WORKING_DIR = utils.BUILD

if 'R8_BENCHMARK_DIR' in os.environ and os.path.isdir(os.environ['R8_BENCHMARK_DIR']):
  WORKING_DIR = os.environ['R8_BENCHMARK_DIR']

APPS = {
  # 'app-name': {
  #     'git_repo': ...
  #     'app_module': ... (default app)
  #     'archives_base_name': ... (default same as app_module)
  #     'flavor': ... (default no flavor)
  # },
  'AnExplorer': {
      'git_repo': 'https://github.com/1hakr/AnExplorer',
      'flavor': 'googleMobilePro',
      'signed-apk-name': 'AnExplorer-googleMobileProRelease-4.0.3.apk',
  },
  'AntennaPod': {
      'git_repo': 'https://github.com/AntennaPod/AntennaPod.git',
      'flavor': 'play',
  },
  'apps-android-wikipedia': {
      'git_repo': 'https://github.com/wikimedia/apps-android-wikipedia',
      'flavor': 'prod',
      'signed-apk-name': 'app-prod-universal-release.apk'
  },
  'friendlyeats-android': {
      'git_repo': 'https://github.com/firebase/friendlyeats-android.git'
  },
  'KISS': {
      'git_repo': 'https://github.com/Neamar/KISS',
  },
  'materialistic': {
      'git_repo': 'https://github.com/hidroh/materialistic',
  },
  'Minimal-Todo': {
      'git_repo': 'https://github.com/avjinder/Minimal-Todo',
  },
  'NewPipe': {
      'git_repo': 'https://github.com/TeamNewPipe/NewPipe',
  },
  'Simple-Calendar': {
      'git_repo': 'https://github.com/SimpleMobileTools/Simple-Calendar',
      'signed-apk-name': 'calendar-release.apk'
  },
  'tachiyomi': {
      'git_repo': 'https://github.com/sgjesse/tachiyomi.git',
      'flavor': 'standard',
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

def IsTrackedByGit(file):
  return subprocess.check_output(['git', 'ls-files', file]).strip() != ''

def GitClone(git_url):
  return subprocess.check_output(['git', 'clone', git_url]).strip()

def GitPull():
  return subprocess.check_output(['git', 'pull']).strip()

def GitCheckout(file):
  return subprocess.check_output(['git', 'checkout', file]).strip()

def MoveApkToDest(apk, apk_dest):
  print('Moving `{}` to `{}`'.format(apk, apk_dest))
  assert os.path.isfile(apk)
  if os.path.isfile(apk_dest):
    os.remove(apk_dest)
  os.rename(apk, apk_dest)

def BuildAppWithSelectedShrinkers(app, config, options):
  git_repo = config['git_repo']

  # Checkout and build in the build directory.
  checkout_dir = os.path.join(WORKING_DIR, app)

  if not os.path.exists(checkout_dir):
    with utils.ChangedWorkingDirectory(WORKING_DIR):
      GitClone(git_repo)
  else:
    with utils.ChangedWorkingDirectory(checkout_dir):
      GitPull()

  if options.use_tot:
    as_utils.add_r8_dependency(checkout_dir)
  else:
    as_utils.remove_r8_dependency(checkout_dir)

  dex_size_per_shrinker = {}

  with utils.ChangedWorkingDirectory(checkout_dir):
    for shrinker in SHRINKERS:
      if options.shrinker is not None and shrinker != options.shrinker:
        continue

      apk_dest = BuildAppWithShrinker(
        app, config, shrinker, checkout_dir, options)

      dex_size = ComputeSizeOfDexFilesInApk(apk_dest)
      dex_size_per_shrinker[shrinker] = dex_size

    if IsTrackedByGit('gradle.properties'):
      GitCheckout('gradle.properties')

  return dex_size_per_shrinker

def BuildAppWithShrinker(app, config, shrinker, checkout_dir, options):
  print('Building {} with {}'.format(app, shrinker))

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
      if shrinker == 'r8full':
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
  releaseTarget = app_module + ':' + 'assemble' + (
      flavor.capitalize() if flavor else '') + 'Release'

  cmd = ['./gradlew', '--no-daemon', 'clean', releaseTarget, '--stacktrace']
  utils.PrintCmd(cmd)
  subprocess.check_call(cmd, env=env)

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
    MoveApkToDest(signed_apk, apk_dest)
  else:
    apk_dest = os.path.join(out, unsigned_apk_name)
    MoveApkToDest(unsigned_apk, apk_dest)

  assert IsBuiltWithR8(apk_dest) == ('r8' in shrinker), (
      'Unexpected marker in generated APK for {}'.format(shrinker))

  return apk_dest

def LogResults(dex_size_per_shrinker_per_app):
  for app, dex_size_per_shrinker in dex_size_per_shrinker_per_app.iteritems():
    print(app + ':')
    baseline = dex_size_per_shrinker.get('proguard', -1)
    for shrinker, dex_size in dex_size_per_shrinker.iteritems():
      if dex_size != baseline and baseline >= 0:
        if dex_size < baseline:
          success('  {}: {} ({})'.format(
            shrinker, dex_size, dex_size - baseline))
        elif dex_size > baseline:
          warn('  {}: {} ({})'.format(
            shrinker, dex_size, dex_size - baseline))
      else:
        print('  {}: {}'.format(shrinker, dex_size))

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--app',
                    help='What app to run on',
                    choices=APPS.keys())
  result.add_option('--sign_apks',
                    help='Whether the APKs should be signed',
                    default=False,
                    action='store_true')
  result.add_option('--shrinker',
                    help='The shrinker to use (by default, all are run)',
                    choices=SHRINKERS)
  result.add_option('--use_tot',
                    help='Whether to use the ToT version of R8',
                    default=False,
                    action='store_true')
  return result.parse_args(argv)

def main(argv):
  (options, args) = ParseOptions(argv)
  assert not options.use_tot or os.path.isfile(utils.R8_JAR), (
      'Cannot build from ToT without r8.jar')

  dex_size_per_shrinker_per_app = {}

  if options.app:
    dex_size_per_shrinker_per_app[options.app] = BuildAppWithSelectedShrinkers(
        options.app, APPS.get(options.app), options)
  else:
    for app, config in APPS.iteritems():
      if not config.get('skip', False):
        dex_size_per_shrinker_per_app[app] = BuildAppWithSelectedShrinkers(
            app, config, options)

  LogResults(dex_size_per_shrinker_per_app)

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
