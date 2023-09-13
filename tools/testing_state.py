#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils

import datetime
import os

CHOICES = ["all", "failing", "past-failing", "outstanding"]
DEFAULT_REPORTS_ROOT = os.path.join(utils.BUILD, "testing-state")

def set_up_test_state(gradle_args, testing_state_mode, testing_state_path):
  if not testing_state_mode:
    return
  if not testing_state_path:
    testing_state_path = os.path.join(DEFAULT_REPORTS_ROOT, utils.get_HEAD_branch())
  testing_state_path = os.path.abspath(testing_state_path)
  gradle_args.append('-Ptesting-state-mode=%s' % testing_state_mode)
  gradle_args.append('-Ptesting-state-path=%s' % testing_state_path)
  prepare_testing_index(testing_state_mode, testing_state_path)

def fresh_testing_index(testing_state_dir):
  number = 0
  while True:
    freshIndex = os.path.join(testing_state_dir, "index.%d.html" % number)
    number += 1
    if not os.path.exists(freshIndex):
      return freshIndex

def prepare_testing_index(testing_state_mode, testing_state_dir):
  if not os.path.exists(testing_state_dir):
    os.makedirs(testing_state_dir)
  index_path = os.path.join(testing_state_dir, "index.html")
  parent_report = None
  resuming = os.path.exists(index_path)
  mode = testing_state_mode if resuming else f"starting (flag: {testing_state_mode})"
  if (resuming):
    parent_report = fresh_testing_index(testing_state_dir)
    os.rename(index_path, parent_report)
  index = open(index_path, "a")
  title = f"Testing: {os.path.basename(testing_state_dir)}"
  # Print a console link to the test report for easy access.
  print("=" * 70)
  print("Test report written to:")
  print(f"  file://{index_path}")
  print("=" * 70)
  # Print the new index content.
  index.write(f"<html><head><title>{title}</title>")
  index.write("<style> * { font-family: monospace; }</style>")
  index.write("<meta http-equiv='refresh' content='10' />")
  index.write(f"</head><body><h1>{title}</h1>")
  index.write(f"<h2>Mode: {mode}</h2>")
  # write index links first to avoid jumping when browsing.
  if parent_report:
    index.write(f"<p><a href=\"file://{parent_report}\">Previous result index</a></p>")
  index.write(f"<p><a href=\"file://{index_path}\">Most recent result index</a></p>")
  index.write(f"<p><a href=\"file://{testing_state_dir}\">Test directories</a></p>")
  # git branch/hash and diff for future reference
  index.write(f"<p>Run on: {datetime.datetime.now()}</p>")
  index.write(f"<p>State path: {testing_state_dir}</p>")
  index.write(f"<p>Git branch: {utils.get_HEAD_branch()}")
  index.write(f"</br>Git SHA: {utils.get_HEAD_sha1()}")
  index.write(f'</br>Git diff summary:\n')
  index.write(f'<pre style="background-color: lightgray">{utils.get_HEAD_diff_stat()}</pre></p>')
  # header for the failing tests
  index.write("<h2>Failing tests (refreshing automatically every 10 seconds)</h2><ul>")
