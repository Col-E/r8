#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils

import datetime
import os

def set_up_test_state(gradle_args, testing_state_path):
  # In the new build the test state directory must be passed explictitly.
  # TODO(b/297316723): Simplify this and just support a single flag: --testing-state <path>
  if not testing_state_path:
    testing_state_path = "%s/test-state/%s" % (utils.BUILD, utils.get_HEAD_branch())
  gradle_args.append('-Ptesting-state=%s' % testing_state_path)
  prepare_testing_index(testing_state_path)

def fresh_testing_index(testing_state_dir):
  number = 0
  while True:
    freshIndex = os.path.join(testing_state_dir, "index.%d.html" % number)
    number += 1
    if not os.path.exists(freshIndex):
      return freshIndex

def prepare_testing_index(testing_state_dir):
  if not os.path.exists(testing_state_dir):
    os.makedirs(testing_state_dir)
  index_path = os.path.join(testing_state_dir, "index.html")
  parent_report = None
  resuming = os.path.exists(index_path)
  if (resuming):
    parent_report = fresh_testing_index(testing_state_dir)
    os.rename(index_path, parent_report)
  index = open(index_path, "a")
  run_prefix = "Resuming" if resuming else "Starting"
  relative_state_dir = os.path.relpath(testing_state_dir)
  title = f"{run_prefix} @ {relative_state_dir}"
  # Print a console link to the test report for easy access.
  print("=" * 70)
  print(f"{run_prefix} test, report written to:")
  print(f"  file://{index_path}")
  print("=" * 70)
  # Print the new index content.
  index.write(f"<html><head><title>{title}</title>")
  index.write("<style> * { font-family: monospace; }</style>")
  index.write("<meta http-equiv='refresh' content='10' />")
  index.write(f"</head><body><h1>{title}</h1>")
  # write index links first to avoid jumping when browsing.
  if parent_report:
    index.write(f"<p><a href=\"file://{parent_report}\">Previous result index</a></p>")
  index.write(f"<p><a href=\"file://{index_path}\">Most recent result index</a></p>")
  index.write(f"<p><a href=\"file://{testing_state_dir}\">Test directories</a></p>")
  # git branch/hash and diff for future reference
  index.write(f"<p>Run on: {datetime.datetime.now()}</p>")
  index.write(f"<p>Git branch: {utils.get_HEAD_branch()}")
  index.write(f"</br>Git SHA: {utils.get_HEAD_sha1()}")
  index.write(f'</br>Git diff summary:\n')
  index.write(f'<pre style="background-color: lightgray">{utils.get_HEAD_diff_stat()}</pre></p>')
  # header for the failing tests
  index.write("<h2>Failing tests (refreshing automatically every 10 seconds)</h2><ul>")
