#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import minify_tool
import os
import sys
import utils


PINNED_R8_JAR = os.path.join(utils.REPO_ROOT, 'third_party/r8/r8.jar')

parser = argparse.ArgumentParser()
parser.add_argument(
    '--print-runtimeraw', metavar='BENCHMARKNAME',
    help='Print "<BENCHMARKNAME>(RunTimeRaw): <elapsed> ms" at the end')


if __name__ == '__main__':
  sys.exit(minify_tool.minify_tool(input_jar=PINNED_R8_JAR, debug=False,
                                   build=False, **vars(parser.parse_args())))
