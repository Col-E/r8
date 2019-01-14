#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Run all internal tests, archive result to cloud storage.
# In the continuous operation flow we have a tester continuously checking
# a specific cloud storage location for a file with a git hash.
# If the file is there, the tester will remove the file, and add another
# file stating that this is now being run. After successfully running,
# the tester will add yet another file, and remove the last one.
# Complete flow with states:
# 1:
#   BOT:
#     Add file READY_FOR_TESTING (contains git hash)
#     Wait until file TESTING_COMPLETE exists (contains git hash)
#     Timeout if no progress for RUN_TIMEOUT
#       Cleanup READY_FOR_TESTING and TESTING
# 2:
#   TESTER:
#     Replace file READY_FOR_TESTING by TESTING (contains git hash)
#     Run tests for git hash
#     Upload commit specific logs if failures
#     Upload git specific overall status file (failed or succeeded)
#     Replace file TESTING by TESTING_COMPLETE (contains git hash)
# 3:
#   BOT:
#     Read overall status
#     Delete TESTING_COMPLETE
#     Exit based on status

import optparse
import os
import subprocess
import sys
import time
import utils

# How often the bot/tester should check state
PULL_DELAY = 30
BUCKET = 'r8-test-results'
TEST_RESULT_DIR = 'internal'

# Magic files
READY_FOR_TESTING = 'READY_FOR_TESTING'
TESTING = 'TESTING'
TESTING_COMPLETE = 'TESTING_COMPLETE'

ALL_MAGIC = [READY_FOR_TESTING, TESTING, TESTING_COMPLETE]

# Log file names
STDERR = 'stderr'
STDOUT = 'stdout'
EXITCODE = 'exitcode'
TIMED_OUT = 'timed_out'

TEST_COMMANDS = [
    # Run test.py internal testing.
    ['tools/test.py', '--only_internal'],
    # Ensure that all internal apps compile.
    ['tools/run_on_app.py', '--ignore-java-version','--run-all', '--out=out']
]

# Command timeout, in seconds.
RUN_TIMEOUT = 3600 * 3
BOT_RUN_TIMEOUT = RUN_TIMEOUT * len(TEST_COMMANDS)

def log(str):
  print("%s: %s" % (time.strftime("%c"), str))

def ParseOptions():
  result = optparse.OptionParser()
  result.add_option('--continuous',
      help='Continuously run internal tests and post results to GCS.',
      default=False, action='store_true')
  result.add_option('--print_logs',
      help='Fetch logs from gcs and print them, takes the commit to print for.',
      default=None)
  result.add_option('--bot',
      help='Run in bot mode, i.e., scheduling runs.',
      default=False, action='store_true')
  result.add_option('--archive',
       help='Post result to GCS, implied by --continuous',
       default=False, action='store_true')
  return result.parse_args()

def get_own_file_content():
  with open(sys.argv[0], 'r') as us:
    return us.read()

def restart_if_new_version(original_content):
  new_content = get_own_file_content()
  if new_content != original_content:
    log('Restarting tools/internal_test.py, content changed')
    os.execv(sys.argv[0], sys.argv)

def ensure_git_clean():
  # Ensure clean git repo.
  diff = subprocess.check_output(['git', 'diff'])
  if len(diff) > 0:
    log('Local modifications to the git repo, exiting')
    sys.exit(1)

def git_pull():
  ensure_git_clean()
  subprocess.check_call(['git', 'checkout', 'master'])
  subprocess.check_call(['git', 'pull'])
  return utils.get_HEAD_sha1()

def git_checkout(git_hash):
  ensure_git_clean()
  # Ensure that we are up to date to get the commit.
  git_pull()
  subprocess.check_call(['git', 'checkout', git_hash])
  return utils.get_HEAD_sha1()

def get_test_result_dir():
  return os.path.join(BUCKET, TEST_RESULT_DIR)

def get_sha_destination(sha):
  return os.path.join(get_test_result_dir(), sha)

def archive_status(failed):
  gs_destination = 'gs://%s' % get_sha_destination(utils.get_HEAD_sha1())
  archive_value('status', gs_destination, failed)

def get_status(sha):
  gs_destination = 'gs://%s/status' % get_sha_destination(sha)
  return utils.cat_file_on_cloud_storage(gs_destination)

def archive_file(name, gs_dir, src_file):
  gs_file = '%s/%s' % (gs_dir, name)
  utils.upload_file_to_cloud_storage(src_file, gs_file, public_read=False)

def archive_value(name, gs_dir, value):
  with utils.TempDir() as temp:
    tempfile = os.path.join(temp, name);
    with open(tempfile, 'w') as f:
      f.write(str(value))
    archive_file(name, gs_dir, tempfile)

def archive_log(stdout, stderr, exitcode, timed_out, cmd):
  sha = utils.get_HEAD_sha1()
  cmd_dir = cmd.replace(' ', '_').replace('/', '_')
  destination = os.path.join(get_sha_destination(sha), cmd_dir)
  gs_destination = 'gs://%s' % destination
  url = 'https://storage.cloud.google.com/%s' % destination
  log('Archiving logs to: %s' % gs_destination)
  archive_value(EXITCODE, gs_destination, exitcode)
  archive_value(TIMED_OUT, gs_destination, timed_out)
  archive_file(STDOUT, gs_destination, stdout)
  archive_file(STDERR, gs_destination, stderr)
  log('Logs available at: %s' % url)

def get_magic_file_base_path():
  return 'gs://%s/magic' % get_test_result_dir()

def get_magic_file_gs_path(name):
  return '%s/%s' % (get_magic_file_base_path(), name)

def get_magic_file_exists(name):
  return utils.file_exists_on_cloud_storage(get_magic_file_gs_path(name))

def delete_magic_file(name):
  utils.delete_file_from_cloud_storage(get_magic_file_gs_path(name))

def put_magic_file(name, sha):
  archive_value(name, get_magic_file_base_path(), sha)

def get_magic_file_content(name, ignore_errors=False):
  return utils.cat_file_on_cloud_storage(get_magic_file_gs_path(name),
                                         ignore_errors=ignore_errors)

def print_magic_file_state():
  log('Magic file status:')
  for magic in ALL_MAGIC:
    if get_magic_file_exists(magic):
      content = get_magic_file_content(magic, ignore_errors=True)
      log('%s content: %s' % (magic, content))

def fetch_and_print_logs(hash):
  gs_base = 'gs://%s' % get_sha_destination(hash)
  listing = utils.ls_files_on_cloud_storage(gs_base).strip().split('\n')
  for entry in listing:
    if not entry.endswith('/status'): # Ignore the overall status file
      for to_print in [EXITCODE, TIMED_OUT, STDERR, STDOUT]:
        gs_location = '%s%s' % (entry, to_print)
        value = utils.cat_file_on_cloud_storage(gs_location)
        print('\n\n%s had value:\n%s' % (to_print, value))

def run_bot():
  print_magic_file_state()
  # Ensure that there is nothing currently scheduled (broken/stopped run)
  for magic in ALL_MAGIC:
    if get_magic_file_exists(magic):
      log('ERROR: Synchronizing file %s exists, cleaning up' % magic)
      delete_magic_file(magic)
  print_magic_file_state()
  assert not get_magic_file_exists(READY_FOR_TESTING)
  git_hash = utils.get_HEAD_sha1()
  put_magic_file(READY_FOR_TESTING, git_hash)
  begin = time.time()
  while True:
    if time.time() - begin > BOT_RUN_TIMEOUT:
      log('Timeout exceeded: http://go/internal-r8-doc')
      raise Exception('Bot timeout')
    if get_magic_file_exists(TESTING_COMPLETE):
      if get_magic_file_content(TESTING_COMPLETE) == git_hash:
        break
      else:
        raise Exception('Non matching git hashes %s and %s' % (
            get_magic_file_content(TESTING_COMPLETE), git_hash))
    log('Still waiting for test result')
    print_magic_file_state()
    time.sleep(PULL_DELAY)
  total_time = time.time()-begin
  log('Done running test for %s in %ss' % (git_hash, total_time))
  test_status = get_status(git_hash)
  delete_magic_file(TESTING_COMPLETE)
  log('Test status is: %s' % test_status)
  if test_status != '0':
    fetch_and_print_logs(git_hash)
    return 1

def run_continuously():
  # If this script changes, we will restart ourselves
  own_content = get_own_file_content()
  while True:
    restart_if_new_version(own_content)
    print_magic_file_state()
    if get_magic_file_exists(READY_FOR_TESTING):
      git_hash = get_magic_file_content(READY_FOR_TESTING)
      checked_out = git_checkout(git_hash)
      # Sanity check, if this does not succeed stop.
      if checked_out != git_hash:
        log('Inconsistent state: %s %s' % (git_hash, checked_out))
        sys.exit(1)
      put_magic_file(TESTING, git_hash)
      delete_magic_file(READY_FOR_TESTING)
      log('Running with hash: %s' % git_hash)
      exitcode = run_once(archive=True)
      log('Running finished with exit code %s' % exitcode)
      put_magic_file(TESTING_COMPLETE, git_hash)
      delete_magic_file(TESTING)
    time.sleep(PULL_DELAY)

def handle_output(archive, stderr, stdout, exitcode, timed_out, cmd):
  if archive:
    archive_log(stdout, stderr, exitcode, timed_out, cmd)
  else:
    print 'Execution of %s resulted in:' % cmd
    print 'exit code: %s ' % exitcode
    print 'timeout: %s ' % timed_out
    with open(stderr, 'r') as f:
      print 'stderr: %s' % f.read()
    with open(stdout, 'r') as f:
      print 'stdout: %s' % f.read()

def execute(cmd, archive, env=None):
  utils.PrintCmd(cmd)
  with utils.TempDir() as temp:
    try:
      stderr_fd = None
      stdout_fd = None
      exitcode = 0
      stderr = os.path.join(temp, 'stderr')
      stderr_fd = open(stderr, 'w')
      stdout = os.path.join(temp, 'stdout')
      stdout_fd = open(stdout, 'w')
      popen = subprocess.Popen(cmd,
                               bufsize=1024*1024*10,
                               stdout=stdout_fd,
                               stderr=stderr_fd,
                               env=env)
      begin = time.time()
      timed_out = False
      while popen.poll() == None:
        if time.time() - begin > RUN_TIMEOUT:
          popen.terminate()
          timed_out = True
        time.sleep(2)
      exitcode = popen.returncode
    finally:
      if stderr_fd:
        stderr_fd.close()
      if stdout_fd:
        stdout_fd.close()
      if exitcode != 0:
        handle_output(archive, stderr, stdout, popen.returncode,
                      timed_out, ' '.join(cmd))
    return exitcode

def run_once(archive):
  failed = False
  git_hash = utils.get_HEAD_sha1()
  log('Running once with hash %s' % git_hash)
  env = os.environ.copy()
  # Bot does not have a lot of memory.
  env['R8_GRADLE_CORES_PER_FORK'] = '8'
  failed = any([execute(cmd, archive, env) for cmd in TEST_COMMANDS])
  archive_status(1 if failed else 0)
  return failed

def Main():
  (options, args) = ParseOptions()
  if options.continuous:
    run_continuously()
  elif options.bot:
    return run_bot()
  elif options.print_logs:
    return fetch_and_print_logs(options.print_logs)
  else:
    return run_once(options.archive)

if __name__ == '__main__':
  sys.exit(Main())
