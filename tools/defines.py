# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import sys

# Static defines for use in tools (and utils.py).
# There must be no dependencies on other utils from this file!

TOOLS_DIR = os.path.abspath(os.path.normpath(os.path.join(__file__, '..')))
REPO_ROOT = os.path.realpath(os.path.join(TOOLS_DIR, '..'))
THIRD_PARTY = os.path.join(REPO_ROOT, 'third_party')


def IsWindows():
    return sys.platform.startswith('win')


def IsLinux():
    return sys.platform.startswith('linux')


def IsOsX():
    return sys.platform.startswith('darwin')
