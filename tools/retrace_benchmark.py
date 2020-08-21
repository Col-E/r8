#!/usr/bin/env python
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys
import time

import golem
import jdk
import proguard
import utils

RETRACERS = ['r8', 'proguard', 'remapper']

def parse_arguments(argv):
  parser = argparse.ArgumentParser(
                    description = 'Run r8 retrace bootstrap benchmarks.')
  parser.add_argument('--golem',
                    help = 'Link in third party dependencies.',
                    default = False,
                    action = 'store_true')
  parser.add_argument('--ignore-java-version',
                    help='Do not check java version',
                    default=False,
                    action='store_true')
  parser.add_argument('--print-runtimeraw',
                    metavar='BENCHMARKNAME',
                    help='Print the line \'<BENCHMARKNAME>(RunTimeRaw):' +
                        ' <elapsed> ms\' at the end where <elapsed> is' +
                        ' the elapsed time in milliseconds.')
  parser.add_argument('--retracer',
                    help='The retracer to use',
                    choices=RETRACERS,
                    required=True)
  parser.add_argument('--download-benchmarks',
                      help='Download retrace benchmarks',
                      default=False,
                      action='store_true')
  options = parser.parse_args(argv)
  return options

def download_benchmarks():
  utils.DownloadFromGoogleCloudStorage(
      os.path.join(utils.THIRD_PARTY, 'retrace_benchmark') + '.tar.gz.sha1')

def run_retrace(options, temp):
  if options.download_benchmarks:
    download_benchmarks()
  if options.retracer == 'r8':
    retracer_args = [
        '-cp', utils.R8LIB_JAR, 'com.android.tools.r8.retrace.Retrace']
  elif options.retracer == 'proguard':
    retracer_args = ['-jar', proguard.getRetraceJar()]
  elif options.retracer == 'remapper':
    retracer_args = ['-jar',
                     os.path.join(
                        utils.THIRD_PARTY,
                        'remapper',
                        'remapper_deploy.jar')]
  else:
    assert False, "Unexpected retracer " + options.retracer
  retrace_args = [jdk.GetJavaExecutable()] + retracer_args + [
    os.path.join(utils.THIRD_PARTY, 'retrace_benchmark', 'r8lib.jar.map'),
    os.path.join(utils.THIRD_PARTY, 'retrace_benchmark', 'stacktrace.txt')]
  utils.PrintCmd(retrace_args)
  t0 = time.time()
  subprocess.check_call(retrace_args)
  t1 = time.time()
  if options.print_runtimeraw:
    print('{}(RunTimeRaw): {} ms'
        .format(options.print_runtimeraw, 1000.0 * (t1 - t0)))


if __name__ == '__main__':
  options = parse_arguments(sys.argv[1:])
  if options.golem:
    golem.link_third_party()
  if not options.ignore_java_version:
    utils.check_java_version()
  with utils.TempDir() as temp:
    run_retrace(options, temp)
