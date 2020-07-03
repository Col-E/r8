#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Run R8 on a simple Hello World program
# Report Golem-compatible RunTimeRaw values:
#
#     <NAME>-Total(RunTimeRaw): <time> ms
#
# where <NAME> is Hello{,Dex}{,Large}{,NoOpt}

import argparse
import os
import subprocess
import sys
import time
import zipfile

import golem
import jdk
import proguard
import utils

HELLO_JAR = os.path.join(utils.BUILD, 'test', 'examples', 'hello.jar')

EXTRA_INPUTS = [
  os.path.join(utils.THIRD_PARTY, 'sample_libraries', lib) for lib in [
    'animal-sniffer-annotations-1.17.jar',
    'annotations-13.0.jar',
    'checker-compat-qual-2.5.2.jar',
    'collections-28.0.0.jar',
    'common-1.1.1.jar',
    'commons-collections4-4.3.jar',
    'commons-compress-1.18.jar',
    'commons-lang3-3.8.1.jar',
    'commons-math3-3.6.1.jar',
    'constraint-layout-solver-1.1.3.jar',
    'converter-gson-2.5.0.jar',
    'dagger-2.22.1.jar',
    'error_prone_annotations-2.2.0.jar',
    'failureaccess-1.0.1.jar',
    'gson-2.8.2.jar',
    'guava-27.1-android.jar',
    'j2objc-annotations-1.1.jar',
    'javax.inject-1.jar',
    'jsr305-3.0.2.jar',
    'kotlin-stdlib-1.3.21.jar',
    'kotlin-stdlib-common-1.3.21.jar',
    'kotlin-stdlib-jdk7-1.3.21.jar',
    'listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar',
    'okhttp-3.14.0.jar',
    'okio-1.17.2.jar',
    'play-services-ads-17.2.0-javadoc.jar',
    'play-services-ads-base-17.2.0-javadoc.jar',
    'play-services-ads-lite-17.2.0-javadoc.jar',
    'play-services-analytics-16.0.8-javadoc.jar',
    'play-services-analytics-impl-16.0.8-javadoc.jar',
    'play-services-base-16.1.0-javadoc.jar',
    'play-services-basement-16.2.0-javadoc.jar',
    'play-services-cast-16.1.2-javadoc.jar',
    'play-services-drive-16.1.0-javadoc.jar',
    'play-services-fitness-16.0.1-javadoc.jar',
    'play-services-games-17.0.0-javadoc.jar',
    'play-services-gass-17.2.0-javadoc.jar',
    'play-services-gcm-16.1.0-javadoc.jar',
    'play-services-iid-16.0.1-javadoc.jar',
    'play-services-measurement-16.4.0-javadoc.jar',
    'play-services-measurement-api-16.4.0-javadoc.jar',
    'play-services-measurement-base-16.4.0-javadoc.jar',
    'play-services-measurement-impl-16.4.0-javadoc.jar',
    'play-services-measurement-sdk-16.4.0-javadoc.jar',
    'play-services-measurement-sdk-api-16.4.0-javadoc.jar',
    'play-services-tagmanager-v4-impl-16.0.8-javadoc.jar',
    'play-services-vision-17.0.2-javadoc.jar',
    'play-services-vision-common-17.0.2-javadoc.jar',
    'protobuf-lite-3.0.1.jar',
    'reactive-streams-1.0.2.jar',
    'retrofit-2.5.0.jar',
    'rxjava-2.2.8.jar',
    'support-annotations-28.0.0.jar',
  ]
]

EXTRA_KEEP_RULES = ['-dontwarn java.lang.ClassValue']

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Compile a hello world example program')
  parser.add_argument('--tool',
                      choices = ['d8', 'r8'] + proguard.getVersions(),
                      required = True,
                      help = 'Compiler tool to use.')
  parser.add_argument('--output-mode',
                      choices = ['dex', 'cf'],
                      required = True,
                      help = 'Output mode to compile to.')
  parser.add_argument('--golem',
                      help = 'Running on golem, link in third_party resources.',
                      default = False,
                      action = 'store_true')
  parser.add_argument('--large',
                      help = 'Add many additional program inputs.',
                      default = False,
                      action = 'store_true')
  parser.add_argument('--noopt',
                      help = 'Disable most optimizations/processing.',
                      default = False,
                      action = 'store_true')
  parser.add_argument('--print-memoryuse',
                      help = 'Prints the line \'<NAME>-Total(MemoryUse):'
                             ' <mem>\' at the end where <mem> is the peak'
                             ' peak resident set size (VmHWM) in bytes.',
                      default = False,
                      action = 'store_true')
  parser.add_argument('--output',
                      help = 'Output directory to keep the generated files')
  return parser.parse_args()

def GetConfRules(extra, noopt):
  rules = ['-keep class hello.Hello { void main(java.lang.String[]); }']
  if len(extra) > 0:
    rules.extend(EXTRA_KEEP_RULES)
  if noopt:
    rules.extend([
      '-dontoptimize',
      '-dontshrink',
      '-dontobfuscate',
      '-keepattributes *',
    ])
  return rules

def GetCompilerPrefix(tool, mode, output, input, lib, extra, noopt):
  return [
    jdk.GetJavaExecutable(),
    '-jar', utils.R8_JAR if tool == 'r8' else utils.D8_JAR,
    '--output', output,
    '--lib', lib,
    '--debug' if noopt else '--release',
    input,
  ] + ([] if mode == 'cf' else ['--min-api', '21']) + extra

def Compile(tool, output_mode, lib, extra, output_dir, noopt, temp_dir):
  output = os.path.join(output_dir, 'out.zip')
  if tool == 'd8':
    if output_mode != 'dex':
      raise ValueError('Invalid output mode for D8')
    classpath = []
    for cp_entry in extra:
      classpath.extend(['--classpath', cp_entry])
    return [
      GetCompilerPrefix(
        tool, output_mode, output, HELLO_JAR, lib, classpath, noopt)
    ]
  # The compilation is either R8 or PG.
  # Write keep rules to a temporary file.
  rules = GetConfRules(extra, noopt)
  rules_file = os.path.join(temp_dir, 'rules.conf')
  open(rules_file, 'w').write('\n'.join(rules))
  if tool == 'r8':
    cmd = GetCompilerPrefix(
        tool, output_mode, output, HELLO_JAR, lib, extra, noopt)
    cmd.extend(['--pg-conf', rules_file])
    if output_mode == 'cf':
      cmd.append('--classfile')
    return [cmd]
  if proguard.isValidVersion(tool):
    # Build PG invokation with additional rules to silence warnings.
    pg_out = output if output_mode == 'cf' \
      else os.path.join(output_dir, 'pgout.zip')
    cmds = [proguard.getCmd([
      '-injars', ':'.join([HELLO_JAR] + extra),
      '-libraryjars', lib,
      '-outjars', pg_out,
      '-dontwarn **',
      '@' + rules_file
    ], version=tool)]
    if output_mode == 'dex':
      cmds.append(
          GetCompilerPrefix('d8', 'dex', output, pg_out, lib, [], noopt))
    return cmds
  raise ValueError('Unknown tool: ' + tool)

def ProcessInput(input, tmp_dir):
  if not input.endswith('.aar'):
    return input
  out_dir = os.path.join(tmp_dir, input)
  os.makedirs(out_dir)
  zip = zipfile.ZipFile(input, 'r')
  zip.extractall(out_dir)
  zip.close()
  return os.path.join(out_dir, 'classes.jar')

def Main():
  args = parse_arguments()
  if args.golem:
    golem.link_third_party()
  utils.check_java_version()

  with utils.TempDir() as temp_dir:
    cmd_prefix = []
    output_dir = args.output if args.output else temp_dir
    temp_dir = os.path.join(args.output, 'tmp') if args.output else temp_dir

    track_memory_file = None
    if args.print_memoryuse:
      track_memory_file = os.path.join(output_dir, utils.MEMORY_USE_TMP_FILE)
      cmd_prefix.extend(['tools/track_memory.sh', track_memory_file])

    name = 'CompileHelloExample'

    tool = args.tool
    output_mode = args.output_mode
    lib = None
    if output_mode == 'dex':
      name += 'Dex'
      lib = utils.get_android_jar(28)
    else:
      lib = utils.RT_JAR

    extra = []
    if args.large:
      name += 'Large'
      extra = EXTRA_INPUTS

    if args.noopt:
      name += 'NoOpt'

    cmds = Compile(
      tool,
      output_mode,
      lib,
      extra,
      output_dir,
      args.noopt,
      temp_dir,
    )

    t0 = time.time()
    for cmd in cmds:
      fullcmd = cmd_prefix + cmd
      utils.PrintCmd(fullcmd)
      subprocess.check_output(fullcmd)
    dt = time.time() - t0

    if args.print_memoryuse:
      print('{}(MemoryUse): {}'
            .format(name, utils.grep_memoryuse(track_memory_file)))

    print('{}(RunTimeRaw): {} ms'
          .format(name, 1000.0 * dt))

if __name__ == '__main__':
  sys.exit(Main())
