#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for triggering bots on specific commits.

import json
import git_utils
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

DESUGAR_BOT = 'archive_lib_desugar'

def ParseOptions():
  result = optparse.OptionParser()
  result.add_option('--release',
                    help='Run on the release branch builders.',
                    default=False, action='store_true')
  result.add_option('--cl',
                    help='Run the specified cl on the bots. This should be '
                    'the full url, e.g., '
                    'https://r8-review.googlesource.com/c/r8/+/37420/1')
  result.add_option('--desugar',
                    help='Run the library desugar and archiving bot.',
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
  assert DESUGAR_BOT in master_builders
  print 'Desugar builder:\n  ' + DESUGAR_BOT
  master_builders.remove(DESUGAR_BOT)
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

def trigger_cl(builders, cl_url):
  for builder in builders:
    cmd = ['bb', 'add', 'r8/ci/%s' % builder , '-cl', cl_url]
    subprocess.check_call(cmd)

def Main():
  (options, args) = ParseOptions()
  if len(args) != 1 and not options.cl and not options.desugar:
    print 'Takes exactly one argument, the commit to run'
    return 1

  if options.cl and options.release:
    print 'You can\'t run cls on the release bots'
    return 1

  if options.cl and options.desugar:
    print 'You can\'t run cls on the desugar bot'
    return 1

  commit = None if (options.cl or options.desugar)  else args[0]
  (master_builders, release_builders) = get_builders()
  builders = release_builders if options.release else master_builders
  if options.builder:
    builder = options.builder
    assert builder in master_builders or builder in release_builders
    builders = [options.builder]
  if options.desugar:
    builders = [DESUGAR_BOT]
    commit = git_utils.GetHeadRevision(utils.REPO_ROOT, use_master=True)
  if options.cl:
    trigger_cl(builders, options.cl)
  else:
    assert commit
    trigger_builders(builders, commit)

if __name__ == '__main__':
  sys.exit(Main())
