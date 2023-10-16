#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Run R8 or PG on 'third_party/r8/r8.jar'.
# Report Golem-compatible CodeSize and RunTimeRaw values:
#
#     <NAME>-Total(CodeSize): <size>
#     <NAME>-Total(RunTimeRaw>: <time> ms
#
# and also detailed segment sizes for each classfile segment:
#
#    <NAME>-Code(CodeSize): <size>
#    <NAME>-AnnotationSets(CodeSize): <size>
#    ...
#
# Uses the R8CfSegments Java tool which is downloaded as an x20 dependency.
# To make changes to the R8CfSegments tool one can use the gradle target -
# remember to update the x20 dependency afterwards if you want the numbers
# tracked.

from __future__ import print_function
import argparse
import minify_tool
import os
import sys
import utils


def parse_arguments():
    parser = argparse.ArgumentParser(
        description='Run R8 or PG on'
        ' third_party/r8/r8.jar.'
        ' Report Golem-compatible CodeSize and RunTimeRaw values.')
    parser.add_argument('--tool',
                        choices=['pg', 'r8'],
                        required=True,
                        help='Compiler tool to use.')
    parser.add_argument(
        '--name',
        required=True,
        help='Results will be printed using the specified benchmark name (e.g.'
        ' <NAME>-<segment>(CodeSize): <bytes>), the full size is reported'
        ' with <NAME>-Total(CodeSize)')
    parser.add_argument('--print-memoryuse',
                        help='Prints the line \'<NAME>-Total(MemoryUse):'
                        ' <mem>\' at the end where <mem> is the peak'
                        ' peak resident set size (VmHWM) in bytes.',
                        default=False,
                        action='store_true')
    parser.add_argument('--output',
                        help='Output directory to keep the generated files')
    return parser.parse_args()


def Main():
    args = parse_arguments()
    utils.check_java_version()
    output_dir = args.output
    with utils.TempDir() as temp_dir:
        if not output_dir:
            output_dir = temp_dir
        track_memory_file = None
        if args.print_memoryuse:
            track_memory_file = os.path.join(output_dir,
                                             utils.MEMORY_USE_TMP_FILE)
        if args.tool == 'pg':
            utils.print_cfsegments(args.name, [utils.PINNED_PGR8_JAR])
        else:
            out_file = os.path.join(output_dir, 'out.jar')
            return_code = minify_tool.minify_tool(
                input_jar=utils.PINNED_R8_JAR,
                output_jar=out_file,
                debug=False,
                build=False,
                track_memory_file=track_memory_file,
                benchmark_name=args.name + "-Total")
            if return_code != 0:
                sys.exit(return_code)

            utils.print_cfsegments(args.name, [out_file])


if __name__ == '__main__':
    sys.exit(Main())
