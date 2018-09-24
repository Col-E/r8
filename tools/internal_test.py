#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Run all internal tests, archive result to cloud storage.

import optparse
import os
import subprocess
import sys
import time
import utils

# How often to pull the git repo, in seconds.
PULL_DELAY = 25
# Command timeout, in seconds.
RUN_TIMEOUT = 3600
BUCKET = 'r8-test-results'
TEST_RESULT_DIR = 'internal'

def ParseOptions():
  result = optparse.OptionParser()
  result.add_option('--continuous',
      help='Continuously run internal tests and post results to GCS.',
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
    print('Restarting tools/internal_test.py, content changed')
    os.execv(sys.argv[0], sys.argv)

def git_pull():
  # Ensure clean git repo.
  diff = subprocess.check_output(['git', 'diff'])
  if len(diff) > 0:
    print('Local modifications to the git repo, exiting')
    sys.exit(1)
  subprocess.check_call(['git', 'pull'])
  return utils.get_HEAD_sha1()

def get_sha_destination(sha):
  return os.path.join(BUCKET, TEST_RESULT_DIR, sha)

def archive_status(failed):
  gs_destination = 'gs://%s' % get_sha_destination(utils.get_HEAD_sha1())
  archive_value('status', gs_destination, failed)

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
  cmd_dir = cmd.replace(' ', '_')
  destination = os.path.join(get_sha_destination(sha), cmd_dir)
  gs_destination = 'gs://%s' % destination
  url = 'https://storage.cloud.google.com/%s' % destination
  print('Archiving logs to: %s' % gs_destination)
  archive_value('exitcode', gs_destination, exitcode)
  archive_value('timed_out', gs_destination, timed_out)
  archive_file('stdout', gs_destination, stdout)
  archive_file('stderr', gs_destination, stderr)
  print('Logs available at: %s' % url)

def run_continuously():
  # If this script changes, we will restart ourselves
  own_content = get_own_file_content()
  git_hash = utils.get_HEAD_sha1()
  while True:
    restart_if_new_version(own_content)
    print('Running with hash: %s' % git_hash)
    exitcode = run_once(archive=True)
    git_pull()
    while git_pull() == git_hash:
      print('Still on same git hash: %s' % git_hash)
      time.sleep(PULL_DELAY)
    git_hash = utils.get_HEAD_sha1()

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

def execute(cmd, archive):
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
                               stderr=stderr_fd)
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
  print('Running once with hash %s' % git_hash)
  # Run test.py internal testing.
  cmd = ['tools/test.py', '--only_internal']
  if execute(cmd, archive):
    failed = True
  # Ensure that all internal apps compile.
  cmd = ['tools/run_on_app.py', '--run-all', '--out=out']
  if execute(cmd, archive):
    failed = True
  archive_status(1 if failed else 0)

def Main():
  (options, args) = ParseOptions()
  if options.continuous:
    run_continuously()
  else:
    run_once(options.archive)

if __name__ == '__main__':
  sys.exit(Main())
