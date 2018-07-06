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
import time
import utils
import uuid

ANDROID_JAR = 'third_party/android_jar/lib-v{api}/android.jar'
DEFAULT_AAPT = 'aapt' # Assume in path.
DEFAULT_D8 = os.path.join(utils.REPO_ROOT, 'tools', 'd8.py')
DEFAULT_DEXSPLITTER = os.path.join(utils.REPO_ROOT, 'tools', 'dexsplitter.py')
DEFAULT_JAVAC = 'javac'
SRC_LOCATION = 'src/com/android/tools/r8/sample/{app}/*.java'
DEFAULT_KEYSTORE = os.path.join(os.getenv('HOME'), '.android', 'debug.keystore')
PACKAGE_PREFIX = 'com.android.tools.r8.sample'
STANDARD_ACTIVITY = "R8Activity"
BENCHMARK_ITERATIONS = 30

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
  result.add_option('--install',
                    help='Install the app (including featuresplit)',
                    default=False, action='store_true')
  result.add_option('--benchmark',
                    help='Benchmark the app on the phone with specialized markers',
                    default=False, action='store_true')
  result.add_option('--benchmark-output-dir',
                    help='Store benchmark results here.',
                    default=None)
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

def get_package_name(app):
  return '%s.%s' % (PACKAGE_PREFIX, app)

def get_qualified_activity(app):
  # The activity specified to adb start is PACKAGE_NAME/.ACTIVITY
  return '%s/.%s' % (get_package_name(app), STANDARD_ACTIVITY)

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

def run_adb(args, ignore_exit=False):
  command = ['adb']
  command.extend(args)
  utils.PrintCmd(command)
  # On M adb install-multiple exits 1 but succeed in installing.
  if ignore_exit:
    subprocess.call(command)
  else:
    subprocess.check_call(command)

def adb_install(apks):
  args = [
      'install-multiple' if len(apks) > 1 else 'install',
      '-r',
      '-d']
  args.extend(apks)
  run_adb(args, ignore_exit=True)

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

def kill(app):
  args = ['shell', 'am', 'force-stop', get_package_name(app)]
  run_adb(args)

def start_logcat():
  return subprocess.Popen(['adb', 'logcat'], bufsize=1024*1024, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

def start(app):
  args = ['shell', 'am', 'start', '-n', get_qualified_activity(app)]
  run_adb(args)

def clear_logcat():
  args = ['logcat', '-c']
  run_adb(args)

def stop_logcat(popen):
  popen.terminate()
  lines = []
  for l in popen.stdout:
    if 'System.out' in l:
      lines.append(l)
  return lines

def store_or_print_benchmarks(lines, output):
  results = {}
  overall_total = 0
  # We assume that the total times are
  # prefixed with 'NAME Total: '. The logcat lines looks like:
  # 06-28 12:22:00.991 13698 13698 I System.out: Call Total: 61614
  for l in lines:
    if 'Total: ' in l:
      split = l.split('Total: ')
      time = split[1]
      name = split[0].split()[-1]
      overall_total += int(time)
      print '%s: %s' % (name, time)
      results[name] = time

  print 'Total: %s' % overall_total
  if not output:
    return overall_total
  results['total'] = str(overall_total)
  output_dir = os.path.join(output, str(uuid.uuid4()))
  os.makedirs(output_dir)
  written_files = []
  for name, time in results.iteritems():
    total_file = os.path.join(output_dir, name)
    written_files.append(total_file)
    with open(total_file, 'w') as f:
      f.write(time)

  print 'Result stored in: \n%s' % ('\n'.join(written_files))
  return overall_total

def benchmark(app, output_dir):
  # Ensure app is not running
  kill(app)
  clear_logcat()
  logcat = start_logcat()
  start(app)
  # We could do better here by continiously parsing the logcat for a marker, but
  # this works nicely with the current setup.
  time.sleep(8)
  kill(app)
  return float(store_or_print_benchmarks(stop_logcat(logcat), output_dir))

def ensure_no_logcat():
  output = subprocess.check_output(['ps', 'aux'])
  if 'adb logcat' in output:
    raise Exception('You have adb logcat running, please close it and rerun')

def Main():
  (options, args) = parse_options()
  apks = []
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
  apks.append(apk_path)

  if is_split:
    split_temp_apk_path = create_temp_apk(options.app, 'split_')
    aapt_add_dex(options.aapt,
                 get_split_path(options.app, 'split'),
                 temp_apk_path)
    split_apk_path = os.path.join(get_bin_path(options.app), 'featuresplit.apk')
    apk_utils.sign(temp_apk_path, split_apk_path,  options.keystore)
    apks.append(split_apk_path)

  print('Generated apks available at: %s' % ' '.join(apks))
  if options.install or options.benchmark:
    adb_install(apks)
  grand_total = 0
  if options.benchmark:
    ensure_no_logcat()
    for _ in range(BENCHMARK_ITERATIONS):
      grand_total += benchmark(options.app, options.benchmark_output_dir)
  print 'Combined average: %s' % (grand_total/BENCHMARK_ITERATIONS)

if __name__ == '__main__':
  sys.exit(Main())
