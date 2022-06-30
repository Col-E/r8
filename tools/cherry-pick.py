#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys
import utils

def parse_options():
  parser = argparse.ArgumentParser(description='Release r8')
  parser.add_argument('--branch',
                      metavar=('<branch>'),
                      help='Branch to cherry-pick to')
  parser.add_argument('--clean-checkout', '--clean_checkout',
                      default=False,
                      action='store_true',
                      help='Perform cherry picks in a clean checkout')
  parser.add_argument('--no-upload', '--no_upload',
                      default=False,
                      action='store_true',
                      help='Do not upload to Gerrit')
  parser.add_argument('hashes', metavar='<hash>', nargs='+',
                    help='Hashed to merge')

  return parser.parse_args()


def run(args):
  # Checkout the branch.
  subprocess.check_output(['git', 'checkout', args.branch])

  if (not args.clean_checkout):
    for i in range(len(args.hashes) + 1):
      branch = 'cherry-%d' % (i + 1)
      print('Deleting branch %s' % branch)
      subprocess.run(['git', 'branch', branch, '-D'])

  count = 1
  for hash in args.hashes:
    branch = 'cherry-%d' % count
    print('Cherry-picking %s in %s' % (hash, branch))
    if (count == 1):
      subprocess.run(['git', 'new-branch', branch, '--upstream',  'origin/%s' % args.branch])
    else:
      subprocess.run(['git', 'new-branch', branch, '--upstream-current'])

    subprocess.run(['git', 'cherry-pick', hash])

    question = ('Ready to continue (cwd %s, will not upload to Gerrit)' % os.getcwd()
      if args.no_upload else
      'Ready to upload %s (cwd %s)' % (branch, os.getcwd()))
    answer = input(question + ' [y/N]?')
    if answer != 'y':
      print('Aborting new branch for %s' % branch_version)
      sys.exit(1)

    if (not args.no_upload):
      subprocess.run(['git', 'cl', 'upload'])
    count = count + 1

  branch = 'cherry-%d' % count
  subprocess.run(['git', 'new-branch', branch, '--upstream-current'])
  editor = os.environ.get('VISUAL')
  if not editor:
    editor = os.environ.get('EDITOR')
  if not editor:
    editor = 'vi'
  else:
    print("Opening src/main/java/com/android/tools/r8/Version.java" +
      " for version update with %" % editor)
  subprocess.run([editor, 'src/main/java/com/android/tools/r8/Version.java'])
  subprocess.run(['git', 'commit', '-a'])
  if (not args.no_upload):
    subprocess.run(['git', 'cl', 'upload'])
  if (args.clean_checkout):
    answer = input('Done, press enter to delete checkout in %s' % os.getcwd())

def main():
  args = parse_options()

  if (args.clean_checkout):
    with utils.TempDir() as temp:
      print("Performing cherry-picking in %s" % temp)
      subprocess.check_call(['git', 'clone', utils.REPO_SOURCE, temp])
      with utils.ChangedWorkingDirectory(temp):
        run(args)
  else:
    # Run in current directory.
    print("Performing cherry-picking in %s" % os.getcwd())
    subprocess.check_output(['git', 'fetch', 'origin'])
    run(args)

if __name__ == '__main__':
  sys.exit(main())
