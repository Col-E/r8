#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils

import optparse
import sys
import toolhelper


def ParseOptions(argv):
    parser = optparse.OptionParser(usage='%prog [options] -- [D8 options]')
    parser.add_option('-c',
                      '--commit-hash',
                      '--commit_hash',
                      help='Commit hash of D8 to use.',
                      default=None)
    parser.add_option('--print-runtimeraw',
                      '--print_runtimeraw',
                      metavar='BENCHMARKNAME',
                      help='Print the line \'<BENCHMARKNAME>(RunTimeRaw):' +
                      ' <elapsed> ms\' at the end where <elapsed> is' +
                      ' the elapsed time in milliseconds.')
    parser.add_option('--version', help='Version of D8 to use.', default=None)
    parser.add_option('--tag', help='Tag of D8 to use.', default=None)
    return parser.parse_args(argv)


def main(argv):
    (options, args) = ParseOptions(sys.argv)
    d8_args = args[1:]
    time_consumer = lambda duration: print_duration(duration, options)
    return toolhelper.run('d8',
                          d8_args,
                          jar=utils.find_r8_jar_from_options(options),
                          main='com.android.tools.r8.D8',
                          time_consumer=time_consumer)


def print_duration(duration, options):
    benchmark_name = options.print_runtimeraw
    if benchmark_name:
        print('%s-Total(RunTimeRaw): %s ms' % (benchmark_name, duration))


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
