#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import run_on_as_app
import shutil
import sys
import upload_to_x20
import utils

def main():
  # We need prodaccess to upload to x20
  utils.check_prodacces()

  working_dir = run_on_as_app.WORKING_DIR

  print 'Removing directories that do not match checked out revision'
  with utils.ChangedWorkingDirectory(working_dir):
    for repo in run_on_as_app.APP_REPOSITORIES:
      repo_dir = os.path.join(working_dir, repo.name)
      if os.path.exists(repo_dir) \
          and utils.get_HEAD_sha1_for_checkout(repo_dir) != repo.revision:
        print 'Removing %s' % repo_dir
        shutil.rmtree(repo_dir)

  print 'Downloading all missing apps'
  run_on_as_app.clone_repositories(quiet=False)

  # Package all files as x20 dependency
  parent_dir = os.path.dirname(working_dir)
  with utils.ChangedWorkingDirectory(parent_dir):
    print 'Creating archive for opensource_apps (this may take some time)'
    working_dir_name = os.path.basename(working_dir)
    repo_dirs = [working_dir_name + '/' + repo.name
                 for repo in run_on_as_app.APP_REPOSITORIES]
    filename = utils.create_archive("opensource_apps", repo_dirs)
    sha1 = utils.get_sha1(filename)
    dest = os.path.join(upload_to_x20.GMSCORE_DEPS, sha1)
    upload_to_x20.uploadFile(filename, dest)
    sha1_file = '%s.sha1' % filename
    with open(sha1_file, 'w') as output:
      output.write(sha1)
    shutil.move(sha1_file,
                os.path.join(utils.THIRD_PARTY, 'opensource_apps.tar.gz.sha1'))

  print 'To have apps benchmarked on Golem, the updated apps have to be ' \
        'downloaded to the runners by ssh\'ing into each runner and do:\n' \
        'cd ../golem\n' \
        'update_dependencies.sh\n'

if __name__ == '__main__':
  sys.exit(main())
