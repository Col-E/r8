#!/usr/bin/env python3
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import jdk
import utils

VERSION_EXTRACTOR = """
import com.android.tools.r8.Version;
public class VersionExtractor {
  public static void main(String[] args) {
    System.out.println(Version.LABEL);
  }
}
"""


def parse_options():
    parser = argparse.ArgumentParser(description='Tag R8 Versions')
    parser.add_argument('--classpath',
                        action='append',
                        help='Dependencies to add to classpath')
    parser.add_argument('--debug-agent',
                        action='store_true',
                        default=False,
                        help='Create a socket for debugging')
    parser.add_argument(
        '--excldeps-variant',
        action='store_true',
        default=False,
        help='Mark this artifact as an "excldeps" variant of the compiler')
    parser.add_argument('--debug-variant',
                        action='store_true',
                        default=False,
                        help='Compile with debug flag')
    parser.add_argument(
        '--lib',
        action='append',
        help='Additional libraries (JDK 1.8 rt.jar already included)')
    parser.add_argument('--output',
                        required=True,
                        help='The output path for the r8lib')
    parser.add_argument('--pg-conf', action='append', help='Keep configuration')
    parser.add_argument('--pg-map',
                        default=None,
                        help='Input map for distribution and composition')
    parser.add_argument('--r8jar', required=True, help='The R8 jar to compile')
    parser.add_argument('--r8compiler',
                        default='build/libs/r8_with_deps.jar',
                        help='The R8 compiler to use')
    return parser.parse_args()


def get_r8_version(r8jar):
    with utils.TempDir() as temp:
        name = os.path.join(temp, "VersionExtractor.java")
        fd = open(name, 'w')
        fd.write(VERSION_EXTRACTOR)
        fd.close()
        cmd = [jdk.GetJavacExecutable(), '-cp', r8jar, name]
        print(' '.join(cmd))
        cp_separator = ';' if utils.IsWindows() else ':'
        subprocess.check_call(cmd)
        output = subprocess.check_output([
            jdk.GetJavaExecutable(), '-cp',
            cp_separator.join([r8jar, os.path.dirname(name)]),
            'VersionExtractor'
        ]).decode('UTF-8').strip()
        if output == 'main':
            return subprocess.check_output(['git', 'rev-parse',
                                            'HEAD']).decode('UTF-8').strip()
        else:
            return output


def main():
    args = parse_options()
    if not os.path.exists(args.r8jar):
        print("Could not find jar: " + args.r8jar)
        return 1
    version = get_r8_version(args.r8jar)
    variant = '+excldeps' if args.excldeps_variant else ''
    map_id_template = version + variant
    source_file_template = 'R8_%MAP_ID_%MAP_HASH'
    # TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
    cmd = [jdk.GetJavaExecutable(), '-Xmx8g', '-ea']
    if args.debug_agent:
        cmd.extend([
            '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005'
        ])
    cmd.append('-Dcom.android.tools.r8.enableKeepAnnotations=1')
    cmd.extend(['-cp', args.r8compiler, 'com.android.tools.r8.R8'])
    cmd.append(args.r8jar)
    if args.debug_variant:
        cmd.append('--debug')
    cmd.append('--classfile')
    cmd.extend(['--map-id-template', map_id_template])
    cmd.extend(['--source-file-template', source_file_template])
    cmd.extend(['--output', args.output])
    cmd.extend(['--pg-conf-output', args.output + '.config'])
    cmd.extend(['--pg-map-output', args.output + '.map'])
    cmd.extend(['--partition-map-output', args.output + '_map.zip'])
    cmd.extend(['--lib', jdk.GetJdkHome()])
    if args.pg_conf:
        for pgconf in args.pg_conf:
            cmd.extend(['--pg-conf', pgconf])
    if args.lib:
        for lib in args.lib:
            cmd.extend(['--lib', lib])
    if args.classpath:
        for cp in args.classpath:
            cmd.extend(['--classpath', cp])
    if args.pg_map:
        cmd.extend(['--pg-map', args.pg_map])
    print(' '.join(cmd))
    subprocess.check_call(cmd)


if __name__ == '__main__':
    sys.exit(main())
