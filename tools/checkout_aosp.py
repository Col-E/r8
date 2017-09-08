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

AOSP_MANIFEST_XML = join(utils.REPO_ROOT, 'third_party',
  'aosp_manifest.xml')
AOSP_MANIFEST_URL = 'https://android.googlesource.com/platform/manifest'

J_DEFAULT = multiprocessing.cpu_count() - 2

# Checkout AOSP source to the specified direcotry using the speficied manifest.
def checkout_aosp(aosp_root, manifest_xml, concurrency):
  manifests_dir = join(aosp_root, '.repo', 'manifests')
  utils.makedirs_if_needed(manifests_dir)

  copy2(manifest_xml, manifests_dir)
  check_call(['repo', 'init', '-u', AOSP_MANIFEST_URL, '-m',
    basename(manifest_xml), '--depth=1'], cwd = aosp_root)

  check_call(['repo', 'sync', '-dq', '-j' + concurrency], cwd = aosp_root)

def parse_arguments():
  parser = argparse.ArgumentParser(
      description = 'Checkout the AOSP source tree.')
  parser.add_argument('--aosp-root',
                      help='Root of the AOSP checkout. ' +
                           'Defaults to current working directory.',
                      default=os.getcwd())
  parser.add_argument('--manifest',
                      help='Manifest to use for the checkout. ' +
                           'Defaults to ' + AOSP_MANIFEST_XML + '.',
                      default=AOSP_MANIFEST_XML)
  parser.add_argument('-j',
                      help='Projects to fetch simultaneously. ' +
                           'Defaults to ' + str(J_DEFAULT) + '.',
                      default=str(J_DEFAULT))
  return parser.parse_args()

def Main():
  args = parse_arguments()
  checkout_aosp(args.aosp_root, args.manifest, args.j)

if __name__ == '__main__':
  sys.exit(Main())
