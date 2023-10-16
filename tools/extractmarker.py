#!/usr/bin/env python3
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import sys
import toolhelper


def extractmarker(apk_or_dex, build=True):
    stdout = toolhelper.run('extractmarker', [apk_or_dex],
                            build=build,
                            return_stdout=True)
    return stdout


def parse_options(argv):
    result = argparse.ArgumentParser(
        description='Relayout a given APK using a startup profile.')
    result.add_argument('--no-build',
                        action='store_true',
                        default=False,
                        help='To disable building using gradle')
    options, args = result.parse_known_args(argv)
    return options, args


def main(argv):
    options, args = parse_options(argv)
    build = not options.no_build
    for apk_or_dex in args:
        print(extractmarker(apk_or_dex, build=build))
        build = False


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
