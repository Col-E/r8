#!/usr/bin/env python3
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import jdk
import os.path
import re
import subprocess
import sys
import urllib.request

import utils

# Grep match string for 'Version X.Y.Z[-dev]'
VERSION_EXP = '^Version [[:digit:]]\+.[[:digit:]]\+.[[:digit:]]\+\(\|-dev\)$'

# R8 is located in the 'builder' library.
AGP_MAVEN = "https://dl.google.com/android/maven2/com/android/tools/build/builder"


def parse_options():
    parser = argparse.ArgumentParser(description='Tag R8 Versions')
    parser.add_argument('--branch',
                        help='The R8 branch to tag versions on, eg, origin/3.0')
    parser.add_argument('--agp',
                        help='The AGP to compute the tag for, eg, 4.2.0-beta03')
    parser.add_argument(
        '--dry-run',
        default=False,
        action='store_true',
        help='Print the state changing commands without running them.')
    return parser.parse_args()


def run(options, cmd):
    print(' '.join(cmd))
    if not options.dry_run:
        subprocess.check_call(cmd)


def main():
    args = parse_options()
    if args.branch:
        tag_r8_branch(args.branch, args)
    elif args.agp:
        if (args.agp == 'all'):
            tag_all_agp_versions(args)
        else:
            tag_agp_version(args.agp, args)
    else:
        print("Should use a top-level option, such as --branch or --agp.")
        return 1
    return 0


def prepare_print_version(dist, temp):
    wrapper_file = os.path.join(
        utils.REPO_ROOT,
        'src/main/java/com/android/tools/r8/utils/PrintR8Version.java')
    cmd = [
        jdk.GetJavacExecutable(),
        wrapper_file,
        '-d',
        temp,
        '-cp',
        dist,
    ]
    utils.PrintCmd(cmd)
    subprocess.check_output(cmd)
    return temp


def get_tag_info_on_origin(tag):
    output = subprocess.check_output(
        ['git', 'ls-remote', '--tags', 'origin', tag]).decode('utf-8')
    if len(output.strip()) == 0:
        return None
    return output


def tag_all_agp_versions(args):
    with utils.TempDir() as temp:
        url = "%s/maven-metadata.xml" % AGP_MAVEN
        metadata = os.path.join(temp, "maven-metadata.xml")
        try:
            urllib.request.urlretrieve(url, metadata)
        except urllib.error.HTTPError as e:
            print('Could not find maven-metadata.xml for agp')
            print(e)
            return 1
        with open(metadata, 'r') as file:
            data = file.read()
            pattern = r'<version>(.+)</version>'
            matches = re.findall(pattern, data)
            matches.reverse()
            for version in matches:
                print('Tagging agp version ' + version)
                tag_agp_version(version, args)


def tag_agp_version(agp, args):
    tag = 'agp-%s' % agp
    result = get_tag_info_on_origin(tag)
    if result:
        print('Tag %s is already present' % tag)
        print(result)
        subprocess.call(['git', 'show', '--oneline', '-s', tag])
        return 0
    with utils.TempDir() as temp:
        url = "%s/%s/builder-%s.jar" % (AGP_MAVEN, agp, agp)
        jar = os.path.join(temp, "agp.jar")
        try:
            urllib.request.urlretrieve(url, jar)
        except urllib.error.HTTPError as e:
            print('Could not find jar for agp %s' % agp)
            print(e)
            return 1
        print_version_helper = prepare_print_version(utils.R8_JAR, temp)
        output = subprocess.check_output([
            jdk.GetJavaExecutable(), '-cp',
            ':'.join([jar, print_version_helper]),
            'com.android.tools.r8.utils.PrintR8Version'
        ]).decode('utf-8')
        version = output.split(' ')[0]
        run(args, ['git', 'tag', '-f', tag, '-m', tag, '%s^{}' % version])
        run(args, ['git', 'push', 'origin', tag])


def tag_r8_branch(branch, args):
    if not branch.startswith('origin/'):
        print('Expected branch to start with origin/')
        return 1
    output = subprocess.check_output(
        ['git', 'log', '--pretty=format:%H\t%s', '--grep', VERSION_EXP,
         branch]).decode('utf-8')
    for l in output.split('\n'):
        (hash, subject) = l.split('\t')
        m = re.search('Version (.+)', subject)
        if not m:
            print('Unable to find a version for line: %s' % l)
            continue
        version = m.group(1)
        result = get_tag_info_on_origin(version)
        if not result:
            run(args, ['git', 'tag', '-a', version, '-m', version, hash])
            run(args, ['git', 'push', 'origin', version])
    if args.dry_run:
        print('Dry run complete. None of the above have been executed.')


if __name__ == '__main__':
    sys.exit(main())
