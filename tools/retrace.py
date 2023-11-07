#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os import path

import argparse
import os
import subprocess
import sys

import jdk
import utils


def parse_arguments():
    parser = argparse.ArgumentParser(
        description='R8lib wrapper for retrace tool.')
    parser.add_argument('-c',
                        '--commit-hash',
                        '--commit_hash',
                        help='Commit hash to download r8lib map file for.',
                        default=None)
    parser.add_argument('--version',
                        help='Version to download r8lib map file for.',
                        default=None)
    parser.add_argument('--tag',
                        help='Tag to download r8lib map file for.',
                        default=None)
    parser.add_argument(
        '--exclude-deps',
        '--exclude_deps',
        default=None,
        action='store_true',
        help='Use the exclude-deps version of the mapping file.')
    parser.add_argument('--map', help='Path to r8lib map.', default=None)
    parser.add_argument('--r8jar', help='Path to r8 jar.', default=None)
    parser.add_argument('--no-r8lib',
                        '--no_r8lib',
                        default=False,
                        action='store_true',
                        help='Use r8.jar and not r8lib.jar.')
    parser.add_argument(
        '--stacktrace',
        help='Path to stacktrace file (read from stdin if not passed).',
        default=None)
    parser.add_argument('--quiet',
                        default=None,
                        action='store_true',
                        help='Disables diagnostics printing to stdout.')
    parser.add_argument(
        '--debug-agent',
        '--debug_agent',
        default=None,
        action='store_true',
        help='Attach a debug-agent to the retracer java process.')
    parser.add_argument(
        '--regex',
        default=None,
        help='Sets a custom regular expression used for parsing')
    parser.add_argument('--verbose',
                        default=None,
                        action='store_true',
                        help='Enables verbose retracing.')
    parser.add_argument('--disable-map-validation',
                        default=None,
                        action='store_true',
                        help='Disable validation of map hash.')
    return parser.parse_args()


def get_map_file(args, temp):
    # default to using the specified map file.
    if args.map:
        return args.map

    # next try to extract it from the tag/version options.
    map_path = utils.find_cloud_storage_file_from_options('r8lib.jar.map', args)
    if map_path:
        return map_path

    # next try to extract it from the stack-trace source-file content.
    if not args.stacktrace:
        if not args.quiet:
            print('Waiting for stack-trace input...')
        args.stacktrace = os.path.join(temp, 'stacktrace.txt')
        open(args.stacktrace, 'w').writelines(sys.stdin.readlines())

    r8_source_file = None
    for line in open(args.stacktrace, 'r'):
        start = line.rfind("(R8_")
        if start > 0:
            end = line.find(":", start)
            content = line[start + 1:end]
            if r8_source_file:
                if content != r8_source_file:
                    print(
                        'WARNING: there are multiple distinct R8 source files:')
                    print(' ' + r8_source_file)
                    print(' ' + content)
            else:
                r8_source_file = content

    if r8_source_file:
        (header, r8_version_or_hash, maphash) = r8_source_file.split('_')
        # If the command-line specified --exclude-deps then assume it is as previous
        # versions will not be marked as such in the source-file line.
        is_excldeps = args.exclude_deps
        excldeps_start = r8_version_or_hash.find('+excldeps')
        if (excldeps_start > 0):
            is_excldeps = True
            r8_version_or_hash = r8_version_or_hash[0:excldeps_start]
        if len(r8_version_or_hash) < 40:
            args.version = r8_version_or_hash
        else:
            args.commit_hash = r8_version_or_hash
        map_path = None
        if path.exists(utils.R8LIB_MAP) and get_hash_from_map_file(
                utils.R8LIB_MAP) == maphash:
            return utils.R8LIB_MAP

        try:
            map_path = utils.find_cloud_storage_file_from_options(
                'r8lib' + ('-exclude-deps' if is_excldeps else '') + '.jar.map',
                args)
        except Exception as e:
            print(e)
            print('WARNING: Falling back to using local mapping file.')

        if map_path and not args.disable_map_validation:
            check_maphash(map_path, maphash, args)
            return map_path

    # If no other map file was found, use the local mapping file.
    if args.r8jar:
        return args.r8jar + ".map"
    return utils.R8LIB_MAP


def check_maphash(mapping_path, maphash, args):
    infile_maphash = get_hash_from_map_file(mapping_path)
    if infile_maphash != maphash:
        print('ERROR: The mapping file hash does not match the R8 line')
        print('  In mapping file: ' + infile_maphash)
        print('  In source file:  ' + maphash)
        if (not args.exclude_deps):
            print(
                'If this could be a version without internalized dependencies '
                + 'try passing --exclude-deps')
        sys.exit(1)


def get_hash_from_map_file(mapping_path):
    map_hash_header = "# pg_map_hash: SHA-256 "
    for line in open(mapping_path, 'r'):
        if line.startswith(map_hash_header):
            return line[len(map_hash_header):].strip()


def main():
    args = parse_arguments()
    with utils.TempDir() as temp:
        map_path = get_map_file(args, temp)
        return run(map_path,
                   args.stacktrace,
                   args.r8jar,
                   args.no_r8lib,
                   quiet=args.quiet,
                   debug=args.debug_agent,
                   regex=args.regex,
                   verbose=args.verbose)


def run(map_path,
        stacktrace,
        r8jar,
        no_r8lib,
        quiet=False,
        debug=False,
        regex=None,
        verbose=False):
    retrace_args = [jdk.GetJavaExecutable()]

    if debug:
        retrace_args.append(
            '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
        )

    if not r8jar:
        r8jar = utils.R8_JAR if no_r8lib else utils.R8LIB

    retrace_args += [
        '-cp', r8jar, 'com.android.tools.r8.retrace.Retrace', map_path
    ]

    if regex:
        retrace_args.append('--regex')
        retrace_args.append(regex)

    if quiet:
        retrace_args.append('--quiet')

    if stacktrace:
        retrace_args.append(stacktrace)

    if verbose:
        retrace_args.append('--verbose')

    utils.PrintCmd(retrace_args, quiet=quiet)
    return subprocess.call(retrace_args)


if __name__ == '__main__':
    sys.exit(main())
