#!/usr/bin/env python
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for running tests. If no argument is given run all tests,
# if an argument is given, run only tests with that pattern. This script will
# force the tests to run, even if no input changed.

import optparse
import os
import shutil
import subprocess
import sys
import thread
import time
import uuid

import gradle
import notify
import utils

ALL_ART_VMS = [
    "default",
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
# is not a problem, no harm done except some logging in the stdout.
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
] + [ 'dex-%s' % dexvm for dexvm in ALL_ART_VMS ]

def ParseOptions():
  result = optparse.OptionParser()
  result.add_option('--no-internal', '--no_internal',
      help='Do not run Google internal tests.',
      default=False, action='store_true')
  result.add_option('--archive-failures', '--archive_failures',
      help='Upload test results to cloud storage on failure.',
      default=False, action='store_true')
  result.add_option('--only-internal', '--only_internal',
      help='Only run Google internal tests.',
      default=False, action='store_true')
  result.add_option('--all-tests', '--all_tests',
      help='Run tests in all configurations.',
      default=False, action='store_true')
  result.add_option('--slow-tests', '--slow_tests',
      help='Also run slow tests.',
      default=False, action='store_true')
  result.add_option('-v', '--verbose',
      help='Print test stdout to, well, stdout.',
      default=False, action='store_true')
  result.add_option('--dex-vm', '--dex_vm',
      help='The android version of the vm to use. "all" will run the tests on '
           'all art vm versions (stopping after first failed execution)',
      default="default",
      choices=ALL_ART_VMS + ["all"])
  result.add_option('--dex-vm-kind', '--dex_vm_kind',
                    help='Whether to use host or target version of runtime',
                    default="host",
                    nargs=1,
                    choices=["host", "target"])
  result.add_option('--one-line-per-test', '--one_line_per_test',
      help='Print a line before a tests starts and after it ends to stdout.',
      default=False, action='store_true')
  result.add_option('--tool',
      help='Tool to run ART tests with: "r8" (default) or "d8" or "r8cf"'
          ' (r8 w/CF-backend). Ignored if "--all_tests" enabled.',
      default=None, choices=["r8", "d8", "r8cf"])
  result.add_option('--jctf',
      help='Run JCTF tests with: "r8" (default) or "d8" or "r8cf".',
      default=False, action='store_true')
  result.add_option('--only-jctf', '--only_jctf',
      help='Run only JCTF tests with: "r8" (default) or "d8" or "r8cf".',
      default=False, action='store_true')
  result.add_option('--jctf-compile-only', '--jctf_compile_only',
      help="Don't run, only compile JCTF tests.",
      default=False, action='store_true')
  result.add_option('--disable-assertions', '--disable_assertions',
      help='Disable assertions when running tests.',
      default=False, action='store_true')
  result.add_option('--with-code-coverage', '--with_code_coverage',
      help='Enable code coverage with Jacoco.',
      default=False, action='store_true')
  result.add_option('--test-dir', '--test_dir',
      help='Use a custom directory for the test artifacts instead of a'
          ' temporary (which is automatically removed after the test).'
          ' Note that the directory will not be cleared before the test.')
  result.add_option('--java-home', '--java_home',
      help='Use a custom java version to run tests.')
  result.add_option('--java-max-memory-size', '--java_max_memory_size',
      help='Set memory for running tests, default 4G',
      default='4G')
  result.add_option('--test-namespace', '--test_namespace',
      help='Only run tests in  this namespace. The namespace is relative to '
          'com/android/tools/r8, e.g., desugar/desugaredlibrary',
      default=None)
  result.add_option('--shard-count', '--shard_count',
      help='We are running this many shards.')
  result.add_option('--shard-number', '--shard_number',
      help='We are running this shard.')
  result.add_option('--generate-golden-files-to', '--generate_golden_files_to',
      help='Store dex files produced by tests in the specified directory.'
           ' It is aimed to be read on platforms with no host runtime available'
           ' for comparison.')
  result.add_option('--use-golden-files-in', '--use_golden_files_in',
      help='Download golden files hierarchy for this commit in the specified'
           ' location and use them instead of executing on host runtime.')
  result.add_option('--no-r8lib', '--no_r8lib',
      default=False, action='store_true',
      help='Run the tests on R8 full with relocated dependencies.')
  result.add_option('--r8lib-no-deps', '--r8lib_no_deps',
      default=False, action='store_true',
      help='Run the tests on r8lib without relocated dependencies.')
  result.add_option('--failed',
      default=False, action='store_true',
      help='Run the tests that failed last execution.')
  result.add_option('--fail-fast', '--fail_fast',
      default=False, action='store_true',
      help='Stop on first failure. Passes --fail-fast to gradle test runner.')
  result.add_option('--worktree',
      default=False, action='store_true',
      help='Tests are run in worktree and should not use gradle user home.')
  result.add_option('--runtimes',
      default=None,
      help='Test parameter runtimes to use, separated by : (eg, none:jdk9).'
          ' Special values include: all (for all runtimes)'
          ' and empty (for no runtimes).')
  result.add_option('--print-hanging-stacks', '--print_hanging_stacks',
      default=-1, type="int", help='Print hanging stacks after timeout in seconds')
  return result.parse_args()

def archive_failures():
  upload_dir = os.path.join(utils.REPO_ROOT, 'build', 'reports', 'tests')
  u_dir = uuid.uuid4()
  destination = 'gs://%s/%s' % (BUCKET, u_dir)
  utils.upload_dir_to_cloud_storage(upload_dir, destination, is_html=True)
  url = 'https://storage.googleapis.com/%s/%s/test/index.html' % (BUCKET, u_dir)
  print 'Test results available at: %s' % url
  print '@@@STEP_LINK@Test failures@%s@@@' % url

def Main():
  (options, args) = ParseOptions()

  if utils.is_bot():
    gradle.RunGradle(['--no-daemon', 'clean'])

  gradle_args = ['--stacktrace']
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
  if options.one_line_per_test:
    gradle_args.append('-Pone_line_per_test')
  if options.jctf:
    gradle_args.append('-Pjctf')
  if options.only_jctf:
    gradle_args.append('-Ponly_jctf')
  if options.test_namespace:
    gradle_args.append('-Ptest_namespace=%s' % options.test_namespace)
  if options.jctf_compile_only:
    gradle_args.append('-Pjctf_compile_only')
  if options.disable_assertions:
    gradle_args.append('-Pdisable_assertions')
  if options.with_code_coverage:
    gradle_args.append('-Pwith_code_coverage')
  if os.name == 'nt':
    # temporary hack
    gradle_args.append('-Pno_internal')
    gradle_args.append('-x')
    gradle_args.append('createJctfTests')
    gradle_args.append('-x')
    gradle_args.append('jctfCommonJar')
    gradle_args.append('-x')
    gradle_args.append('jctfTestsClasses')
  if options.test_dir:
    gradle_args.append('-Ptest_dir=' + options.test_dir)
    if not os.path.exists(options.test_dir):
      os.makedirs(options.test_dir)
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
    # Force gradle to build a version of r8lib without dependencies for
    # BootstrapCurrentEqualityTest.
    gradle_args.append('R8LibNoDeps')
  if options.r8lib_no_deps:
    gradle_args.append('-Pr8lib_no_deps')
  if options.worktree:
    gradle_args.append('-g=' + os.path.join(utils.REPO_ROOT, ".gradle_user_home"))
    gradle_args.append('--no-daemon')

  # Build an R8 with dependencies for bootstrapping tests before adding test sources.
  gradle_args.append('r8WithDeps')
  gradle_args.append('r8WithDeps11')
  gradle_args.append('r8WithRelocatedDeps')
  gradle_args.append('r8WithRelocatedDeps11')

  # Add Gradle tasks
  gradle_args.append('cleanTest')
  gradle_args.append('test')
  if options.fail_fast:
    gradle_args.append('--fail-fast')
  if options.failed:
    args = compute_failed_tests(args)
    if args is None:
      return 1
    if len(args) == 0:
      print "No failing tests"
      return 0
  # Test filtering. Must always follow the 'test' task.
  for testFilter in args:
    gradle_args.append('--tests')
    gradle_args.append(testFilter)
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
    thread.start_new_thread(timeout_handler, (timestamp_file, print_stacks_timeout,))
  rotate_test_reports()

  if options.only_jctf:
    # Note: not setting -Pruntimes will run with all available runtimes.
    return_code = gradle.RunGradle(gradle_args, throw_on_failure=False)
    return archive_and_return(return_code, options)

  # Now run tests on selected runtime(s).
  if options.runtimes:
    if options.dex_vm != 'default':
      print 'Unexpected runtimes and dex_vm argument: ' + options.dex_vm
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
          print "Invalid runtime prefix '%s'." % prefix
          print "Must be just 'all', 'empty'," \
                " or a prefix of %s" % ', '.join(VALID_RUNTIMES)
          sys.exit(1)
        runtimes.extend(matches)
      gradle_args.append('-Pruntimes=%s' % ':'.join(runtimes))

    return_code = gradle.RunGradle(gradle_args, throw_on_failure=False)
    return archive_and_return(return_code, options)

  # Legacy testing populates the runtimes based on dex_vm.
  vms_to_test = [options.dex_vm] if options.dex_vm != "all" else ALL_ART_VMS

  for art_vm in vms_to_test:
    vm_suffix = "_" + options.dex_vm_kind if art_vm != "default" else ""
    runtimes = ['dex-' + art_vm]
    # Only append the "none" runtime and JVMs if running on the "default" DEX VM.
    if art_vm == "default":
      # TODO(b/170454076): Remove special casing for bot when rex-script has
      #  been migrated to account for runtimes.
      if utils.is_bot():
        runtimes.extend(['jdk11', 'none'])
      else:
        runtimes.extend(['jdk8', 'jdk9', 'jdk11', 'none'])
    return_code = gradle.RunGradle(
        gradle_args + [
          '-Pdex_vm=%s' % art_vm + vm_suffix,
          '-Pruntimes=%s' % ':'.join(runtimes),
        ],
        throw_on_failure=False)
    if options.generate_golden_files_to:
      sha1 = '%s' % utils.get_HEAD_sha1()
      with utils.ChangedWorkingDirectory(options.generate_golden_files_to):
        archive = utils.create_archive(sha1)
        utils.upload_file_to_cloud_storage(archive,
                                           'gs://r8-test-results/golden-files/' + archive)

    if return_code != 0:
      return archive_and_return(return_code, options)

  return 0


def archive_and_return(return_code, options):
  if return_code != 0:
    if options.archive_failures and os.name != 'nt':
      archive_failures()
  return return_code

def print_jstacks():
  processes = subprocess.check_output(['ps', 'aux'])
  for l in processes.splitlines():
    if 'java' in l and 'openjdk' in l:
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
  if index is 0:
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
    print "Running with --failed can take an optional path to a report index (or report number)."
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
    print "Can't re-run failing, no report at:", report
    return None
  print "Reading failed tests in", report
  failing = set()
  inFailedSection = False
  for line in file(report):
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
