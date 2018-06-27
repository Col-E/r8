#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script for building sample apks using the sdk tools directly.

import apk_utils
import fnmatch
import glob
import optparse
import os
import shutil
import subprocess
import sys
import utils

ANDROID_JAR = 'third_party/android_jar/lib-v{api}/android.jar'
DEFAULT_AAPT = 'aapt' # Assume in path.
DEFAULT_D8 = os.path.join(utils.REPO_ROOT, 'tools', 'd8.py')
DEFAULT_DEXSPLITTER = os.path.join(utils.REPO_ROOT, 'tools', 'dexsplitter.py')
DEFAULT_JAVAC = 'javac'
SRC_LOCATION = 'src/com/android/tools/r8/sample/{app}/*.java'
DEFAULT_KEYSTORE = os.path.join(os.getenv('HOME'), '.android', 'debug.keystore')

SAMPLE_APKS = [
    'simple',
    'split'
]

def parse_options():
  result = optparse.OptionParser()
  result.add_option('--aapt',
                    help='aapt executable to use',
                    default=DEFAULT_AAPT)
  result.add_option('--api',
                    help='Android api level',
                    default=21,
                    choices=[14, 15, 19, 21, 22, 23, 24, 25, 26])
  result.add_option('--keystore',
                    help='Keystore used for signing',
                    default=DEFAULT_KEYSTORE)
  result.add_option('--split',
                    help='Split the app using the split.spec file',
                    default=False, action='store_true')
  result.add_option('--app',
                    help='Which app to build',
                    default='simple',
                    choices=SAMPLE_APKS)
  return result.parse_args()

def run_aapt(aapt, args):
  command = [aapt]
  command.extend(args)
  utils.PrintCmd(command)
  subprocess.check_call(command)

def get_build_dir(app):
  return os.path.join(utils.BUILD, 'sampleApks', app)

def get_gen_path(app):
  gen_path = os.path.join(get_build_dir(app), 'gen')
  utils.makedirs_if_needed(gen_path)
  return gen_path

def get_bin_path(app):
  bin_path = os.path.join(get_build_dir(app), 'bin')
  utils.makedirs_if_needed(bin_path)
  return bin_path

def get_android_jar(api):
  return os.path.join(utils.REPO_ROOT, ANDROID_JAR.format(api=api))

def get_sample_dir(app):
  return os.path.join(utils.REPO_ROOT, 'src', 'test', 'sampleApks', app)

def get_src_path(app):
  return os.path.join(get_sample_dir(app), 'src')

def get_dex_path(app):
  return os.path.join(get_bin_path(app), 'classes.dex')

def get_split_path(app, split):
  return os.path.join(get_bin_path(app), split, 'classes.dex')

def run_aapt_pack(aapt, api, app):
  with utils.ChangedWorkingDirectory(get_sample_dir(app)):
    args = ['package',
            '-v', '-f',
            '-I', get_android_jar(api),
            '-M', 'AndroidManifest.xml',
            '-A', 'assets',
            '-S', 'res',
            '-m',
            '-J', get_gen_path(app),
            '-F', os.path.join(get_bin_path(app), 'resources.ap_'),
            '-G', os.path.join(get_build_dir(app), 'proguard_options')]
    run_aapt(aapt, args)

def run_aapt_split_pack(aapt, api, app):
  with utils.ChangedWorkingDirectory(get_sample_dir(app)):
    args = ['package',
            '-v', '-f',
            '-I', get_android_jar(api),
            '-M', 'split_manifest/AndroidManifest.xml',
            '-S', 'res',
            '-F', os.path.join(get_bin_path(app), 'split_resources.ap_')]
    run_aapt(aapt, args)

def compile_with_javac(api, app):
  with utils.ChangedWorkingDirectory(get_sample_dir(app)):
    files = glob.glob(SRC_LOCATION.format(app=app))
    command = [DEFAULT_JAVAC,
               '-classpath', get_android_jar(api),
               '-sourcepath', '%s:%s' % (get_src_path(app), get_gen_path(app)),
               '-d', get_bin_path(app)]
    command.extend(files)
    utils.PrintCmd(command)
    subprocess.check_call(command)

def dex(app, api):
  files = []
  for root, dirnames, filenames in os.walk(get_bin_path(app)):
    for filename in fnmatch.filter(filenames, '*.class'):
        files.append(os.path.join(root, filename))
  command = [DEFAULT_D8,
             '--output', get_bin_path(app),
             '--classpath', get_android_jar(api),
             '--min-api', str(api)]
  command.extend(files)
  utils.PrintCmd(command)
  subprocess.check_call(command)

def split(app):
  split_spec = os.path.join(get_sample_dir(app), 'split.spec')
  command = [DEFAULT_DEXSPLITTER,
             '--input', get_dex_path(app),
             '--output', get_bin_path(app),
             '--feature-splits', split_spec]
  utils.PrintCmd(command)
  subprocess.check_call(command)

def create_temp_apk(app, prefix):
  temp_apk_path = os.path.join(get_bin_path(app), '%s.ap_' % app)
  shutil.copyfile(os.path.join(get_bin_path(app), '%sresources.ap_' % prefix),
                  temp_apk_path)
  return temp_apk_path

def aapt_add_dex(aapt, dex, temp_apk_path):
  args = ['add',
          '-k', temp_apk_path,
          dex]
  run_aapt(aapt, args)

def Main():
  (options, args) = parse_options()
  is_split = options.split
  run_aapt_pack(options.aapt, options.api, options.app)
  if is_split:
    run_aapt_split_pack(options.aapt, options.api, options.app)
  compile_with_javac(options.api, options.app)
  dex(options.app, options.api)
  dex_files = { options.app: get_dex_path(options.app)}
  dex_path = get_dex_path(options.app)
  if is_split:
    split(options.app)
    dex_path = get_split_path(options.app, 'base')

  temp_apk_path = create_temp_apk(options.app, '')
  aapt_add_dex(options.aapt, dex_path, temp_apk_path)
  apk_path = os.path.join(get_bin_path(options.app), '%s.apk' % options.app)
  apk_utils.sign(temp_apk_path, apk_path,  options.keystore)
  print('Apk available at: %s' % apk_path)

  if split:
    split_temp_apk_path = create_temp_apk(options.app, 'split_')
    aapt_add_dex(options.aapt,
                 get_split_path(options.app, 'split'),
                 temp_apk_path)
    split_apk_path = os.path.join(get_bin_path(options.app), 'featuresplit.apk')
    apk_utils.sign(temp_apk_path, split_apk_path,  options.keystore)
    print('Feature split available at: %s' % split_apk_path)



if __name__ == '__main__':
  sys.exit(Main())
