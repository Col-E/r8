#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for triggering bots on specific commits.

import json
import optparse
import os
import re
import subprocess
import sys
import urllib

import utils

LUCI_SCHEDULE = os.path.join(utils.REPO_ROOT, 'infra', 'config', 'global',
                             'luci-scheduler.cfg')
# Trigger lines have the format:
#   triggers: "BUILDER_NAME"
TRIGGERS_RE = r'^  triggers: "(\w.*)"'

def ParseOptions():
  result = optparse.OptionParser()
  result.add_option('--release',
                    help='Run on the release branch builders.',
                    default=False, action='store_true')
  result.add_option('--builder', help='Trigger specific builder')
  return result.parse_args()

def get_builders():
  is_release = False
  master_builders = []
  release_builders = []
  with open(LUCI_SCHEDULE, 'r') as fp:
    lines = fp.readlines()
    for line in lines:
      if 'branch-gitiles-trigger' in line:
        is_release = True
      match = re.match(TRIGGERS_RE, line)
      if match:
        builder = match.group(1)
        if is_release:
          assert 'release' in builder
          release_builders.append(builder)
        else:
          assert 'release' not in builder
          master_builders.append(builder)
  print 'Master builders:\n  ' + '\n  '.join(master_builders)
  print 'Release builders:\n  ' + '\n  '.join(release_builders)
  return (master_builders, release_builders)

def sanity_check_url(url):
  a = urllib.urlopen(url)
  if a.getcode() != 200:
    raise Exception('Url: %s \n returned %s' % (url, a.getcode()))

def trigger_builders(builders, commit):
  commit_url = 'https://r8.googlesource.com/r8/+/%s' % commit
  sanity_check_url(commit_url)
  for builder in builders:
    cmd = ['bb', 'add', 'r8/ci/%s' % builder , '-commit', commit_url]
    subprocess.check_call(cmd)

def Main():
  (options, args) = ParseOptions()
  if len(args) != 1:
    print 'Takes exactly one argument, the commit to run'
    return 1
  commit = args[0]
  (master_builders, release_builders) = get_builders()
  if options.builder:
    builder = options.builder
    assert builder in master_builders or builder in release_builders
    trigger_builders([builder], commit)
  else:
    trigger_builders(release_builders if options.release else master_builders,
                     commit)

if __name__ == '__main__':
  sys.exit(Main())
