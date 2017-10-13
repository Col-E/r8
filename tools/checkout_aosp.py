#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os.path import basename, join
from shutil import copy2
from subprocess import check_call
import argparse
import multiprocessing
import os
import sys

import utils
import utils_aosp

AOSP_MANIFEST_XML = join(utils.REPO_ROOT, 'third_party',
  'aosp_manifest.xml')
AOSP_MANIFEST_URL = 'https://android.googlesource.com/platform/manifest'
J_DEFAULT = multiprocessing.cpu_count() - 2

# Checkout AOSP source to the specified direcotry using the speficied manifest.
def checkout_aosp(aosp_root, url, branch, manifest_xml, concurrency, shallow):
  utils.makedirs_if_needed(aosp_root)
  command = ['repo', 'init', '-u', url]
  if (shallow):
    command.extend(['--depth=1'])
  if (branch):
    command.extend(['-b', branch])
  else:
    manifests_dir = join(aosp_root, '.repo', 'manifests')
    utils.makedirs_if_needed(manifests_dir)
    copy2(manifest_xml, manifests_dir)
    command.extend(['-m', basename(manifest_xml)])
  check_call(command, cwd = aosp_root)

  check_call(['repo', 'sync', '-dq', '-j' + concurrency], cwd = aosp_root)

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Checkout the AOSP source tree.')
  utils_aosp.add_root_argument(parser)
  parser.add_argument('--url',
                      help='URL the repo. ' +
                           'Defaults to ' + AOSP_MANIFEST_URL + '.',
                      default=AOSP_MANIFEST_URL)
  parser.add_argument('--manifest',
                      help='Manifest to use for the checkout. ' +
                           'Defaults to ' + AOSP_MANIFEST_XML + '.',
                      default=AOSP_MANIFEST_XML)
  parser.add_argument('--branch',
                      help='Branch to checkout. This overrides ' +
                           'passing --manifest')
  parser.add_argument('--shallow',
                      action = 'store_true',
                      help='Shallow checkout.')
  parser.add_argument('-j',
                      help='Projects to fetch simultaneously. ' +
                           'Defaults to ' + str(J_DEFAULT) + '.',
                      default=str(J_DEFAULT))
  return parser.parse_args()

def Main():
  args = parse_arguments()
  checkout_aosp(args.aosp_root, args.url, args.branch, args.manifest,
                args.j, args.shallow)

if __name__ == '__main__':
  sys.exit(Main())
