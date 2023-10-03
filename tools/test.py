#!/usr/bin/env python3
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for running tests. If no argument is given run all tests,
# if an argument is given, run only tests with that pattern. This script will
# force the tests to run, even if no input changed.

import argparse
import os
import shutil
import subprocess
import sys
import time
import uuid

import archive_desugar_jdk_libs
import download_kotlin_dev
import gradle
import notify
import testing_state
import utils

if utils.is_python3():
  import threading
else:
  import thread

ALL_ART_VMS = [
    "default",
    "14.0.0",
    "13.0.0",
    "12.0.0",
    "10.0.0",
    "9.0.0",
    "8.1.0",
    "7.0.0",
    "6.0.1",
    "5.1.1",
    "4.4.4",
    "4.0.4"]

# How often do we check for progress on the bots:
# Should be long enough that a normal run would always have med progress
# Should be short enough that we ensure that two calls are close enough
# to happen before bot times out.
# A false positiv, i.e., printing the stacks of non hanging processes
# is not a problem, no harm done except some logging in stdout.
TIMEOUT_HANDLER_PERIOD = 60 * 18

BUCKET = 'r8-test-results'

NUMBER_OF_TEST_REPORTS = 5
REPORTS_PATH = os.path.join(utils.BUILD, 'reports')
REPORT_INDEX = ['tests', 'test', 'index.html']
VALID_RUNTIMES = [
  'none',
  'jdk8',
  'jdk9',
  'jdk11',
  'jdk17',
  'jdk20',
] + [ 'dex-%s' % dexvm for dexvm in ALL_ART_VMS ]

def ParseOptions():
  result = argparse.ArgumentParser()
  result.add_argument('--no-internal', '--no_internal',
      help='Do not run Google internal tests.',
      default=False, action='store_true')
  result.add_argument('--archive-failures', '--archive_failures',
      help='Upload test results to cloud storage on failure.',
      default=False, action='store_true')
  result.add_argument('--archive-failures-file-name',
                    '--archive_failures_file_name',
                    help='Set file name for the archived failures file name',
                    default=uuid.uuid4())
  result.add_argument('--only-internal', '--only_internal',
      help='Only run Google internal tests.',
      default=False, action='store_true')
  result.add_argument('--all-tests', '--all_tests',
      help='Run tests in all configurations.',
      default=False, action='store_true')
  result.add_argument('--slow-tests', '--slow_tests',
      help='Also run slow tests.',
      default=False, action='store_true')
  result.add_argument('-v', '--verbose',
      help='Print test stdout to, well, stdout.',
      default=False, action='store_true')
  result.add_argument('--dex-vm', '--dex_vm',
      help='The android version of the vm to use. "all" will run the tests on '
           'all art vm versions (stopping after first failed execution)',
      default="default",
      choices=ALL_ART_VMS + ["all"])
  result.add_argument('--dex-vm-kind', '--dex_vm_kind',
                    help='Whether to use host or target version of runtime',
                    default="host",
                    nargs=1,
                    choices=["host", "target"])
  result.add_argument('--one-line-per-test', '--one_line_per_test',
      help='Print a line before a tests starts and after it ends to stdout.',
      default=False, action='store_true')
  result.add_argument('--tool',
      help='Tool to run ART tests with: "r8" (default) or "d8" or "r8cf"'
          ' (r8 w/CF-backend). Ignored if "--all_tests" enabled.',
      default=None, choices=["r8", "d8", "r8cf"])
  result.add_argument('--disable-assertions', '--disable_assertions', '-da',
      help='Disable Java assertions when running the compiler '
           '(default enabled)',
      default=False, action='store_true')
  result.add_argument('--with-code-coverage', '--with_code_coverage',
      help='Enable code coverage with Jacoco.',
      default=False, action='store_true')
  result.add_argument('--test-dir', '--test_dir',
      help='Use a custom directory for the test artifacts instead of a'
          ' temporary (which is automatically removed after the test).'
          ' Note that the directory will not be cleared before the test.')
  result.add_argument('--command-cache-dir', '--command_cache_dir',
      help='Cache command invocations to this directory, speeds up test runs',
      default=os.environ.get('R8_COMMAND_CACHE_DIR'))
  result.add_argument('--command-cache-stats', '--command_cache_stats',
      help='Collect and print statistics about the command cache.',
      default=False, action='store_true')
  result.add_argument('--java-home', '--java_home',
      help='Use a custom java version to run tests.')
  result.add_argument('--java-max-memory-size', '--java_max_memory_size',
      help='Set memory for running tests, default 4G',
      default=os.environ.get('R8_JAVA_MAX_MEMORY_SIZE', '4G'))
  result.add_argument('--test-namespace', '--test_namespace',
      help='Only run tests in  this namespace. The namespace is relative to '
          'com/android/tools/r8, e.g., desugar/desugaredlibrary',
      default=None)
  result.add_argument('--shard-count', '--shard_count',
      help='We are running this many shards.')
  result.add_argument('--shard-number', '--shard_number',
      help='We are running this shard.')
  result.add_argument('--generate-golden-files-to', '--generate_golden_files_to',
      help='Store dex files produced by tests in the specified directory.'
           ' It is aimed to be read on platforms with no host runtime available'
           ' for comparison.')
  result.add_argument('--use-golden-files-in', '--use_golden_files_in',
      help='Download golden files hierarchy for this commit in the specified'
           ' location and use them instead of executing on host runtime.')
  result.add_argument('--no-r8lib', '--no_r8lib',
      default=False, action='store_true',
      help='Run the tests on R8 full with relocated dependencies.')
  result.add_argument('--no-arttests', '--no_arttests',
      default=False, action='store_true',
      help='Do not run the art tests.')
  result.add_argument('--r8lib-no-deps', '--r8lib_no_deps',
      default=False, action='store_true',
      help='Run the tests on r8lib without relocated dependencies.')
  result.add_argument('--failed',
      default=False, action='store_true',
      help='Run the tests that failed last execution.')
  result.add_argument('--fail-fast', '--fail_fast',
      default=False, action='store_true',
      help='Stop on first failure. Passes --fail-fast to gradle test runner.')
  result.add_argument('--worktree',
      default=False, action='store_true',
      help='Tests are run in worktree and should not use gradle user home.')
  result.add_argument('--runtimes',
      default=None,
      help='Test parameter runtimes to use, separated by : (eg, none:jdk9).'
          ' Special values include: all (for all runtimes)'
          ' and empty (for no runtimes).')
  result.add_argument('--print-hanging-stacks', '--print_hanging_stacks',
      default=-1, type=int, help='Print hanging stacks after timeout in seconds')
  result.add_argument('--print-full-stacktraces', '--print_full_stacktraces',
      default=False, action='store_true',
      help='Print the full stacktraces without any filtering applied')
  result.add_argument(
      '--print-obfuscated-stacktraces', '--print_obfuscated_stacktraces',
      default=False, action='store_true',
      help='Print the obfuscated stacktraces')
  result.add_argument(
      '--debug-agent', '--debug_agent',
      help='Enable Java debug agent and suspend compilation (default disabled)',
      default=False,
      action='store_true')
  result.add_argument('--desugared-library-configuration',
      '--desugared_library-configuration',
      help='Use alternative desugared library configuration.')
  result.add_argument('--desugared-library', '--desugared_library',
      help='Build and use desugared library from GitHub.')
  result.add_argument('--print-times', '--print_times',
      help='Print the execution time of the slowest tests..',
      default=False, action='store_true')
  result.add_argument(
      '--testing-state-dir',
      help='Explicitly set the testing state directory '
           '(defaults to build/test-state/<git-branch>).')
  result.add_argument(
      '--rerun',
      help='Rerun tests (implicitly enables testing state).',
      choices=testing_state.CHOICES)
  result.add_argument(
      '--stacktrace',
      help='Pass --stacktrace to the gradle run',
      default=False, action='store_true')
  result.add_argument('--kotlin-compiler-dev',
                    help='Specify to download a kotlin dev compiler and run '
                         'tests with that',
                    default=False, action='store_true')
  result.add_argument('--kotlin-compiler-old',
                    help='Specify to run tests on older kotlin compilers',
                    default=False, action='store_true')
  result.add_argument('--new-gradle',
                    help='Specify to run in the new gradle setup',
                    default=True, action='store_true')
  return result.parse_known_args()

def has_failures(classes_file):
  with open(classes_file) as f:
    contents = f.read()
    # The report has a div tag with the percentage of tests that succeeded.
    assert '<div class="percent">' in contents
    return '<div class="percent">100%</div>' not in contents

def should_upload(filename, absolute_filename):
  # filename is relative to REPO_ROOT/build/reports/tests
  if filename.startswith('test/packages'):
    # We don't upload the package overview
    return False
  if filename.startswith('test/classes'):
    return has_failures(absolute_filename)
  # Always upload index, css and js
  return True

def archive_failures(options):
  upload_dir = os.path.join(utils.REPO_ROOT, 'build', 'reports', 'tests')
  file_name = options.archive_failures_file_name
  destination_dir = 'gs://%s/%s/' % (BUCKET, file_name)
  for (dir_path, dir_names, file_names) in os.walk(upload_dir):
    for f in file_names:
      absolute_file = os.path.join(dir_path, f)
      relative_file = absolute_file[len(upload_dir)+1:]
      if (should_upload(relative_file, absolute_file)):
        utils.upload_file_to_cloud_storage(absolute_file,
                                           destination_dir + relative_file)
  url = 'https://storage.googleapis.com/%s/%s/test/index.html' % (BUCKET, file_name)
  print('Test results available at: %s' % url)

def Main():
  (options, args) = ParseOptions()
  if utils.is_bot():
    gradle.RunGradle(['--no-daemon', 'clean'], new_gradle=options.new_gradle)
    print('Running with python ' + str(sys.version_info))
    # Always print stats on bots if command cache is enabled
    options.command_cache_stats = options.command_cache_dir is not None

  desugar_jdk_json_dir = None
  if options.desugared_library_configuration:
    if options.desugared_library_configuration != 'jdk11':
      print("Only value supported for --desugared-library is 'jdk11'")
      exit(1)
    desugar_jdk_json_dir = 'src/library_desugar/jdk11'

  desugar_jdk_libs = None
  if options.desugared_library:
    if options.desugared_library != 'HEAD':
      print("Only value supported for --desugared-library is 'HEAD'")
      exit(1)
    desugar_jdk_libs_dir = 'build/desugar_jdk_libs'
    shutil.rmtree(desugar_jdk_libs_dir, ignore_errors=True)
    os.makedirs(desugar_jdk_libs_dir)
    print('Building desugared library.')
    with utils.TempDir() as checkout_dir:
      archive_desugar_jdk_libs.CloneDesugaredLibrary('google', checkout_dir, 'HEAD')
      # Make sure bazel is extracted in third_party.
      utils.DownloadFromGoogleCloudStorage(utils.BAZEL_SHA_FILE)
      utils.DownloadFromGoogleCloudStorage(utils.JAVA8_SHA_FILE)
      utils.DownloadFromGoogleCloudStorage(utils.JAVA11_SHA_FILE)
      (library_jar, maven_zip) = archive_desugar_jdk_libs.BuildDesugaredLibrary(checkout_dir, 'jdk11_legacy' if options.desugared_library_configuration == 'jdk11' else 'jdk8')
      desugar_jdk_libs = os.path.join(desugar_jdk_libs_dir, os.path.basename(library_jar))
      shutil.copyfile(library_jar, desugar_jdk_libs)
      print('Desugared library for test in ' + desugar_jdk_libs)

  gradle_args = []

  if options.stacktrace or utils.is_bot():
    gradle_args.append('--stacktrace')

  if utils.is_bot():
    # Bots don't like dangling processes.
    gradle_args.append('--no-daemon')

  # Set all necessary Gradle properties and options first.
  if options.shard_count:
    assert options.shard_number
    gradle_args.append('-Pshard_count=%s' % options.shard_count)
    gradle_args.append('-Pshard_number=%s' % options.shard_number)
  if options.verbose:
    gradle_args.append('-Pprint_test_stdout')
  if options.no_internal:
    gradle_args.append('-Pno_internal')
  if options.only_internal:
    gradle_args.append('-Ponly_internal')
  if options.all_tests:
    gradle_args.append('-Pall_tests')
  if options.slow_tests:
    gradle_args.append('-Pslow_tests=1')
  if options.tool:
    gradle_args.append('-Ptool=%s' % options.tool)
  if options.one_line_per_test and not options.new_gradle:
    gradle_args.append('-Pone_line_per_test')
  if options.test_namespace:
    gradle_args.append('-Ptest_namespace=%s' % options.test_namespace)
  if options.disable_assertions:
    gradle_args.append('-Pdisable_assertions')
  if options.with_code_coverage:
    gradle_args.append('-Pwith_code_coverage')
  if options.print_full_stacktraces:
    gradle_args.append('-Pprint_full_stacktraces')
  if options.print_obfuscated_stacktraces:
    gradle_args.append('-Pprint_obfuscated_stacktraces')
  if options.kotlin_compiler_old:
    gradle_args.append('-Pkotlin_compiler_old')
  if options.kotlin_compiler_dev:
    gradle_args.append('-Pkotlin_compiler_dev')
    download_kotlin_dev.download_newest()
  if os.name == 'nt':
    gradle_args.append('-Pno_internal')
  if options.test_dir:
    gradle_args.append('-Ptest_dir=' + options.test_dir)
    if not os.path.exists(options.test_dir):
      os.makedirs(options.test_dir)
  if options.command_cache_dir:
    gradle_args.append('-Pcommand_cache_dir=' + options.command_cache_dir)
    if not os.path.exists(options.command_cache_dir):
      os.makedirs(options.command_cache_dir)
    if options.command_cache_stats:
      stats_dir = os.path.join(options.command_cache_dir, 'stats')
      gradle_args.append('-Pcommand_cache_stats_dir=' + stats_dir)
      if not os.path.exists(stats_dir):
        os.makedirs(stats_dir)
      # Clean out old stats files
      for (_, _, file_names) in os.walk(stats_dir):
        for f in file_names:
          os.remove(os.path.join(stats_dir, f))
  if options.java_home:
    gradle_args.append('-Dorg.gradle.java.home=' + options.java_home)
  if options.java_max_memory_size:
    gradle_args.append('-Ptest_xmx=' + options.java_max_memory_size)
  if options.generate_golden_files_to:
    gradle_args.append('-Pgenerate_golden_files_to=' + options.generate_golden_files_to)
    if not os.path.exists(options.generate_golden_files_to):
      os.makedirs(options.generate_golden_files_to)
    gradle_args.append('-PHEAD_sha1=' + utils.get_HEAD_sha1())
  if options.use_golden_files_in:
    gradle_args.append('-Puse_golden_files_in=' + options.use_golden_files_in)
    if not os.path.exists(options.use_golden_files_in):
      os.makedirs(options.use_golden_files_in)
    gradle_args.append('-PHEAD_sha1=' + utils.get_HEAD_sha1())
  if (not options.no_r8lib) and options.r8lib_no_deps:
    print('Cannot run tests on r8lib with and without deps. R8lib is now default target.')
    exit(1)
  if not options.no_r8lib:
    gradle_args.append('-Pr8lib')
    if options.new_gradle:
      gradle_args.append(':test:r8LibNoDeps')
      gradle_args.append(':test:retraceWithRelocatedDeps')
    else:
      # Force gradle to build a version of r8lib without dependencies for
      # BootstrapCurrentEqualityTest.
      gradle_args.append('R8LibNoDeps')
      gradle_args.append('R8Retrace')
  if options.r8lib_no_deps:
    gradle_args.append('-Pr8lib_no_deps')
  if options.worktree:
    gradle_args.append('-g=' + os.path.join(utils.REPO_ROOT, ".gradle_user_home"))
    gradle_args.append('--no-daemon')
  if options.debug_agent:
    gradle_args.append('--no-daemon')
  if desugar_jdk_json_dir:
    gradle_args.append('-Pdesugar_jdk_json_dir=' + desugar_jdk_json_dir)
  if desugar_jdk_libs:
    gradle_args.append('-Pdesugar_jdk_libs=' + desugar_jdk_libs)
  if options.no_arttests:
    gradle_args.append('-Pno_arttests=true')

  # Testing state is only supported in new-gradle going forward
  if options.new_gradle and options.rerun:
    testing_state.set_up_test_state(gradle_args, options.rerun, options.testing_state_dir)

  # Enable completeness testing of ART profile rewriting.
  gradle_args.append('-Part_profile_rewriting_completeness_check=true')

  # Build an R8 with dependencies for bootstrapping tests before adding test sources.
  if options.new_gradle:
    gradle_args.append(':main:r8WithRelocatedDeps')
    gradle_args.append(':test:cleanTest')
    gradle_args.append('test:test')
    gradle_args.append('--stacktrace')
    gradle_args.append('-Pprint_full_stacktraces')
  else:
    gradle_args.append('r8WithRelocatedDeps')
    gradle_args.append('r8WithRelocatedDeps17')
    # Add Gradle tasks
    gradle_args.append('cleanTest')
    gradle_args.append('test')

  if options.debug_agent:
    gradle_args.append('--debug-jvm')
  if options.fail_fast:
    gradle_args.append('--fail-fast')
  if options.failed:
    args = compute_failed_tests(args)
    if args is None:
      return 1
    if len(args) == 0:
      print("No failing tests")
      return 0
  # Test filtering. Must always follow the 'test' task.
  testFilterProperty = []
  for testFilter in args:
    gradle_args.append('--tests')
    gradle_args.append(testFilter)
    testFilterProperty.append(testFilter)
    assert not ("|" in testFilter), "| is used as separating character"
  if len(testFilterProperty) > 0:
    gradle_args.append("-Ptestfilter=" + "|".join(testFilterProperty))
  if options.with_code_coverage:
    # Create Jacoco report after tests.
    gradle_args.append('jacocoTestReport')

  if options.use_golden_files_in:
    sha1 = '%s' % utils.get_HEAD_sha1()
    with utils.ChangedWorkingDirectory(options.use_golden_files_in):
      utils.download_file_from_cloud_storage(
                                    'gs://r8-test-results/golden-files/%s.tar.gz' % sha1,
                                    '%s.tar.gz' % sha1)
      utils.unpack_archive('%s.tar.gz' % sha1)

  print_stacks_timeout = options.print_hanging_stacks
  if (utils.is_bot() and not utils.IsWindows()) or print_stacks_timeout > -1:
    timestamp_file = os.path.join(utils.BUILD, 'last_test_time')
    if os.path.exists(timestamp_file):
      os.remove(timestamp_file)
    gradle_args.append('-Pupdate_test_timestamp=' + timestamp_file)
    print_stacks_timeout = (print_stacks_timeout
                            if print_stacks_timeout != -1
                            else TIMEOUT_HANDLER_PERIOD)
    if utils.is_python3():
      threading.Thread(
          target=timeout_handler,
          args=(timestamp_file, print_stacks_timeout),
          daemon=True).start()
    else:
      thread.start_new_thread(
          timeout_handler, (timestamp_file, print_stacks_timeout,))
  rotate_test_reports()

  # Now run tests on selected runtime(s).
  if options.runtimes:
    if options.dex_vm != 'default':
      print('Unexpected runtimes and dex_vm argument: ' + options.dex_vm)
      sys.exit(1)
    if options.runtimes == 'empty':
      # Set runtimes with no content will configure no runtimes.
      gradle_args.append('-Pruntimes=')
    elif options.runtimes == 'all':
      # An unset runtimes will configure all runtimes
      pass
    else:
      prefixes = [prefix.strip() for prefix in options.runtimes.split(':')]
      runtimes = []
      for prefix in prefixes:
        matches = [ rt for rt in VALID_RUNTIMES if rt.startswith(prefix) ]
        if len(matches) == 0:
          print("Invalid runtime prefix '%s'." % prefix)
          print("Must be just 'all', 'empty'," \
                " or a prefix of %s" % ', '.join(VALID_RUNTIMES))
          sys.exit(1)
        runtimes.extend(matches)
      gradle_args.append('-Pruntimes=%s' % ':'.join(runtimes))

    return_code = gradle.RunGradle(
        gradle_args, throw_on_failure=False, new_gradle=options.new_gradle)
    return archive_and_return(return_code, options)

  # Legacy testing populates the runtimes based on dex_vm.
  vms_to_test = [options.dex_vm] if options.dex_vm != "all" else ALL_ART_VMS

  if options.print_times:
    gradle_args.append('-Pprint_times=true')
  for art_vm in vms_to_test:
    vm_suffix = "_" + options.dex_vm_kind if art_vm != "default" else ""
    runtimes = ['dex-' + art_vm]
    # Append the "none" runtime and default JVM if running the "default" DEX VM.
    if art_vm == "default":
        runtimes.extend(['jdk11', 'none'])
    return_code = gradle.RunGradle(
        gradle_args + [
          '-Pdex_vm=%s' % art_vm + vm_suffix,
          '-Pruntimes=%s' % ':'.join(runtimes),
        ],
        throw_on_failure=False,
        new_gradle=options.new_gradle)
    if options.generate_golden_files_to:
      sha1 = '%s' % utils.get_HEAD_sha1()
      with utils.ChangedWorkingDirectory(options.generate_golden_files_to):
        archive = utils.create_archive(sha1)
        utils.upload_file_to_cloud_storage(archive,
                                           'gs://r8-test-results/golden-files/' + archive)

    return archive_and_return(return_code, options)

  return 0

def archive_and_return(return_code, options):
  if return_code != 0:
    if options.archive_failures:
      archive_failures(options)
  if options.command_cache_stats:
    stats_dir = os.path.join(options.command_cache_dir, 'stats')
    cache_hit = 0
    cache_miss = 0
    cache_put = 0
    for (_, _, file_names) in os.walk(stats_dir):
      for f in file_names:
        if f.endswith('CACHEHIT'):
          cache_hit += os.stat(os.path.join(stats_dir, f)).st_size
        if f.endswith('CACHEMISS'):
          cache_miss += os.stat(os.path.join(stats_dir, f)).st_size
        if f.endswith('CACHEPUT'):
          cache_put += os.stat(os.path.join(stats_dir, f)).st_size
    print('Command cache stats')
    print('  Cache hits: ' + str(cache_hit))
    print('  Cache miss: ' + str(cache_miss))
    print('  Cache puts: ' + str(cache_put))
  return return_code

def print_jstacks():
  processes = subprocess.check_output(['ps', 'aux']).decode('utf-8')
  for l in processes.splitlines():
    if 'art' in l or 'dalvik' in l:
      print('Running art of dalvik process: \n%s' % l)
    if 'java' in l and 'openjdk' in l:
      print('Running jstack on process: \n%s' % l)
      # Example line:
      # ricow    184313  2.6  0.0 36839068 31808 ?      Sl   09:53   0:00 /us..
      columns = l.split()
      pid = columns[1]
      return_value = subprocess.call(['jstack', pid])
      if return_value:
        print('Could not jstack %s' % l)

def get_time_from_file(timestamp_file):
  if os.path.exists(timestamp_file):
    timestamp = os.stat(timestamp_file).st_mtime
    print('TIMEOUT HANDLER timestamp: %s' % (timestamp))
    sys.stdout.flush()
    return timestamp
  else:
    print('TIMEOUT HANDLER no timestamp file yet')
    sys.stdout.flush()
    return None

def timeout_handler(timestamp_file, timeout_handler_period):
  last_timestamp = None
  while True:
    time.sleep(timeout_handler_period)
    new_timestamp = get_time_from_file(timestamp_file)
    if last_timestamp and new_timestamp == last_timestamp:
      print_jstacks()
    last_timestamp = new_timestamp

def report_dir_path(index):
  if index == 0:
    return REPORTS_PATH
  return '%s%d' % (REPORTS_PATH, index)

def report_index_path(index):
  return os.path.join(report_dir_path(index), *REPORT_INDEX)

# Rotate test results so previous results are still accessible.
def rotate_test_reports():
  if not os.path.exists(report_dir_path(0)):
    return
  i = 1
  while i < NUMBER_OF_TEST_REPORTS and os.path.exists(report_dir_path(i)):
    i += 1
  if i == NUMBER_OF_TEST_REPORTS and os.path.exists(report_dir_path(i)):
    shutil.rmtree(report_dir_path(i))
  while i > 0:
    shutil.move(report_dir_path(i - 1), report_dir_path(i))
    i -= 1

def compute_failed_tests(args):
  if len(args) > 1:
    print("Running with --failed can take an optional path to a report index (or report number).")
    return None
  report = report_index_path(0)
  # If the default report does not exist, fall back to the previous report as it may be a failed
  # gradle run which has already moved the report to report1, but did not produce a new report.
  if not os.path.exists(report):
    report1 = report_index_path(1)
    if os.path.exists(report1):
      report = report1
  if len(args) == 1:
    try:
      # try to parse the arg as a report index.
      index = int(args[0])
      report = report_index_path(index)
    except ValueError:
      # if integer parsing failed assume it is a report file path.
      report = args[0]
  if not os.path.exists(report):
    print("Can't re-run failing, no report at:", report)
    return None
  print("Reading failed tests in", report)
  failing = set()
  inFailedSection = False
  for line in open(report):
    l = line.strip()
    if l == "<h2>Failed tests</h2>":
      inFailedSection = True
    elif l.startswith("<h2>"):
      inFailedSection = False
    prefix = '<a href="classes/'
    if inFailedSection and l.startswith(prefix):
      href = l[len(prefix):l.index('">')]
      # Ignore enties ending with .html which are test classes, not test methods.
      if not href.endswith('.html'):
        # Remove the .html and anchor separateor, also, a classMethod test is the static
        # setup failing so rerun the full class of tests.
        test = href.replace('.html','').replace('#', '.').replace('.classMethod', '')
        failing.add(test)
  return list(failing)
if __name__ == '__main__':
  return_code = Main()
  if return_code != 0:
    notify.notify("Tests failed.")
  else:
    notify.notify("Tests passed.")
  sys.exit(return_code)
