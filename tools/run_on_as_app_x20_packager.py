#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import run_on_as_app
import shutil
import subprocess
import sys
import utils

def main():
  working_dir = run_on_as_app.WORKING_DIR

  print 'Removing directories that do not match checked out revision'
  if not os.path.exists(working_dir):
    os.makedirs(working_dir)
  else:
    for repo in run_on_as_app.APP_REPOSITORIES:
      repo_dir = os.path.join(working_dir, repo.name)
      if os.path.exists(repo_dir) \
          and utils.get_HEAD_sha1_for_checkout(repo_dir) != repo.revision:
        print 'Removing %s' % repo_dir
        shutil.rmtree(repo_dir)

  print 'Downloading all missing apps'
  run_on_as_app.clone_repositories(quiet=False)

  # Package all files as cloud dependency
  print 'Creating archive for opensource_apps (this may take some time)'
  if os.path.exists(utils.OPENSOURCE_APPS_FOLDER):
    shutil.rmtree(utils.OPENSOURCE_APPS_FOLDER)
  for repo in run_on_as_app.APP_REPOSITORIES:
    repo_dir = os.path.join(working_dir, repo.name)
    # Ensure there is a local gradle user home in the folder
    for app in repo.apps:
      app_checkout_dir = (os.path.join(repo_dir, app.dir)
                          if app.dir else repo_dir)
      gradle_user_home = os.path.join(
          app_checkout_dir, run_on_as_app.GRADLE_USER_HOME)
      if not os.path.exists(gradle_user_home):
        print 'Could not find the local gradle cache at %s. You should run ' \
              'run_on_as_app for app %s at least once.' \
              % (gradle_user_home, repo.name)
        sys.exit(1)
    dst = os.path.join(utils.OPENSOURCE_APPS_FOLDER, repo.name)
    shutil.copytree(repo_dir, dst)

  with utils.ChangedWorkingDirectory(utils.THIRD_PARTY):
    subprocess.check_call(['upload_to_google_storage.py', '-a', '--bucket',
                           'r8-deps', 'opensource_apps'])

  print 'To have apps benchmarked on Golem, the updated apps have to be ' \
        'downloaded to the runners by ssh\'ing into each runner and do:\n' \
        'cd ../golem\n' \
        'update_dependencies.sh\n'

if __name__ == '__main__':
  sys.exit(main())
