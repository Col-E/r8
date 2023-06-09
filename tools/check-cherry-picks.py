#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import subprocess
import sys
import re
import r8_release

class Branch:
  def __init__(self, name, first, last=None):
    self.name = name
    self.first = first
    self.last = last # optional last for testing purposes.

  def origin(self):
    return "origin/%s" % self.name

  def last_or_origin(self):
    return self.last if self.last else self.origin()

  def __str__(self):
    return self.name

# The initial commit is the furthest back we need to search on main.
# Currently, it is the merge point of main onto 4.0.23-dev
MAIN = Branch('main', 'a2e203580aa00a36f85cd68d3d584b97aef34d59')
OLDEST_BRANCH_VERSION = (4, 0)
DEV_BRANCH_VERSION = [int(s) for s in r8_release.R8_DEV_BRANCH.split('.')]

# List of change ids that should not be reported.
IGNORED = [
    'I92d7bf3afbf609fdea21683941cfd15c90305cf2'
]

VERBOSE = False

# Helper to call and decode a shell command.
def run_cmd(cmd):
  if VERBOSE:
    print(' '.join(cmd))
  return subprocess.check_output(cmd).decode('UTF-8')

# Comparator on major and minor branch versions.
def branch_version_less_than(b1, b2):
  if b1[0] < b2[0]:
    return True
  if b1[0] == b2[0] and b1[1] < b2[1]:
    return True
  return False

# Find all release branches between OLDEST_BRANCH and DEV_BRANCH
def get_release_branches():
  # Release branches are assumed to be of the form 'origin/X.Y'
  out = run_cmd(['git', 'branch', '-r', '-l'])
  pattern = re.compile('origin/(\d+).(\d+)')
  releases = []
  for line in out.split('\n'):
    m = pattern.search(line.strip())
    if m:
      major = m.group(1)
      minor = m.group(2)
      if major and minor:
        candidate = (int(major), int(minor))
        if branch_version_less_than(candidate, OLDEST_BRANCH_VERSION):
          continue
        if branch_version_less_than(candidate, DEV_BRANCH_VERSION):
          releases.extend(find_dev_cutoff(candidate))
  return releases

# Find the most recent commit hash that is for a -dev version.
# This is the starting point for the map of commits after cutoff from main.
def find_dev_cutoff(branch_version):
  out = run_cmd([
      'git',
      'log',
      'origin/%d.%d' % branch_version,
      '--grep', 'Version .*-dev',
      '--pretty=oneline',
  ])
  # Format of output is: <hash> Version <version>-dev
  try:
    hash = out[0:out.index(' ')]
    return [Branch('%d.%d' % branch_version, hash)]
  except ValueError:
    throw_error("Failed to find dev cutoff for branch %d.%d" % branch_version)

# Build a map from each "Change-Id" hash to the hash of its commit.
def get_change_id_map(branch):
  out = run_cmd([
      'git',
      'log',
      '%s..%s' % (branch.first, branch.last_or_origin())
  ])
  map = {}
  current_commit = None
  for line in out.split('\n'):
    if line.startswith('commit '):
      current_commit = line[len('commit '):]
      assert len(current_commit) == 40
    elif line.strip().startswith('Change-Id: '):
      change_id = line.strip()[len('Change-Id: '):]
      assert len(change_id) == 41
      map[change_id] = current_commit
  return map

# Check if a specific commit is present on a specific branch.
def is_commit_in(commit, branch):
  out = run_cmd(['git', 'branch', '-r', branch.origin(), '--contains', commit])
  return out.strip() == branch.origin()

def main():
  found_errors = False
  # The main map is all commits back to the "init" point.
  main_map = get_change_id_map(MAIN)
  # Compute the release branches.
  release_branches = get_release_branches()
  # Populate the release maps with all commits after the last -dev point.
  release_maps = {}
  for branch in release_branches:
    release_maps[branch.name] = get_change_id_map(branch)
  # Each branch is then compared forwards with each subsequent branch.
  for i in range(len(release_branches)):
    branch = release_branches[i]
    newer_branches = release_branches[i+1:]
    if (len(newer_branches) == 0):
      print('Last non-dev release branch is %s, nothing to check.' % branch)
      continue
    print('Checking branch %s.' % branch)
    changes = release_maps[branch.name]
    cherry_picks_count = 0
    for change in changes.keys():
      is_cherry_pick = False
      missing_from = None
      commit_on_main = main_map.get(change)
      for newer_branch in newer_branches:
        if change in release_maps[newer_branch.name]:
          is_cherry_pick = True
          # If the change is in the release mappings check for holes.
          if missing_from:
            found_errors = change_error(
                change,
                'Error: missing Change-Id %s on branch %s. '
                'Is present on %s and again on %s.' % (
                    change, missing_from, branch, newer_branch,
                ))
        elif commit_on_main:
          is_cherry_pick = True
          # The change is not in the non-dev part of the branch, so we need to
          # check that the fork from main included the change.
          if not is_commit_in(commit_on_main, newer_branch):
            found_errors = change_error(
                change,
                'Error: missing Change-Id %s on branch %s. '
                'Is present on %s and on main as commit %s.' % (
                    change, newer_branch, branch, commit_on_main
                ))
        else:
          # The change is not on "main" so we just record for holes on releases.
          missing_from = newer_branch
      if is_cherry_pick:
        cherry_picks_count += 1
    print('Found %d cherry-picks (out of %d commits).' % (
        cherry_picks_count, len(changes)))

  if found_errors:
    return 1
  return 0

def change_error(change, msg):
  if change in IGNORED:
    return False
  error(msg)
  return True

def throw_error(msg):
  raise ValueError(msg)

def error(msg):
  print(msg, file=sys.stderr)

if __name__ == '__main__':
  sys.exit(main())
