#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script that automatically pulls and uploads all upstream direct and indirect
# branches into the current branch.
#
# Example:
#
#   $ git branch -vv
#   * feature_final    xxxxxxxxx [feature_prereq_c: ...] ...
#     feature_prereq_c xxxxxxxxx [feature_prereq_b: ...] ...
#     feature_prereq_b xxxxxxxxx [feature_prereq_a: ...] ...
#     feature_prereq_a xxxxxxxxx [master: ...] ...
#     master           xxxxxxxxx [origin/master] ...
#
# Executing `git_sync_cl_chain.py -m <message>` causes the following chain of
# commands to be executed:
#
#   $ git checkout feature_prereq_a; git pull; git cl upload -m <message>
#   $ git checkout feature_prereq_b; git pull; git cl upload -m <message>
#   $ git checkout feature_prereq_c; git pull; git cl upload -m <message>
#   $ git checkout feature_final;    git pull; git cl upload -m <message>

import optparse
import os
import sys

import defines
import utils

REPO_ROOT = defines.REPO_ROOT

class Repo(object):
  def __init__(self, name, is_current, upstream):
    self.name = name
    self.is_current = is_current
    self.upstream = upstream

def ParseOptions(argv):
  result = optparse.OptionParser()
  result.add_option('--message', '-m', help='Message for patchset')
  result.add_option('--rebase',
                    help='To use `git pull --rebase` instead of `git pull`',
                    action='store_true')
  result.add_option('--no_upload', '--no-upload',
                    help='Disable uploading to Gerrit', action='store_true')
  (options, args) = result.parse_args(argv)
  options.upload = not options.no_upload
  assert options.message, 'A message for the patchset is required.'
  assert len(args) == 0
  return options

def main(argv):
  options = ParseOptions(argv)
  rebase_args = ['--rebase'] if options.rebase else []
  with utils.ChangedWorkingDirectory(REPO_ROOT, quiet=True):
    branches = [
        parse(line)
        for line in utils.RunCmd(['git', 'branch', '-vv'], quiet=True)]

    current_branch = None
    for branch in branches:
      if branch.is_current:
        current_branch = branch
        break
    assert current_branch is not None

    if current_branch.upstream == None:
      print('Nothing to sync')
      return

    stack = []
    while current_branch:
      stack.append(current_branch)
      if current_branch.upstream is None or current_branch.upstream == 'master':
        break
      current_branch = get_branch_with_name(current_branch.upstream, branches)

    while len(stack) > 0:
      branch = stack.pop()
      print('Syncing ' + branch.name)
      utils.RunCmd(['git', 'checkout', branch.name], quiet=True)
      utils.RunCmd(['git', 'pull'] + rebase_args, quiet=True)
      if options.upload:
        utils.RunCmd(['git', 'cl', 'upload', '-m', options.message], quiet=True)

    utils.RunCmd(['git', 'cl', 'issue'])

def get_branch_with_name(name, branches):
  for branch in branches:
    if branch.name == name:
      return branch
  return None

# Parses a line from the output of `git branch -vv`.
#
# Example output ('*' denotes the current branch):
#
#   $ git branch -vv
#   * feature_final    xxxxxxxxx [feature_prereq_c: ...] ...
#     feature_prereq_c xxxxxxxxx [feature_prereq_b: ...] ...
#     feature_prereq_b xxxxxxxxx [feature_prereq_a: ...] ...
#     feature_prereq_a xxxxxxxxx [master: ...] ...
#     master           xxxxxxxxx [origin/master] ...
def parse(line):
  is_current = False
  if line.startswith('*'):
    is_current = True
    line = line[1:].lstrip()
  else:
    line = line.lstrip()

  name_end_index = line.index(' ')
  name = line[:name_end_index]
  line = line[name_end_index:].lstrip()

  if ('[') not in line or ':' not in line:
    return Repo(name, is_current, None)

  upstream_start_index = line.index('[')
  line = line[upstream_start_index+1:]
  upstream_end_index = line.index(':')
  upstream = line[:upstream_end_index]

  return Repo(name, is_current, upstream)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
