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

SHRINKERS = ['r8', 'r8full', 'proguard']

APPS = {
  # 'app-name': {
  #     'git_repo': ...
  #     'app_module': ... (default app)
  #     'archives_base_name': ... (default same as app_module)
  #     'flavor': ... (default no flavor)
  # },
  'tachiyomi': {
      'git_repo': 'https://github.com/sgjesse/tachiyomi.git',
      'flavor': 'standard',
  },
  # This does not build yet.
  'muzei': {
      'git_repo': 'https://github.com/sgjesse/muzei.git',
      'app_module': 'main',
      'archives_base_name': 'muzei',
  },
}

def GitClone(git_url):
  return subprocess.check_output(['git', 'clone', git_url]).strip()

def GitPull():
  return subprocess.check_output(['git', 'pull']).strip()

def GitCheckout(file):
  return subprocess.check_output(['git', 'checkout', file]).strip()

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--app',
                    help='What app to run on',
                    choices=APPS.keys())
  return result.parse_args(argv)

def main(argv):
  (options, args) = ParseOptions(argv)

  # Common environment setup.
  user_home = os.path.expanduser('~')
  android_home = os.path.join(user_home, 'Android', 'Sdk')
  android_build_tools_version = '28.0.3'
  android_build_tools = os.path.join(
      android_home, 'build-tools', android_build_tools_version)

  # App setup.
  app = options.app
  if not app:
    raise Exception(
        "You need to specify '--app={}'".format('|'.join(APPS.keys())))
  config = APPS.get(app)
  git_repo = config['git_repo']
  app_module = config.get('app_module', 'app')
  archives_base_name = config.get(' archives_base_name', app_module)
  flavor = config.get('flavor')

  # Checkout and build in the build directory.
  working_dir = utils.BUILD
  checkout_dir = os.path.join(working_dir, app)

  if not os.path.exists(checkout_dir):
    with utils.ChangedWorkingDirectory(working_dir):
      GitClone(git_repo)
  else:
    with utils.ChangedWorkingDirectory(checkout_dir):
      GitPull()

  with utils.ChangedWorkingDirectory(checkout_dir):
    for shrinker in SHRINKERS:

      # Ensure that gradle.properties are not modified before modifying it to
      # select shrinker.
      GitCheckout('gradle.properties')
      with open("gradle.properties", "a") as gradle_properties:
        if shrinker == 'r8full':
          gradle_properties.write('\nandroid.enableR8.fullMode=true\n')
        if shrinker == 'proguard':
          gradle_properties.write('\nandroid.enableR8=false\n')

      out = os.path.join(checkout_dir, 'out', shrinker)
      if not os.path.exists(out):
        os.makedirs(out)

      env = os.environ.copy()
      env['ANDROID_HOME'] = android_home
      releaseTarget = app_module + ':' + 'assembleRelease'
      subprocess.check_call(
          ['./gradlew', '--no-daemon', 'clean', releaseTarget], env=env)

      unsigned_apk_name = (archives_base_name
          + (('-' + flavor) if flavor else '')
          + '-release-unsigned.apk')
      signed_apk_name = archives_base_name + '-release.apk'

      build_output_apks = os.path.join(app_module, 'build', 'outputs', 'apk')
      if flavor:
        unsigned_apk = os.path.join(
            build_output_apks, flavor, 'release', unsigned_apk_name)
      else:
        unsigned_apk = os.path.join(
            build_output_apks, 'release', unsigned_apk_name)

      signed_apk = os.path.join(out, signed_apk_name)

      keystore = 'app.keystore'
      keystore_password = 'android'
      apk_utils.sign_with_apksigner(
          android_build_tools,
          unsigned_apk,
          signed_apk,
          keystore,
          keystore_password)

    GitCheckout('gradle.properties')

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
