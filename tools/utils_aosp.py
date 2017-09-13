#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.


from os.path import join
from subprocess import check_call

import os

import utils

AOSP_HELPER_SH = join(utils.REPO_ROOT, 'scripts', 'aosp_helper.sh')

DEFAULT_LUNCH = 'aosp_x86-eng'

DEFAULT_ROOT = join(utils.REPO_ROOT, 'build', 'aosp')

def add_common_arguments(parser):
  parser.add_argument('--aosp-root',
      help='Root of the AOSP checkout. ' +
           'Defaults to ' +  DEFAULT_ROOT +'.',
      default=DEFAULT_ROOT)
  parser.add_argument('--lunch',
      help='Build menu. ' +
           'Defaults to ' + DEFAULT_LUNCH + '.',
      default=DEFAULT_LUNCH)

def run_through_aosp_helper(lunch, args, cwd):
  args[0:0] = [AOSP_HELPER_SH, lunch]
  check_call(args, cwd = cwd)
