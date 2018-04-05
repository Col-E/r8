#!/usr/bin/env python
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Wrapper script for running gradle.
# Will make sure we pulled down gradle before running, and will use the pulled
# down version to have a consistent developer experience.

import os
import subprocess
import sys
import utils

GRADLE_DIR = os.path.join(utils.REPO_ROOT, 'third_party', 'gradle')
GRADLE_SHA1 = os.path.join(GRADLE_DIR, 'gradle.tar.gz.sha1')
GRADLE_TGZ = os.path.join(GRADLE_DIR, 'gradle.tar.gz')

SHADOW_DIR = os.path.join(utils.REPO_ROOT, 'third_party')
SHADOW_SHA1 = os.path.join(SHADOW_DIR, 'shadow.tar.gz.sha1')
SHADOW_TGZ = os.path.join(SHADOW_DIR, 'shadow.tar.gz')

if utils.IsWindows():
  GRADLE = os.path.join(GRADLE_DIR, 'gradle', 'bin', 'gradle.bat')
else:
  GRADLE = os.path.join(GRADLE_DIR, 'gradle', 'bin', 'gradle')

SHADOW = os.path.join(SHADOW_DIR, 'shadow', 'shadow-2.0.1.jar')

def PrintCmd(s):
  if type(s) is list:
    s = ' '.join(s)
  print 'Running: %s' % s
  # I know this will hit os on windows eventually if we don't do this.
  sys.stdout.flush()

def EnsureGradle():
  if not os.path.exists(GRADLE) or os.path.getmtime(GRADLE_TGZ) < os.path.getmtime(GRADLE_SHA1):
    # Bootstrap or update gradle, everything else is controlled using gradle.
    utils.DownloadFromGoogleCloudStorage(GRADLE_SHA1)
    # Update the mtime of the tar file to make sure we do not run again unless
    # there is an update.
    os.utime(GRADLE_TGZ, None)
  else:
    print 'gradle.py: Gradle binary present'

def EnsureShadow():
  if not os.path.exists(SHADOW) or os.path.getmtime(SHADOW_TGZ) < os.path.getmtime(SHADOW_SHA1):
    # Bootstrap or update gradle, everything else is controlled using gradle.
    utils.DownloadFromGoogleCloudStorage(SHADOW_SHA1)
    # Update the mtime of the tar file to make sure we do not run again unless
    # there is an update.
    os.utime(SHADOW_TGZ, None)
  else:
    print 'gradle.py: Shadow library present'

def EnsureDeps():
  EnsureGradle()
  EnsureShadow()

def RunGradleIn(gradleCmd, args, cwd, throw_on_failure=True):
  EnsureDeps()
  cmd = [gradleCmd]
  cmd.extend(args)
  utils.PrintCmd(cmd)
  with utils.ChangedWorkingDirectory(cwd):
    return_value = subprocess.call(cmd)
    if throw_on_failure and return_value != 0:
      raise Exception('Failed to execute gradle')
    return return_value

def RunGradleWrapperIn(args, cwd, throw_on_failure=True):
  return RunGradleIn('./gradlew', args, cwd, throw_on_failure)

def RunGradle(args, throw_on_failure=True):
  return RunGradleIn(GRADLE, args, utils.REPO_ROOT, throw_on_failure)

def RunGradleExcludeDeps(args, throw_on_failure=True):
  EnsureDeps()
  args.append('-Pexclude_deps')
  return RunGradle(args, throw_on_failure)

def RunGradleInGetOutput(gradleCmd, args, cwd):
  EnsureDeps()
  cmd = [gradleCmd]
  cmd.extend(args)
  utils.PrintCmd(cmd)
  with utils.ChangedWorkingDirectory(cwd):
    return subprocess.check_output(cmd)

def RunGradleWrapperInGetOutput(args, cwd):
  return RunGradleInGetOutput('./gradlew', args, cwd)

def RunGradleGetOutput(args):
  return RunGradleInGetOutput(GRADLE, args, utils.REPO_ROOT)

def Main():
  RunGradle(sys.argv[1:])

if __name__ == '__main__':
  sys.exit(Main())
