#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils
import subprocess

def GitClone(url, checkout_dir):
  cmd = ['git', 'clone', url, checkout_dir]
  utils.PrintCmd(cmd)
  return subprocess.check_call(cmd)

def GetHeadRevision(checkout_dir):
  cmd = ['git', 'rev-parse', 'HEAD']
  utils.PrintCmd(cmd)
  with ChangedWorkingDirectory(checkout_dir):
    return subprocess.check_output(cmd).strip()
