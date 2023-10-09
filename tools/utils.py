# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Different utility functions used accross scripts

import hashlib
import jdk
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile

import defines
from thread_utils import print_thread

ANDROID_JAR_DIR = 'third_party/android_jar/lib-v{api}'
ANDROID_JAR = os.path.join(ANDROID_JAR_DIR, 'android.jar')
TOOLS_DIR = defines.TOOLS_DIR
REPO_ROOT = defines.REPO_ROOT
THIRD_PARTY = defines.THIRD_PARTY
BUNDLETOOL_JAR_DIR = os.path.join(THIRD_PARTY, 'bundletool/bundletool-1.11.0')
BUNDLETOOL_JAR = os.path.join(BUNDLETOOL_JAR_DIR, 'bundletool-all-1.11.0.jar')
ANDROID_SDK = os.path.join(THIRD_PARTY, 'android_sdk')
MEMORY_USE_TMP_FILE = 'memory_use.tmp'
DEX_SEGMENTS_RESULT_PATTERN = re.compile('- ([^:]+): ([0-9]+)')
BUILD = os.path.join(REPO_ROOT, 'build')
BUILD_DEPS_DIR = os.path.join(BUILD, 'deps')
BUILD_MAIN_DIR = os.path.join(BUILD, 'classes', 'main')
BUILD_JAVA_MAIN_DIR = os.path.join(BUILD, 'classes', 'java', 'main')
BUILD_TEST_DIR = os.path.join(BUILD, 'classes', 'test')
LIBS = os.path.join(BUILD, 'libs')
CUSTOM_CONVERSION_DIR = os.path.join(
    THIRD_PARTY, 'openjdk', 'custom_conversion')
GENERATED_LICENSE_DIR = os.path.join(BUILD, 'generatedLicense')
SRC_ROOT = os.path.join(REPO_ROOT, 'src', 'main', 'java')
TEST_ROOT = os.path.join(REPO_ROOT, 'src', 'test', 'java')
REPO_SOURCE = 'https://r8.googlesource.com/r8'

GRADLE_TASK_CLEAN_TEST = ':test:cleanTest'
GRADLE_TASK_CONSOLIDATED_LICENSE = ':main:consolidatedLicense'
GRADLE_TASK_KEEP_ANNO_JAR = ':keepanno:keepAnnoAnnotationsJar'
GRADLE_TASK_R8 = ':main:r8WithRelocatedDeps'
GRADLE_TASK_R8LIB = ':test:r8LibWithRelocatedDeps'
GRADLE_TASK_R8LIB_NO_DEPS = ':test:r8LibNoDeps'
GRADLE_TASK_RETRACE = ':test:retraceWithRelocatedDeps'
GRADLE_TASK_RETRACE_NO_DEPS = ':test:retraceNoDeps'
GRADLE_TASK_SOURCE_JAR = ':test:sourcesJar'
GRADLE_TASK_SWISS_ARMY_KNIFE = ':main:swissArmyKnife'
GRADLE_TASK_TEST = ':test:test'

D8 = 'd8'
R8 = 'r8'
R8LIB = 'r8lib'
R8LIB_NO_DEPS = 'r8LibNoDeps'
R8RETRACE = 'R8Retrace'
R8RETRACE_NO_DEPS = 'R8RetraceNoDeps'
R8_SRC = 'sourceJar'
LIBRARY_DESUGAR_CONVERSIONS =\
  'download_deps_third_party_openjdk_custom_conversion'
R8_TESTS_TARGET = 'TestJar'
R8_TESTS_DEPS_TARGET = 'RepackageTestDeps'
R8LIB_TESTS_TARGET = 'configureTestForR8Lib'
R8LIB_TESTS_DEPS_TARGET = R8_TESTS_DEPS_TARGET
KEEPANNO_ANNOTATIONS_TARGET = 'keepAnnoJar'

ALL_DEPS_JAR = os.path.join(LIBS, 'deps_all.jar')
R8_JAR = os.path.join(LIBS, 'r8.jar')
R8_WITH_RELOCATED_DEPS_JAR = os.path.join(LIBS, 'r8_with_relocated_deps.jar')
R8LIB_JAR = os.path.join(LIBS, 'r8lib.jar')
R8LIB_MAP = '%s.map' % R8LIB_JAR
R8_SRC_JAR = os.path.join(LIBS, 'r8-src.jar')
R8LIB_EXCLUDE_DEPS_JAR = os.path.join(LIBS, 'r8lib-exclude-deps.jar')
R8_FULL_EXCLUDE_DEPS_JAR = os.path.join(LIBS, 'r8-full-exclude-deps.jar')
R8RETRACE_JAR = os.path.join(LIBS, 'r8retrace.jar')
R8RETRACE_EXCLUDE_DEPS_JAR = os.path.join(LIBS, 'r8retrace-exclude-deps.jar')
R8_TESTS_JAR = os.path.join(LIBS, 'r8tests.jar')
R8LIB_TESTS_JAR = os.path.join(LIBS, 'r8libtestdeps-cf.jar')
R8_TESTS_DEPS_JAR = os.path.join(LIBS, 'test_deps_all.jar')
R8LIB_TESTS_DEPS_JAR = R8_TESTS_DEPS_JAR
MAVEN_ZIP_LIB = os.path.join(LIBS, 'r8lib.zip')
LIBRARY_DESUGAR_CONVERSIONS_LEGACY_ZIP = os.path.join(
    CUSTOM_CONVERSION_DIR, 'library_desugar_conversions_legacy.jar')
LIBRARY_DESUGAR_CONVERSIONS_ZIP = os.path.join(
    CUSTOM_CONVERSION_DIR, 'library_desugar_conversions.jar')
KEEPANNO_ANNOTATIONS_JAR = os.path.join(LIBS, 'keepanno-annotations.jar')

DESUGAR_CONFIGURATION = os.path.join(
      'src', 'library_desugar', 'desugar_jdk_libs.json')
DESUGAR_IMPLEMENTATION = os.path.join(
      'third_party', 'openjdk', 'desugar_jdk_libs', 'desugar_jdk_libs.jar')
DESUGAR_CONFIGURATION_JDK11_LEGACY = os.path.join(
      'src', 'library_desugar', 'jdk11', 'desugar_jdk_libs_legacy.json')
DESUGAR_CONFIGURATION_JDK11_MINIMAL = os.path.join(
      'src', 'library_desugar', 'jdk11', 'desugar_jdk_libs_minimal.json')
DESUGAR_CONFIGURATION_JDK11 = os.path.join(
      'src', 'library_desugar', 'jdk11', 'desugar_jdk_libs.json')
DESUGAR_CONFIGURATION_JDK11_NIO = os.path.join(
      'src', 'library_desugar', 'jdk11', 'desugar_jdk_libs_nio.json')
DESUGAR_IMPLEMENTATION_JDK11 = os.path.join(
      'third_party', 'openjdk', 'desugar_jdk_libs_11', 'desugar_jdk_libs.jar')
DESUGAR_CONFIGURATION_MAVEN_ZIP = os.path.join(
  LIBS, 'desugar_jdk_libs_configuration.zip')
DESUGAR_CONFIGURATION_JDK11_LEGACY_MAVEN_ZIP = os.path.join(
  LIBS, 'desugar_jdk_libs_configuration_jdk11_legacy.zip')
DESUGAR_CONFIGURATION_JDK11_MINIMAL_MAVEN_ZIP = os.path.join(
  LIBS, 'desugar_jdk_libs_configuration_jdk11_minimal.zip')
DESUGAR_CONFIGURATION_JDK11_MAVEN_ZIP = os.path.join(
  LIBS, 'desugar_jdk_libs_configuration_jdk11.zip')
DESUGAR_CONFIGURATION_JDK11_NIO_MAVEN_ZIP = os.path.join(
  LIBS, 'desugar_jdk_libs_configuration_jdk11_nio.zip')
GENERATED_LICENSE = os.path.join(GENERATED_LICENSE_DIR, 'LICENSE')
RT_JAR = os.path.join(REPO_ROOT, 'third_party/openjdk/openjdk-rt-1.8/rt.jar')
R8LIB_KEEP_RULES = os.path.join(REPO_ROOT, 'src/main/keep.txt')
CF_SEGMENTS_TOOL = os.path.join(THIRD_PARTY, 'cf_segments')
PINNED_R8_JAR = os.path.join(REPO_ROOT, 'third_party/r8/r8.jar')
PINNED_PGR8_JAR = os.path.join(REPO_ROOT, 'third_party/r8/r8-pg6.0.1.jar')
SAMPLE_LIBRARIES_SHA_FILE = os.path.join(
    THIRD_PARTY, 'sample_libraries.tar.gz.sha1')
OPENSOURCE_DUMPS_DIR = os.path.join(THIRD_PARTY, 'opensource-apps')
INTERNAL_DUMPS_DIR = os.path.join(THIRD_PARTY, 'internal-apps')
BAZEL_SHA_FILE = os.path.join(THIRD_PARTY, 'bazel.tar.gz.sha1')
BAZEL_TOOL = os.path.join(THIRD_PARTY, 'bazel')
JAVA8_SHA_FILE = os.path.join(THIRD_PARTY, 'openjdk', 'jdk8', 'linux-x86.tar.gz.sha1')
JAVA11_SHA_FILE = os.path.join(THIRD_PARTY, 'openjdk', 'jdk-11', 'linux.tar.gz.sha1')
DESUGAR_JDK_LIBS_11_SHA_FILE = os.path.join(THIRD_PARTY, 'openjdk', 'desugar_jdk_libs_11.tar.gz.sha1')
IGNORE_WARNINGS_RULES = os.path.join(REPO_ROOT, 'src', 'test', 'ignorewarnings.rules')

ANDROID_HOME_ENVIROMENT_NAME = "ANDROID_HOME"
ANDROID_TOOLS_VERSION_ENVIRONMENT_NAME = "ANDROID_TOOLS_VERSION"
USER_HOME = os.path.expanduser('~')

R8_TEST_RESULTS_BUCKET = 'r8-test-results'
R8_INTERNAL_TEST_RESULTS_BUCKET = 'r8-internal-test-results'

def archive_file(name, gs_dir, src_file):
  gs_file = '%s/%s' % (gs_dir, name)
  upload_file_to_cloud_storage(src_file, gs_file)

def archive_value(name, gs_dir, value):
  with TempDir() as temp:
    tempfile = os.path.join(temp, name);
    with open(tempfile, 'w') as f:
      f.write(str(value))
    archive_file(name, gs_dir, tempfile)

def find_cloud_storage_file_from_options(name, options, orElse=None):
  # Import archive on-demand since archive depends on utils.
  from archive import GetUploadDestination
  hash_or_version = find_hash_or_version_from_options(options)
  if not hash_or_version:
    return orElse
  is_hash = options.commit_hash is not None
  download_path = GetUploadDestination(hash_or_version, name, is_hash)
  if file_exists_on_cloud_storage(download_path):
    out = tempfile.NamedTemporaryFile().name
    download_file_from_cloud_storage(download_path, out)
    return out
  else:
    raise Exception('Could not find file {} from hash/version: {}.'
                  .format(name, hash_or_version))

def find_r8_jar_from_options(options):
  return find_cloud_storage_file_from_options('r8.jar', options)

def find_r8_lib_jar_from_options(options):
  return find_cloud_storage_file_from_options('r8lib.jar', options)

def find_hash_or_version_from_options(options):
  if options.tag:
    return find_hash_or_version_from_tag(options.tag)
  else:
    return options.commit_hash or options.version

def find_hash_or_version_from_tag(tag_or_hash):
  info = subprocess.check_output([
      'git',
      'show',
      tag_or_hash,
      '-s',
      '--format=oneline']).decode('utf-8').splitlines()[-1].split()
  # The info should be on the following form [hash,"Version",version]
  if len(info) == 3 and len(info[0]) == 40 and info[1] == "Version":
    return info[2]
  return None

def getAndroidHome():
  return os.environ.get(
      ANDROID_HOME_ENVIROMENT_NAME, os.path.join(USER_HOME, 'Android', 'Sdk'))

def getAndroidBuildTools():
  if ANDROID_TOOLS_VERSION_ENVIRONMENT_NAME in os.environ:
    version = os.environ.get(ANDROID_TOOLS_VERSION_ENVIRONMENT_NAME)
    build_tools_dir = os.path.join(getAndroidHome(), 'build-tools', version)
    assert os.path.exists(build_tools_dir)
    return build_tools_dir
  else:
    versions = ['33.0.1', '32.0.0']
    for version in versions:
      build_tools_dir = os.path.join(getAndroidHome(), 'build-tools', version)
      if os.path.exists(build_tools_dir):
        return build_tools_dir
  raise Exception('Unable to find Android build-tools')

def is_python3():
  return sys.version_info.major == 3

def Print(s, quiet=False):
  if quiet:
    return
  print(s)

def Warn(message):
  CRED = '\033[91m'
  CEND = '\033[0m'
  print(CRED + message + CEND)

def PrintCmd(cmd, env=None, quiet=False, worker_id=None):
  if quiet:
    return
  if type(cmd) is list:
    cmd = ' '.join(cmd)
  if env:
    env = ' '.join(['{}=\"{}\"'.format(x, y) for x, y in env.iteritems()])
    print_thread('Running: {} {}'.format(env, cmd), worker_id)
  else:
    print_thread('Running: {}'.format(cmd), worker_id)
  # I know this will hit os on windows eventually if we don't do this.
  sys.stdout.flush()

class ProgressLogger(object):
  CLEAR_LINE = '\033[K'
  UP = '\033[F'

  def __init__(self, quiet=False):
    self._count = 0
    self._has_printed = False
    self._quiet = quiet

  def log(self, text):
    if len(text.strip()) == 0:
      return
    if self._quiet:
      if self._has_printed:
        sys.stdout.write(ProgressLogger.UP + ProgressLogger.CLEAR_LINE)
      if len(text) > 140:
        text = text[0:140] + '...'
    print(text)
    self._has_printed = True

  def done(self):
    if self._quiet and self._has_printed:
      sys.stdout.write(ProgressLogger.UP + ProgressLogger.CLEAR_LINE)
      print('')
      sys.stdout.write(ProgressLogger.UP)

def RunCmd(cmd, env_vars=None, quiet=False, fail=True, logging=True):
  PrintCmd(cmd, env=env_vars, quiet=quiet)
  env = os.environ.copy()
  if env_vars:
    env.update(env_vars)
  process = subprocess.Popen(
      cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
  stdout = []
  logger = ProgressLogger(quiet=quiet) if logging else None
  failed = False
  while True:
    line = process.stdout.readline().decode('utf-8')
    if line != '':
      stripped = line.rstrip()
      stdout.append(stripped)
      if logger:
        logger.log(stripped)
      # TODO(christofferqa): r8 should fail with non-zero exit code.
      if ('AssertionError:' in stripped
          or 'CompilationError:' in stripped
          or 'CompilationFailedException:' in stripped
          or 'Compilation failed' in stripped
          or 'FAILURE:' in stripped
          or 'org.gradle.api.ProjectConfigurationException' in stripped
          or 'BUILD FAILED' in stripped):
        failed = True
    else:
      if logger:
        logger.done()
      exit_code = process.poll()
      if exit_code or failed:
        for line in stdout:
          Warn(line)
        if fail:
          raise subprocess.CalledProcessError(
              exit_code or -1, cmd, output='\n'.join(stdout))
      return stdout

def RunGradlew(
    args, clean=True, stacktrace=True, use_daemon=False, env_vars=None,
    quiet=False, fail=True, logging=True):
  cmd = ['./gradlew']
  if clean:
    assert 'clean' not in args
    cmd.append('clean')
  if stacktrace:
    assert '--stacktrace' not in args
    cmd.append('--stacktrace')
  if not use_daemon:
    assert '--no-daemon' not in args
    cmd.append('--no-daemon')
  cmd.extend(args)
  return RunCmd(cmd, env_vars=env_vars, quiet=quiet, fail=fail, logging=logging)

def IsWindows():
  return defines.IsWindows()

def IsLinux():
  return defines.IsLinux()

def IsOsX():
  return defines.IsOsX()

def EnsureDepFromGoogleCloudStorage(dep, tgz, sha1, msg):
  if (not os.path.exists(dep)
     or not os.path.exists(tgz)
     or os.path.getmtime(tgz) < os.path.getmtime(sha1)):
    DownloadFromGoogleCloudStorage(sha1)
    # Update the mtime of the tar file to make sure we do not run again unless
    # there is an update.
    os.utime(tgz, None)
  else:
    print('Ensure cloud dependency:', msg, 'present')

def DownloadFromX20(sha1_file):
  download_script = os.path.join(REPO_ROOT, 'tools', 'download_from_x20.py')
  cmd = [download_script, sha1_file]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def DownloadFromGoogleCloudStorage(sha1_file, bucket='r8-deps', auth=False,
                                   quiet=False):
  suffix = '.bat' if IsWindows() else ''
  download_script = 'download_from_google_storage%s' % suffix
  cmd = [download_script]
  if not auth:
    cmd.append('-n')
  cmd.extend(['-b', bucket, '-u', '-s',  sha1_file])
  if not quiet:
    PrintCmd(cmd)
    subprocess.check_call(cmd)
  else:
    subprocess.check_output(cmd)

def get_sha1(filename):
  sha1 = hashlib.sha1()
  with open(filename, 'rb') as f:
    while True:
      chunk = f.read(1024*1024)
      if not chunk:
        break
      sha1.update(chunk)
  return sha1.hexdigest()

def is_main():
  remotes = subprocess.check_output(['git', 'branch', '-r', '--contains',
                                     'HEAD']).decode('utf-8')
  return 'origin/main' in remotes

def get_HEAD_branch():
  result = subprocess.check_output(['git', 'rev-parse', '--abbrev-ref', 'HEAD']).decode('utf-8')
  return result.strip()

def get_HEAD_sha1():
  return get_HEAD_sha1_for_checkout(REPO_ROOT)

def get_HEAD_diff_stat():
  return subprocess.check_output(['git', 'diff', '--stat']).decode('utf-8')

def get_HEAD_sha1_for_checkout(checkout):
  cmd = ['git', 'rev-parse', 'HEAD']
  PrintCmd(cmd)
  with ChangedWorkingDirectory(checkout):
    return subprocess.check_output(cmd).decode('utf-8').strip()

def makedirs_if_needed(path):
  try:
    os.makedirs(path)
  except OSError:
    if not os.path.isdir(path):
        raise

def get_gsutil():
  return 'gsutil.py' if os.name != 'nt' else 'gsutil.py.bat'

def upload_dir_to_cloud_storage(directory, destination, is_html=False):
  # Upload and make the content encoding right for viewing directly
  cmd = [get_gsutil(), '-m', 'cp']
  if is_html:
    cmd += ['-z', 'html']
  cmd += ['-R', directory, destination]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def upload_file_to_cloud_storage(source, destination):
  cmd = [get_gsutil(), 'cp']
  cmd += [source, destination]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def delete_file_from_cloud_storage(destination):
  cmd = [get_gsutil(), 'rm', destination]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def ls_files_on_cloud_storage(destination):
  cmd = [get_gsutil(), 'ls', destination]
  PrintCmd(cmd)
  return subprocess.check_output(cmd).decode('utf-8')

def cat_file_on_cloud_storage(destination, ignore_errors=False):
  cmd = [get_gsutil(), 'cat', destination]
  PrintCmd(cmd)
  try:
    return subprocess.check_output(cmd).decode('utf-8').strip()
  except subprocess.CalledProcessError as e:
    if ignore_errors:
      return ''
    else:
      raise e

def file_exists_on_cloud_storage(destination):
  cmd = [get_gsutil(), 'ls', destination]
  PrintCmd(cmd)
  return subprocess.call(cmd) == 0

def download_file_from_cloud_storage(source, destination, quiet=False):
  cmd = [get_gsutil(), 'cp', source, destination]
  PrintCmd(cmd, quiet=quiet)
  subprocess.check_call(cmd)

def create_archive(name, sources=None):
  if not sources:
    sources = [name]
  tarname = '%s.tar.gz' % name
  with tarfile.open(tarname, 'w:gz') as tar:
    for source in sources:
      tar.add(source)
  return tarname

def extract_dir(filename):
  return filename[0:len(filename) - len('.tar.gz')]

def unpack_archive(filename):
  dest_dir = extract_dir(filename)
  if os.path.exists(dest_dir):
    print('Deleting existing dir %s' % dest_dir)
    shutil.rmtree(dest_dir)
  dirname = os.path.dirname(os.path.abspath(filename))
  with tarfile.open(filename, 'r:gz') as tar:
    tar.extractall(path=dirname)

def check_gcert():
  status = subprocess.call(['gcertstatus'])
  if status != 0:
    subprocess.check_call(['gcert'])

# Note that gcs is eventually consistent with regards to list operations.
# This is not a problem in our case, but don't ever use this method
# for synchronization.
def cloud_storage_exists(destination):
  cmd = [get_gsutil(), 'ls', destination]
  PrintCmd(cmd)
  exit_code = subprocess.call(cmd)
  return exit_code == 0

class TempDir(object):
 def __init__(self, prefix='', delete=True):
   self._temp_dir = None
   self._prefix = prefix
   self._delete = delete

 def __enter__(self):
   self._temp_dir = tempfile.mkdtemp(self._prefix)
   return self._temp_dir

 def __exit__(self, *_):
   if self._delete:
     shutil.rmtree(self._temp_dir, ignore_errors=True)

class ChangedWorkingDirectory(object):
 def __init__(self, working_directory, quiet=False):
   self._quiet = quiet
   self._working_directory = working_directory

 def __enter__(self):
   self._old_cwd = os.getcwd()
   if not self._quiet:
     print('Enter directory:', self._working_directory)
   os.chdir(self._working_directory)

 def __exit__(self, *_):
   if not self._quiet:
     print('Enter directory:', self._old_cwd)
   os.chdir(self._old_cwd)

# Reading Android CTS test_result.xml

class CtsModule(object):
  def __init__(self, module_name):
    self.name = module_name

class CtsTestCase(object):
  def __init__(self, test_case_name):
    self.name = test_case_name

class CtsTest(object):
  def __init__(self, test_name, outcome):
    self.name = test_name
    self.outcome = outcome

# Generator yielding CtsModule, CtsTestCase or CtsTest from
# reading through a CTS test_result.xml file.
def read_cts_test_result(file_xml):
  re_module = re.compile('<Module name="([^"]*)"')
  re_test_case = re.compile('<TestCase name="([^"]*)"')
  re_test = re.compile('<Test result="(pass|fail)" name="([^"]*)"')
  with open(file_xml) as f:
    for line in f:
      m = re_module.search(line)
      if m:
        yield CtsModule(m.groups()[0])
        continue
      m = re_test_case.search(line)
      if m:
        yield CtsTestCase(m.groups()[0])
        continue
      m = re_test.search(line)
      if m:
        outcome = m.groups()[0]
        assert outcome in ['fail', 'pass']
        yield CtsTest(m.groups()[1], outcome == 'pass')

def grep_memoryuse(logfile):
  re_vmhwm = re.compile('^VmHWM:[ \t]*([0-9]+)[ \t]*([a-zA-Z]*)')
  result = None
  with open(logfile) as f:
    for line in f:
      m = re_vmhwm.search(line)
      if m:
        groups = m.groups()
        s = len(groups)
        if s >= 1:
          result = int(groups[0])
          if s >= 2:
            unit = groups[1]
            if unit == 'kB':
              result *= 1024
            elif unit != '':
              raise Exception('Unrecognized unit in memory usage log: {}'
                  .format(unit))
  if result is None:
    raise Exception('No memory usage found in log: {}'.format(logfile))
  return result

# Return a dictionary: {segment_name -> segments_size}
def getDexSegmentSizes(dex_files):
  assert len(dex_files) > 0
  cmd = [jdk.GetJavaExecutable(), '-jar', R8_JAR, 'dexsegments']
  cmd.extend(dex_files)
  PrintCmd(cmd)
  output = subprocess.check_output(cmd).decode('utf-8')

  matches = DEX_SEGMENTS_RESULT_PATTERN.findall(output)

  if matches is None or len(matches) == 0:
    raise Exception('DexSegments failed to return any output for' \
        ' these files: {}'.format(dex_files))

  result = {}

  for match in matches:
    result[match[0]] = int(match[1])

  return result

# Return a dictionary: {segment_name -> segments_size}
def getCfSegmentSizes(cfFile):
  cmd = [jdk.GetJavaExecutable(),
         '-cp',
         CF_SEGMENTS_TOOL,
         'com.android.tools.r8.cf_segments.MeasureLib',
         cfFile]
  PrintCmd(cmd)
  output = subprocess.check_output(cmd).decode('utf-8')

  matches = DEX_SEGMENTS_RESULT_PATTERN.findall(output)

  if matches is None or len(matches) == 0:
    raise Exception('CfSegments failed to return any output for' \
                    ' the file: ' + cfFile)

  result = {}

  for match in matches:
    result[match[0]] = int(match[1])

  return result

def get_maven_path(artifact, version):
  return os.path.join('com', 'android', 'tools', artifact, version)

def print_cfsegments(prefix, cf_files):
  for cf_file in cf_files:
    for segment_name, size in getCfSegmentSizes(cf_file).items():
      print('{}-{}(CodeSize): {}'
            .format(prefix, segment_name, size))

def print_dexsegments(prefix, dex_files, worker_id=None):
  for segment_name, size in getDexSegmentSizes(dex_files).items():
    print_thread(
      '{}-{}(CodeSize): {}'.format(prefix, segment_name, size),
      worker_id)

# Ensure that we are not benchmarking with a google jvm.
def check_java_version():
  cmd= [jdk.GetJavaExecutable(), '-version']
  output = subprocess.check_output(cmd, stderr = subprocess.STDOUT).decode('utf-8')
  m = re.search('openjdk version "([^"]*)"', output)
  if m is None:
    raise Exception("Can't check java version: no version string in output"
        " of 'java -version': '{}'".format(output))
  version = m.groups(0)[0]
  m = re.search('google', version)
  if m is not None:
    raise Exception("Do not use google JVM for benchmarking: " + version)

def get_android_jar_dir(api):
  return os.path.join(REPO_ROOT, ANDROID_JAR_DIR.format(api=api))

def get_android_jar(api):
  return os.path.join(REPO_ROOT, ANDROID_JAR.format(api=api))

def get_android_optional_jars(api):
  android_optional_jars_dir = os.path.join(get_android_jar_dir(api), 'optional')
  android_optional_jars = [
    os.path.join(android_optional_jars_dir, 'android.test.base.jar'),
    os.path.join(android_optional_jars_dir, 'android.test.mock.jar'),
    os.path.join(android_optional_jars_dir, 'android.test.runner.jar'),
    os.path.join(android_optional_jars_dir, 'org.apache.http.legacy.jar')
  ]
  return [
      android_optional_jar for android_optional_jar in android_optional_jars
      if os.path.isfile(android_optional_jar)]

def is_bot():
  return 'SWARMING_BOT_ID' in os.environ

def uncompressed_size(path):
  return sum(z.file_size for z in zipfile.ZipFile(path).infolist())

def getR8Version(path):
  cmd = [jdk.GetJavaExecutable(), '-cp', path, 'com.android.tools.r8.R8',
        '--version']
  output = subprocess.check_output(cmd, stderr = subprocess.STDOUT).decode('utf-8')
  # output is of the form 'R8 <version> (with additional info)'
  # so we split on '('; clean up tailing spaces; and strip off 'R8 '.
  return output.split('(')[0].strip()[3:]

def desugar_configuration_name_and_version(configuration, is_for_maven):
  name = 'desugar_jdk_libs_configuration'
  with open(configuration, 'r') as f:
    configuration_json = json.loads(f.read())
    configuration_format_version = \
        configuration_json.get('configuration_format_version')
    if (not configuration_format_version):
        raise Exception(
            'No "configuration_format_version" found in ' + configuration)
    if (configuration_format_version != 3
        and configuration_format_version != 5
        and configuration_format_version != (200 if is_for_maven else 100)):
          raise Exception(
              'Unsupported "configuration_format_version" "%s" found in %s'
              % (configuration_format_version, configuration))
    version = configuration_json.get('version')
    if not version:
      if configuration_format_version == (200 if is_for_maven else 100):
        identifier = configuration_json.get('identifier')
        if not identifier:
          raise Exception(
              'No "identifier" found in ' + configuration)
        identifier_split = identifier.split(':')
        if (len(identifier_split) != 3):
          raise Exception('Invalid "identifier" found in ' + configuration)
        if (identifier_split[0] != 'com.tools.android'):
          raise Exception('Invalid "identifier" found in ' + configuration)
        if not identifier_split[1].startswith('desugar_jdk_libs_configuration'):
          raise Exception('Invalid "identifier" found in ' + configuration)
        name = identifier_split[1]
        version = identifier_split[2]
      else:
        raise Exception(
            'No "version" found in ' + configuration)
    else:
      if configuration_format_version == (200 if is_for_maven else 100):
        raise Exception(
            'No "version" expected in ' + configuration)
    # Disallow prerelease, as older R8 versions cannot parse it causing hard to
    # understand errors.
    check_basic_semver_version(version, 'in ' + configuration, allowPrerelease = False)
    return (name, version)

class SemanticVersion:
  def __init__(self, major, minor, patch, prerelease):
    self.major = major
    self.minor = minor
    self.patch = patch
    self.prerelease = prerelease
    # Build metadata currently not suppported

  def larger_than(self, other):
    if self.prerelease or other.prerelease:
      raise Exception("Comparison with prerelease not implemented")
    if self.major > other.major:
      return True
    if self.major == other.major and self.minor > other.minor:
      return True
    if self.patch:
      return (self.major == other.major
        and self.minor == other.minor
        and self.patch > other.patch)
    else:
      return False


# Check that the passed string is formatted as a basic semver version (x.y.z or x.y.z-prerelease
# depending on the value of allowPrerelease).
# See https://semver.org/. The regexp parts used are not all complient with what is suggested
# on https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string.
def check_basic_semver_version(version, error_context = '', components = 3, allowPrerelease = False):
    regexp = '^'
    for x in range(components):
      regexp += '([0-9]+)'
      if x < components - 1:
        regexp += '\\.'
    if allowPrerelease:
      # This part is from
      # https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
      regexp += r'(?:-(?P<prerelease>(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?'
    regexp += '$'
    reg = re.compile(regexp)
    match = reg.match(version)
    if not match:
      raise Exception("Invalid version '"
            + version
            + "'"
            + (' ' + error_context) if len(error_context) > 0 else '')
    if components == 2:
      return SemanticVersion(int(match.group(1)), int(match.group(2)), None, None)
    elif components == 3 and not allowPrerelease:
      return SemanticVersion(
        int(match.group(1)), int(match.group(2)), int(match.group(3)), None)
    elif components == 3 and allowPrerelease:
      return SemanticVersion(
        int(match.group(1)), int(match.group(2)), int(match.group(3)), match.group('prerelease'))
    else:
      raise Exception('Argument "components" must be 2 or 3')
