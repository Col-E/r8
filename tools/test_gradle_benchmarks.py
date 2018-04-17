#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from __future__ import print_function
import argparse
import os
import sys
import utils
import gradle
from enum import Enum

BENCHMARKS_ROOT_DIR = os.path.join(utils.REPO_ROOT, 'third_party', 'benchmarks')

def parse_arguments():
  parser = argparse.ArgumentParser(
    description='Run D8 or DX on gradle apps located in'
                ' third_party/benchmarks/.'
                ' Report Golem-compatible RunTimeRaw values.')
  result.add_option('--skip_download',
                    help='Don\'t automatically pull down dependencies.',
                    default=False, action='store_true')
  parser.add_argument('--tool',
                      choices=['dx', 'd8'],
                      required=True,
                      help='Compiler tool to use.')

  parser.add_argument('--benchmark',
                      help='Which benchmark to run, default all')
  return parser.parse_args()

class Benchmark:
  class Tools(Enum):
    D8 = 1
    DX = 2

  class DesugarMode(Enum):
    D8_DESUGARING = 1
    DESUGAR_TOOL = 2

  displayName = ""
  rootDirPath = ""
  appPath = ""
  moduleName = ""
  buildCommand = ""
  cleanCommand = ""
  env = {}

  def __init__(self, displayName, benchmarkDir, moduleName, buildCommand,
               cleanCommand):
    self.displayName = displayName
    self.rootDirPath = os.path.join(BENCHMARKS_ROOT_DIR,
                                    benchmarkDir.split(os.sep)[0])
    self.appPath = os.path.join(BENCHMARKS_ROOT_DIR, benchmarkDir)
    self.moduleName = moduleName
    self.buildCommand = buildCommand
    self.cleanCommand = cleanCommand
    self.env = os.environ.copy()
    self.env["ANDROID_HOME"] = os.path.join(utils.REPO_ROOT, 'third_party',
                                            'benchmarks', 'android-sdk')

  def RunGradle(self, command, tool, desugarMode):

    args = ['-Dr8.root.dir=' + utils.REPO_ROOT, '--init-script',
            os.path.join(BENCHMARKS_ROOT_DIR, 'init-script.gradle')]

    if tool == self.Tools.D8:
      args.append('-Dandroid.enableD8=true')
    elif tool == self.Tools.DX:
      args.append('-Dandroid.enableD8=false')
    else:
      raise AssertionError("Unknown tool: " + repr(tool))

    if desugarMode == self.DesugarMode.D8_DESUGARING:
      args.append('-Dandroid.enableDesugar=false')
    elif desugarMode == self.DesugarMode.DESUGAR_TOOL:
      args.append('-Dandroid.enableDesugar=true')
    else:
      raise AssertionError("Unknown desugar mode: " + repr(desugarMode))

    args.extend(command)

    return gradle.RunGradleWrapperInGetOutput(args, self.appPath, env=self.env)

  def Build(self, tool, desugarMode):
    return self.RunGradle(self.buildCommand, tool, desugarMode)

  def Clean(self):
    # tools and desugar mode not relevant for clean
    return self.RunGradle(self.cleanCommand,
                          self.Tools.D8,
                          self.DesugarMode.D8_DESUGARING)

  def EnsurePresence(self):
    EnsurePresence(self.rootDirPath, self.displayName)

def EnsurePresence(dir, displayName):
  if not os.path.exists(dir) or os.path.getmtime(dir + '.tar.gz')\
          < os.path.getmtime(dir + '.tar.gz.sha1'):
    utils.DownloadFromX20(dir + '.tar.gz.sha1')
    # Update the mtime of the tar file to make sure we do not run again unless
    # there is an update.
    os.utime(dir + '.tar.gz', None)
  else:
    print('test_gradle_benchmarks.py: benchmark {} is present'.format(displayName))

def TaskFilter(taskname):
  acceptedGradleTasks = [
    'dex',
    'Dex',
    'proguard',
    'Proguard',
    'kotlin',
    'Kotlin',
  ]

  return any(namePattern in taskname for namePattern in acceptedGradleTasks)

def PrintBuildTimeForGolem(benchmark, stdOut):
  for line in stdOut.splitlines():
    if 'BENCH' in line and benchmark.moduleName in line:
      commaSplit = line.split(',')
      assert len(commaSplit) == 3

      # Keep only module that have been configured to use R8
      if benchmark.moduleName + ':' not in commaSplit[1]:
        continue

      # remove <module-name> + ':'
      taskName = commaSplit[1][(len(benchmark.moduleName) + 1):]

      # Just a temporary assumption.
      # This means we have submodules, so we'll need to check their
      # configuration so that the right r8/d8 is taken. For now it shouldn't
      # be the case.
      assert taskName.find(':') == -1, taskName

      if TaskFilter(taskName):
        # taskName looks like:
        #  transformClassesWithDexBuilderForDevelopmentDebug
        # we don't want unimportant information in UI, so we strip it down to:
        #  ClassesDexBuilderDevelopment
        # Output example:
        # SantaTracker-ClassesDexBuilderDevelopment(RunTimeRaw): 748 ms
        assert taskName.startswith('transform')
        taskName = taskName[len('transform'):]
        taskName = taskName.replace('With', '')
        taskName = taskName.replace('For', '')
        taskName = taskName.replace('Default', '')
        benchmarkName = benchmark.displayName + '-' + taskName
        print('{}(RunTimeRaw): {} ms'.format(benchmarkName, commaSplit[2]))

def Main():
  args = parse_arguments()

  if args.tool == 'd8':
    tool = Benchmark.Tools.D8
    desugarMode = Benchmark.DesugarMode.D8_DESUGARING
  else:
    tool = Benchmark.Tools.DX
    desugarMode = Benchmark.DesugarMode.DESUGAR_TOOL

  buildTimeBenchmarks = [
    Benchmark('AntennaPod',
              os.path.join('antenna-pod', 'AntennaPod'),
              ':app',
              [':app:assembleDebug'],
              ['clean']),
    Benchmark('Maps',
              'gradle-java-1.6',
              ':maps',
              [':maps:assembleDebug', '--settings-file',
               'settings.gradle.maps'],
              ['clean']),
    Benchmark('Music2',
              'gradle-java-1.6',
              ':music2Old',
              [':music2Old:assembleDebug', '--settings-file',
               'settings.gradle.music2Old'],
              ['clean']),
    Benchmark('Velvet',
              'gradle-java-1.6',
              ':velvet',
              [':velvet:assembleDebug', '--settings-file',
               'settings.gradle.velvet'],
              ['clean']),
    Benchmark('SantaTracker',
              'santa-tracker',
              ':santa-tracker',
              [':santa-tracker:assembleDebug'],
              ['clean']),

    # disabled for now, apparently because of b/74227571
    # Benchmark('Tachiyomi',
    #           'tachiyomi',
    #           ':app',
    #           ['assembleStandardDebug'],
    #           ['clean']),

    Benchmark('WordPress',
              'wordpress',
              ':WordPress',
              ['assembleVanillaDebug'],
              ['clean']),

  ]
  if not args.skip_download:
    EnsurePresence(os.path.join('third_party', 'benchmarks', 'android-sdk'),
                   'android SDK')
    EnsurePresence(os.path.join('third_party', 'gradle-plugin'),
                   'Android Gradle plugin')
  toRun = buildTimeBenchmarks
  if args.benchmark:
    toRun = [b for b in toRun if b.displayName == args.benchmark]
    if len(toRun) != 1:
      raise AssertionError("Unknown benchmark: " + args.benchmark)
  for benchmark in toRun:
    if not args.skip_download:
      benchmark.EnsurePresence()
    benchmark.Clean()
    stdOut = benchmark.Build(tool, desugarMode)
    PrintBuildTimeForGolem(benchmark, stdOut)


if __name__ == '__main__':
  sys.exit(Main())
