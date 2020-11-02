# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Different utility functions used accross scripts

import hashlib
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
import jdk

ANDROID_JAR_DIR = 'third_party/android_jar/lib-v{api}'
ANDROID_JAR = os.path.join(ANDROID_JAR_DIR, 'android.jar')
TOOLS_DIR = defines.TOOLS_DIR
REPO_ROOT = defines.REPO_ROOT
THIRD_PARTY = defines.THIRD_PARTY
ANDROID_SDK = os.path.join(THIRD_PARTY, 'android_sdk')
MEMORY_USE_TMP_FILE = 'memory_use.tmp'
DEX_SEGMENTS_RESULT_PATTERN = re.compile('- ([^:]+): ([0-9]+)')
BUILD = os.path.join(REPO_ROOT, 'build')
BUILD_DEPS_DIR = os.path.join(BUILD, 'deps')
BUILD_MAIN_DIR = os.path.join(BUILD, 'classes', 'main')
BUILD_TEST_DIR = os.path.join(BUILD, 'classes', 'test')
LIBS = os.path.join(BUILD, 'libs')
GENERATED_LICENSE_DIR = os.path.join(BUILD, 'generatedLicense')
SRC_ROOT = os.path.join(REPO_ROOT, 'src', 'main', 'java')
TEST_ROOT = os.path.join(REPO_ROOT, 'src', 'test', 'java')
REPO_SOURCE = 'https://r8.googlesource.com/r8'

D8 = 'd8'
R8 = 'r8'
R8LIB = 'r8lib'
R8LIB_NO_DEPS = 'r8LibNoDeps'
R8_SRC = 'sourceJar'
LIBRARY_DESUGAR_CONVERSIONS = 'buildLibraryDesugarConversions'

D8_JAR = os.path.join(LIBS, 'd8.jar')
R8_JAR = os.path.join(LIBS, 'r8.jar')
R8LIB_JAR = os.path.join(LIBS, 'r8lib.jar')
R8LIB_MAP = os.path.join(LIBS, 'r8lib.jar.map')
R8_SRC_JAR = os.path.join(LIBS, 'r8-src.jar')
R8LIB_EXCLUDE_DEPS_JAR = os.path.join(LIBS, 'r8lib-exclude-deps.jar')
R8_FULL_EXCLUDE_DEPS_JAR = os.path.join(LIBS, 'r8-full-exclude-deps.jar')
MAVEN_ZIP = os.path.join(LIBS, 'r8.zip')
MAVEN_ZIP_LIB = os.path.join(LIBS, 'r8lib.zip')
LIBRARY_DESUGAR_CONVERSIONS_ZIP = os.path.join(LIBS, 'library_desugar_conversions.zip')

DESUGAR_CONFIGURATION = os.path.join(
      'src', 'library_desugar', 'desugar_jdk_libs.json')
DESUGAR_IMPLEMENTATION = os.path.join(
      'third_party', 'openjdk', 'desugar_jdk_libs', 'libjava.jar')
DESUGAR_CONFIGURATION_MAVEN_ZIP = os.path.join(
  LIBS, 'desugar_jdk_libs_configuration.zip')
GENERATED_LICENSE = os.path.join(GENERATED_LICENSE_DIR, 'LICENSE')
RT_JAR = os.path.join(REPO_ROOT, 'third_party/openjdk/openjdk-rt-1.8/rt.jar')
R8LIB_KEEP_RULES = os.path.join(REPO_ROOT, 'src/main/keep.txt')
CF_SEGMENTS_TOOL = os.path.join(THIRD_PARTY, 'cf_segments')
PINNED_R8_JAR = os.path.join(REPO_ROOT, 'third_party/r8/r8.jar')
PINNED_PGR8_JAR = os.path.join(REPO_ROOT, 'third_party/r8/r8-pg6.0.1.jar')
SAMPLE_LIBRARIES_SHA_FILE = os.path.join(
    THIRD_PARTY, 'sample_libraries.tar.gz.sha1')
OPENSOURCE_APPS_SHA_FILE = os.path.join(
    THIRD_PARTY, 'opensource_apps.tar.gz.sha1')
# TODO(b/152155164): Remove this when all apps has been migrated.
OPENSOURCE_APPS_FOLDER = os.path.join(THIRD_PARTY, 'opensource_apps')
OPENSOURCE_DUMPS_DIR = os.path.join(THIRD_PARTY, 'opensource-apps')
BAZEL_SHA_FILE = os.path.join(THIRD_PARTY, 'bazel.tar.gz.sha1')
BAZEL_TOOL = os.path.join(THIRD_PARTY, 'bazel')
JAVA8_SHA_FILE = os.path.join(THIRD_PARTY, 'openjdk', 'jdk8', 'linux-x86.tar.gz.sha1')

ANDROID_HOME_ENVIROMENT_NAME = "ANDROID_HOME"
ANDROID_TOOLS_VERSION_ENVIRONMENT_NAME = "ANDROID_TOOLS_VERSION"
USER_HOME = os.path.expanduser('~')

R8_TEST_RESULTS_BUCKET = 'r8-test-results'

def archive_file(name, gs_dir, src_file):
  gs_file = '%s/%s' % (gs_dir, name)
  upload_file_to_cloud_storage(src_file, gs_file, public_read=False)

def archive_value(name, gs_dir, value):
  with TempDir() as temp:
    tempfile = os.path.join(temp, name);
    with open(tempfile, 'w') as f:
      f.write(str(value))
    archive_file(name, gs_dir, tempfile)

def getAndroidHome():
  return os.environ.get(
      ANDROID_HOME_ENVIROMENT_NAME, os.path.join(USER_HOME, 'Android', 'Sdk'))

def getAndroidBuildTools():
  version = os.environ.get(ANDROID_TOOLS_VERSION_ENVIRONMENT_NAME, '28.0.3')
  return os.path.join(getAndroidHome(), 'build-tools', version)

def Print(s, quiet=False):
  if quiet:
    return
  print(s)

def Warn(message):
  CRED = '\033[91m'
  CEND = '\033[0m'
  print(CRED + message + CEND)

def PrintCmd(cmd, env=None, quiet=False):
  if quiet:
    return
  if type(cmd) is list:
    cmd = ' '.join(cmd)
  if env:
    env = ' '.join(['{}=\"{}\"'.format(x, y) for x, y in env.iteritems()])
    print('Running: {} {}'.format(env, cmd))
  else:
    print('Running: {}'.format(cmd))
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
    line = process.stdout.readline()
    if line != b'':
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
  if not os.path.exists(dep) or os.path.getmtime(tgz) < os.path.getmtime(sha1):
    DownloadFromGoogleCloudStorage(sha1)
    # Update the mtime of the tar file to make sure we do not run again unless
    # there is an update.
    os.utime(tgz, None)
  else:
    print 'Ensure cloud dependency:', msg, 'present'

def DownloadFromX20(sha1_file):
  download_script = os.path.join(REPO_ROOT, 'tools', 'download_from_x20.py')
  cmd = [download_script, sha1_file]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def DownloadFromGoogleCloudStorage(sha1_file, bucket='r8-deps', auth=False):
  suffix = '.bat' if IsWindows() else ''
  download_script = 'download_from_google_storage%s' % suffix
  cmd = [download_script]
  if not auth:
    cmd.append('-n')
  cmd.extend(['-b', bucket, '-u', '-s',  sha1_file])
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def get_sha1(filename):
  sha1 = hashlib.sha1()
  with open(filename, 'rb') as f:
    while True:
      chunk = f.read(1024*1024)
      if not chunk:
        break
      sha1.update(chunk)
  return sha1.hexdigest()

def is_master():
  remotes = subprocess.check_output(['git', 'branch', '-r', '--contains',
                                     'HEAD'])
  return 'origin/master' in remotes

def get_HEAD_sha1():
  return get_HEAD_sha1_for_checkout(REPO_ROOT)

def get_HEAD_sha1_for_checkout(checkout):
  cmd = ['git', 'rev-parse', 'HEAD']
  PrintCmd(cmd)
  with ChangedWorkingDirectory(checkout):
    return subprocess.check_output(cmd).strip()

def makedirs_if_needed(path):
  try:
    os.makedirs(path)
  except OSError:
    if not os.path.isdir(path):
        raise

def upload_dir_to_cloud_storage(directory, destination, is_html=False, public_read=True):
  # Upload and make the content encoding right for viewing directly
  cmd = ['gsutil.py', '-m', 'cp']
  if is_html:
    cmd += ['-z', 'html']
  if public_read:
    cmd += ['-a', 'public-read']
  cmd += ['-R', directory, destination]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def upload_file_to_cloud_storage(source, destination, public_read=True):
  cmd = ['gsutil.py', 'cp']
  if public_read:
    cmd += ['-a', 'public-read']
  cmd += [source, destination]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def delete_file_from_cloud_storage(destination):
  cmd = ['gsutil.py', 'rm', destination]
  PrintCmd(cmd)
  subprocess.check_call(cmd)

def ls_files_on_cloud_storage(destination):
  cmd = ['gsutil.py', 'ls', destination]
  PrintCmd(cmd)
  return subprocess.check_output(cmd)

def cat_file_on_cloud_storage(destination, ignore_errors=False):
  cmd = ['gsutil.py', 'cat', destination]
  PrintCmd(cmd)
  try:
    return subprocess.check_output(cmd)
  except subprocess.CalledProcessError as e:
    if ignore_errors:
      return ''
    else:
      raise e

def file_exists_on_cloud_storage(destination):
  cmd = ['gsutil.py', 'ls', destination]
  PrintCmd(cmd)
  return subprocess.call(cmd) == 0

def download_file_from_cloud_storage(source, destination):
  cmd = ['gsutil.py', 'cp', source, destination]
  PrintCmd(cmd)
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
    print 'Deleting existing dir %s' % dest_dir
    shutil.rmtree(dest_dir)
  dirname = os.path.dirname(os.path.abspath(filename))
  with tarfile.open(filename, 'r:gz') as tar:
    tar.extractall(path=dirname)

def check_gcert():
  subprocess.check_call(['gcert'])

# Note that gcs is eventually consistent with regards to list operations.
# This is not a problem in our case, but don't ever use this method
# for synchronization.
def cloud_storage_exists(destination):
  cmd = ['gsutil.py', 'ls', destination]
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
     print 'Enter directory:', self._working_directory
   os.chdir(self._working_directory)

 def __exit__(self, *_):
   if not self._quiet:
     print 'Enter directory:', self._old_cwd
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
  output = subprocess.check_output(cmd)

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
  output = subprocess.check_output(cmd)

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

def print_dexsegments(prefix, dex_files):
  for segment_name, size in getDexSegmentSizes(dex_files).items():
    print('{}-{}(CodeSize): {}'
        .format(prefix, segment_name, size))

# Ensure that we are not benchmarking with a google jvm.
def check_java_version():
  cmd= [jdk.GetJavaExecutable(), '-version']
  output = subprocess.check_output(cmd, stderr = subprocess.STDOUT)
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
  output = subprocess.check_output(cmd, stderr = subprocess.STDOUT)
  # output is of the form 'R8 <version> (with additional info)'
  # so we split on '('; clean up tailing spaces; and strip off 'R8 '.
  return output.split('(')[0].strip()[3:]

def desugar_configuration_version():
  with open(DESUGAR_CONFIGURATION, 'r') as f:
    configuration_json = json.loads(f.read())
    configuration_format_version = \
        configuration_json.get('configuration_format_version')
    version = configuration_json.get('version')
    if not version:
      raise Exception(
          'No "version" found in ' + utils.DESUGAR_CONFIGURATION)
    check_basic_semver_version(version, 'in ' + DESUGAR_CONFIGURATION)
    return version

class SemanticVersion:
  def __init__(self, major, minor, patch):
    self.major = major
    self.minor = minor
    self.patch = patch
    # Build metadata currently not suppported

  def larger_than(self, other):
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


# Check that the passed string is formatted as a basic semver version (x.y.z)
# See https://semver.org/.
def check_basic_semver_version(version, error_context = '', components = 3):
    regexp = '^'
    for x in range(components):
      regexp += '([0-9]+)'
      if x < components - 1:
        regexp += '\\.'
    regexp += '$'
    reg = re.compile(regexp)
    match = reg.match(version)
    if not match:
      raise Exception("Invalid version '"
            + version
            + "'"
            + (' ' + error_context) if len(error_context) > 0 else '')
    if components == 2:
      return SemanticVersion(int(match.group(1)), int(match.group(2)), None)
    elif components == 3:
      return SemanticVersion(
        int(match.group(1)), int(match.group(2)), int(match.group(3)))
    else:
      raise Exception('Argument "components" must be 2 or 3')
