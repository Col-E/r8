#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import compiledump
import gradle
import jdk
import utils

GOLEM_BUILD_TARGETS_TESTS = [
    utils.GRADLE_TASK_ALL_TESTS_WITH_APPLY_MAPPING_JAR,
    utils.GRADLE_TASK_TEST_DEPS_JAR
]
GOLEM_BUILD_TARGETS = [utils.GRADLE_TASK_R8LIB] + GOLEM_BUILD_TARGETS_TESTS


def get_golem_resource_path(benchmark):
    return os.path.join('benchmarks', benchmark)


def get_jdk_home(options, benchmark):
    if options.golem:
        return os.path.join(get_golem_resource_path(benchmark), 'linux')
    return None


def parse_options(argv):
    result = argparse.ArgumentParser(description='Run test-based benchmarks.')
    result.add_argument('--golem',
                        help='Indicate this as a run on golem',
                        default=False,
                        action='store_true')
    result.add_argument('--benchmark',
                        help='The test benchmark to run',
                        required=True)
    result.add_argument(
        '--target',
        help='The test target to run',
        required=True,
        # These should 1:1 with benchmarks/BenchmarkTarget.java
        choices=['d8', 'r8-full', 'r8-force', 'r8-compat'])
    result.add_argument('--nolib',
                        '--no-lib',
                        '--no-r8lib',
                        help='Run the non-lib R8 build (default false)',
                        default=False,
                        action='store_true')
    result.add_argument('--no-build',
                        '--no_build',
                        help='Run without building first (default false)',
                        default=False,
                        action='store_true')
    result.add_argument('--enable-assertions',
                        '--enable_assertions',
                        '-ea',
                        help='Enable assertions when running',
                        default=False,
                        action='store_true')
    result.add_argument('--print-times',
                        help='Print timing information from r8',
                        default=False,
                        action='store_true')
    result.add_argument(
        '--version',
        '-v',
        help='Use R8 version/hash for the run (default local build)',
        default=None)
    result.add_argument('--temp',
                        help='A directory to use for temporaries and outputs.',
                        default=None)
    return result.parse_known_args(argv)


def main(argv, temp):
    (options, args) = parse_options(argv)

    if options.temp:
        temp = options.temp

    if options.golem:
        options.no_build = True
        if options.nolib:
            print("Error: golem should always run r8lib")
            return 1

    if options.nolib:
        testBuildTargets = [
            utils.GRADLE_TASK_TEST_JAR, utils.GRADLE_TASK_TEST_DEPS_JAR
        ]
        buildTargets = [utils.GRADLE_TASK_R8] + testBuildTargets
        r8jar = utils.R8_JAR
        testjars = [utils.R8_TESTS_JAR, utils.R8_TESTS_DEPS_JAR]
    else:
        testBuildTargets = GOLEM_BUILD_TARGETS_TESTS
        buildTargets = GOLEM_BUILD_TARGETS
        r8jar = utils.R8LIB_JAR
        testjars = [
            os.path.join(utils.R8LIB_TESTS_JAR),
            os.path.join(utils.R8LIB_TESTS_DEPS_JAR)
        ]

    if options.version:
        # r8 is downloaded so only test jar needs to be built.
        buildTargets = testBuildTargets
        r8jar = compiledump.download_distribution(options.version,
                                                  options.nolib, temp)

    if not options.no_build:
        gradle.RunGradle(buildTargets + ['-Pno_internal'])

    if not options.golem:
        # When running locally, change the working directory to be in 'temp'.
        # This is hard to do properly within the JVM so we do it here.
        with utils.ChangedWorkingDirectory(temp):
            return run(options, r8jar, testjars)
    else:
        return run(options, r8jar, testjars)


def run(options, r8jar, testjars):
    jdkhome = get_jdk_home(options, options.benchmark)
    cmd = [jdk.GetJavaExecutable(jdkhome)]
    if options.enable_assertions:
        cmd.append('-ea')
    if options.print_times:
        cmd.append('-Dcom.android.tools.r8.printtimes=1')
    if not options.golem:
        cmd.extend([
            f'-DTEST_DATA_LOCATION={utils.REPO_ROOT}/d8_r8/test_modules/tests_java_8/build/classes/java/test'
        ])
    cmd.extend(['-cp', ':'.join([r8jar] + testjars)])
    cmd.extend([
        'com.android.tools.r8.benchmarks.BenchmarkMainEntryRunner',
        options.benchmark,
        options.target,
        # When running locally the working directory is moved and we pass the
        # repository root as an argument. The runner can then setup dependencies.
        'golem' if options.golem else utils.REPO_ROOT,
    ])
    return subprocess.check_call(cmd)


if __name__ == '__main__':
    with utils.TempDir() as temp:
        sys.exit(main(sys.argv[1:], temp))
