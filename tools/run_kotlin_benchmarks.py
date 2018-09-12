#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script for running kotlin based benchmarks

import golem
import optparse
import os
import subprocess
import sys
import toolhelper
import utils


BENCHMARK_ROOT = os.path.join(utils.REPO_ROOT, 'third_party', 'benchmarks',
                              'kotlin-benches')

BENCHMARK_PATTERN = '{benchmark}/kotlin/perf/build/libs/perf-1.0-BENCH.jar'
BENCHMARK_MAIN_CLASS = 'com.android.kt.bms.cli.Runner'
ART = os.path.join(utils.TOOLS_DIR, 'linux', 'art', 'bin', 'art')

PROGUARD_CONF = """
# From Android rules
-keepclasseswithmembers public class * {
  public static void main(java.lang.String[]);
}
# Disable obfuscation to only focus on shrinking
-dontobfuscate
# Once we're ready for optimization, we might want to relax access modifiers.
-allowaccessmodification
"""

DEVICE_TEMP='/data/local/temp/bench'


def parse_options():
  result = optparse.OptionParser()
  result.add_option('--api',
                    help='Android api level',
                    default='26',
                    choices=['21', '22', '23', '24', '25', '26'])
  result.add_option('--benchmark',
                    help='The benchmark to run',
                    default='rgx',
                    choices=['rgx', 'deltablue', 'sta', 'empty'])
  result.add_option('--golem',
                    help='Don\'t build r8 and link in third_party deps',
                    default=False, action='store_true')
  result.add_option('--use-device',
                    help='Run the benchmark on an attaced device',
                    default=False, action='store_true')
  return result.parse_args()


def get_jar_for_benchmark(benchmark):
  return os.path.join(BENCHMARK_ROOT,
                      BENCHMARK_PATTERN.format(benchmark=benchmark))

def run_art(dex):
  command = ['bash', ART, '-cp', dex, BENCHMARK_MAIN_CLASS]
  utils.PrintCmd(command)
  benchmark_output = subprocess.check_output(command)
  return get_result(benchmark_output)

def adb(args):
  command = ['adb'] + args
  utils.PrintCmd(command)
  return subprocess.check_output(['adb'] + args)

def get_result(output):
  # There is a lot of debug output, with the actual results being in the line with:
  # RESULTS,KtBench,KtBench,15719
  # structure.
  for result in [s for s in output.splitlines() if s.startswith('RESULTS')]:
    return s.split('RESULTS,KtBench,KtBench,')[1]

def run_art_device(dex):
  adb(['wait-for-device', 'root'])
  device_dst = os.path.join(DEVICE_TEMP, os.path.basename(dex))
  adb(['push', dex, device_dst])
  benchmark_output = adb(['shell', 'dalvikvm', '-cp', device_dst, BENCHMARK_MAIN_CLASS])
  return get_result(benchmark_output)

def Main():
  (options, args) = parse_options()
  if options.golem:
    golem.link_third_party()
  with utils.TempDir() as temp:
    dex_path = os.path.join(temp, "classes.jar")
    proguard_conf = os.path.join(temp, 'proguard.conf')
    with open(proguard_conf, 'w') as f:
      f.write(PROGUARD_CONF)
    benchmark_jar = get_jar_for_benchmark(options.benchmark)
    r8_args = [
        '--lib', utils.get_android_jar(26), # Only works with api 26
        '--output', dex_path,
        '--pg-conf', proguard_conf,
        '--min-api', str(options.api),
        benchmark_jar
    ]
    toolhelper.run('r8', r8_args, build=not options.golem)
    if options.use_device:
      result = run_art_device(dex_path)
    else:
      result = run_art(dex_path)
    print('Kotlin_{}(RunTimeRaw): {} ms'.format(options.benchmark, result))

if __name__ == '__main__':
  sys.exit(Main())
