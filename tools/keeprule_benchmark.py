#!/usr/bin/env python
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys
import time

import jdk
import proguard
import toolhelper
import utils

SHRINKERS = ['r8'] + proguard.getVersions()

INPUT_PROGRAM = utils.PINNED_R8_JAR

ANNO = 'com.android.tools.r8.com.google.common.annotations.VisibleForTesting'

R8_OPTIONS = [
  'printTimes',
  'passthroughDexCode',
  'enableClassMerging',
  'enableDevirtualization',
  'enableNonNullTracking',
  'enableInlining',
  'enableSwitchMapRemoval',
  'enableValuePropagation',
  'useSmaliSyntax',
  'verbose',
  'quiet',
  'invalidDebugInfoFatal',
  'intermediate',
  'enableLambdaMerging',
  'enableDesugaring',
  'enableMainDexListCheck',
  'enableTreeShaking',
  'printCfg',
  'ignoreMissingClasses',
  'forceProguardCompatibility',
  'enableMinification',
  'disableAssertions',
  'debugKeepRules',
  'debug',
  'minimalMainDex',
  'skipReadingDexCode',
]

R8_CLASSES = [
  'com.android.tools.r8.code.Format11x',
  'com.android.tools.r8.code.MoveFrom16',
  'com.android.tools.r8.code.AddLong2Addr',
  'com.android.tools.r8.code.AgetByte',
  'com.android.tools.r8.code.SubDouble',
  'com.android.tools.r8.code.Sput',
  'com.android.tools.r8.code.Format10x',
  'com.android.tools.r8.code.RemInt',
  'com.android.tools.r8.code.ConstWide',
  'com.android.tools.r8.code.SgetWide',
  'com.android.tools.r8.code.OrInt2Addr',
  'com.android.tools.r8.code.Iget',
  'com.android.tools.r8.code.Instruction',
  'com.android.tools.r8.code.SubInt2Addr',
  'com.android.tools.r8.code.SwitchPayload',
  'com.android.tools.r8.code.Const4',
  'com.android.tools.r8.code.ShrIntLit8',
  'com.android.tools.r8.code.ConstWide16',
  'com.android.tools.r8.code.NegInt',
  'com.android.tools.r8.code.SgetBoolean',
  'com.android.tools.r8.code.Format22x',
  'com.android.tools.r8.code.InvokeVirtualRange',
  'com.android.tools.r8.code.Format45cc',
  'com.android.tools.r8.code.DivFloat2Addr',
  'com.android.tools.r8.code.MulIntLit16',
  'com.android.tools.r8.code.BytecodeStream',
]

KEEP_MAIN = \
  '-keep class com.android.tools.r8.R8 { void main(java.lang.String[]); }'

BENCHMARKS = [
  # Baseline compile just keeps R8.main (implicitly kept for all benchmarks).
  ('KeepBaseline', ''),

  # Mirror default keep getters/setters, but independent of hierarchy.
  ('KeepGetters',
    '-keepclassmembers class * { *** get*(); }'),
  ('KeepGettersIf',
    '-if class * { *** get*(); } -keep class <1> { *** get<2>(); }'),

  # Mirror default keep getters/setters below View (here a class with a B).
  ('KeepSubGetters',
    '-keepclassmembers class * extends **.*B* { *** get*(); }'),
  ('KeepSubGettersIf',
    '-if class * extends **.*B* -keep class <1> { *** get*(); }'),

  # General keep rule to keep annotated members.
  ('KeepAnnoMethod',
    '-keepclasseswithmembers class * { @%s *** *(...); }' % ANNO),
  ('KeepAnnoMethodCond',
    '-keepclassmembers class * { @%s *** *(...); }' % ANNO),
  ('KeepAnnoMethodIf',
    '-if class * { @%s *** *(...); } -keep class <1> { @%s *** <2>(...); }' \
        % (ANNO, ANNO)),

  # Large collection of rules mirroring AAPT conditional rules on R fields.
  ('KeepAaptFieldIf',
   '\n'.join([
     '-if class **.InternalOptions { boolean %s; }'
     ' -keep class %s { <init>(...); }' % (f, c)
     for (f, c) in zip(R8_OPTIONS, R8_CLASSES) * 1 #100
   ])),

  # If rules with predicates that will never by true, but will need
  # consideration. The CodeSize of these should be equal to the baseline run.
  ('KeepIfNonExistingClass',
   '-if class **.*A*B*C*D*E*F* -keep class %s' % ANNO),
  ('KeepIfNonExistingMember',
   '-if class **.*A* { *** *a*b*c*d*e*f*(...); } -keep class %s' % ANNO)
]

def parse_arguments(argv):
  parser = argparse.ArgumentParser(
                    description = 'Run keep-rule benchmarks.')
  parser.add_argument('--golem',
                    help = 'Link in third party dependencies.',
                    default = False,
                    action = 'store_true')
  parser.add_argument('--ignore-java-version',
                    help='Do not check java version',
                    default=False,
                    action='store_true')
  parser.add_argument('--shrinker',
                    help='The shrinker to use',
                    choices=SHRINKERS,
                    default=SHRINKERS[0])
  parser.add_argument('--runs',
                      help='Number of runs to average out time on',
                      type=int,
                      default=3)
  parser.add_argument('--benchmark',
                      help='Benchmark to run (default all)',
                      choices=map(lambda (x,y): x, BENCHMARKS),
                      default=None)
  options = parser.parse_args(argv)
  return options

class BenchmarkResult:
  def __init__(self, name, size, runs):
    self.name = name
    self.size = size
    self.runs = runs

def isPG(shrinker):
  return proguard.isValidVersion(shrinker)

def shrinker_args(shrinker, keepfile, output):
  if shrinker == 'r8':
    return [
      jdk.GetJavaExecutable(),
      '-cp', utils.R8LIB_JAR,
      'com.android.tools.r8.R8',
      INPUT_PROGRAM,
      '--lib', utils.RT_JAR,
      '--output', output,
      '--min-api', '10000',
      '--pg-conf', keepfile,
      ]
  elif isPG(shrinker):
    return proguard.getCmd([
      '-injars', INPUT_PROGRAM,
      '-libraryjars', utils.RT_JAR,
      '-outjars', output,
      '-dontwarn', '**',
      '-optimizationpasses', '2',
      '@' + keepfile,
    ],
    version=shrinker)
  else:
    assert False, "Unexpected shrinker " + shrinker

def dex(input, output):
  toolhelper.run(
      'd8',
      [
        input,
        '--lib', utils.RT_JAR,
        '--min-api', '10000',
        '--output', output
      ],
      build=False,
      debug=False)

def run_shrinker(options, temp):
  benchmarks = BENCHMARKS
  if options.benchmark:
    for (name, rules) in BENCHMARKS:
      if name == options.benchmark:
        benchmarks = [(name, rules)]
        break
    assert len(benchmarks) == 1, "Unexpected benchmark " + options.benchmark

  run_count = options.runs
  benchmark_results = []
  for (name, rule) in benchmarks:
    benchmark_keep = os.path.join(temp, '%s-keep.txt' % name)
    with open(benchmark_keep, 'w') as fp:
      fp.write(KEEP_MAIN)
      fp.write('\n')
      fp.write(rule)

    benchmark_runs = []
    benchmark_size = 0
    for i in range(run_count):
      out = os.path.join(temp, '%s-out%d.jar' % (name, i))
      cmd = shrinker_args(options.shrinker, benchmark_keep, out)
      utils.PrintCmd(cmd)
      t0 = time.time()
      subprocess.check_output(cmd)
      t1 = time.time()
      benchmark_runs.append(t1 - t0)
      if isPG(options.shrinker):
        dexout = os.path.join(temp, '%s-out%d-dex.jar' % (name, i))
        dex(out, dexout)
        benchmark_size = utils.uncompressed_size(dexout)
      else:
        benchmark_size = utils.uncompressed_size(out)
    benchmark_results.append(
        BenchmarkResult(name, benchmark_size, benchmark_runs))

  print 'Runs:', options.runs
  for result in benchmark_results:
    benchmark_avg = sum(result.runs) / run_count
    print '%s(CodeSize): %d' % (result.name, result.size)
    print '%s(RunTimeRaw): %d ms' % (result.name, 1000.0 * benchmark_avg)

if __name__ == '__main__':
  options = parse_arguments(sys.argv[1:])
  if options.golem:
    golem.link_third_party()
  if not options.ignore_java_version:
    utils.check_java_version()
  with utils.TempDir() as temp:
    run_shrinker(options, temp)
