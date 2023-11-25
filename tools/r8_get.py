#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import sys
import argparse
import compiledump


def parse_arguments():
    parser = argparse.ArgumentParser(
        description='Helper to fetch r8.jar from cloudstorage.')
    parser.add_argument(
        '-v',
        '--version',
        help='Version or commit-hash to download '
        '(e.g., 3.3.50 or 33ae86d80351efc4d632452331d06cb97e42f2a7).',
        required=True)
    parser.add_argument(
        '--outdir',
        help='Output directory to place the r8.jar in (default cwd).',
        default=None)
    parser.add_argument(
        '--nolib',
        help='Use the non-lib distribution (default uses the lib distribution)',
        default=False,
        action='store_true')
    return parser.parse_args()


def main():
    args = parse_arguments()
    outdir = args.outdir if args.outdir else ''
    print(compiledump.download_distribution(args.version, args, outdir))
    return 0


if __name__ == '__main__':
    sys.exit(main())
