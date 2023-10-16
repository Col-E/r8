#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for triggering bots on specific commits.

import json
import git_utils
import optparse
import os
import re
import subprocess
import sys
import urllib
from urllib.request import urlopen
import utils

LUCI_SCHEDULE = os.path.join(utils.REPO_ROOT, 'infra', 'config', 'global',
                             'generated', 'luci-scheduler.cfg')
# Trigger lines have the format:
#   triggers: "BUILDER_NAME"
TRIGGERS_RE = r'^  triggers: "(\w.*)"'

DESUGAR_JDK11_BOT = 'lib_desugar-archive-jdk11'
DESUGAR_JDK11_LEGACY_BOT = 'lib_desugar-archive-jdk11-legacy'
DESUGAR_JDK8_BOT = 'lib_desugar-archive-jdk8'
SMALI_BOT = 'smali'


def ParseOptions():
    result = optparse.OptionParser()
    result.add_option('--release',
                      help='Run on the release branch builders.',
                      default=False,
                      action='store_true')
    result.add_option('--cl',
                      metavar=('<url>'),
                      help='Run the specified cl on the bots. This should be '
                      'the full url, e.g., '
                      'https://r8-review.googlesource.com/c/r8/+/37420/1')
    result.add_option('--desugar-jdk11',
                      help='Run the jdk11 library desugar and archiving bot.',
                      default=False,
                      action='store_true')
    result.add_option(
        '--desugar-jdk11-legacy',
        help='Run the jdk11 legacy library desugar and archiving bot.',
        default=False,
        action='store_true')
    result.add_option('--desugar-jdk8',
                      help='Run the jdk8 library desugar and archiving bot.',
                      default=False,
                      action='store_true')
    result.add_option('--smali',
                      metavar=('<version>'),
                      help='Build smali version <version>.')

    result.add_option('--builder', help='Trigger specific builder')
    return result.parse_args()


def get_builders():

    is_release = False
    main_builders = []
    release_builders = []
    with open(LUCI_SCHEDULE, 'r') as fp:
        lines = fp.readlines()
        for line in lines:
            if 'branch-gitiles' in line:
                is_release = True
            if 'main-gitiles-trigger' in line:
                is_release = False
            match = re.match(TRIGGERS_RE, line)
            if match:
                builder = match.group(1)
                if is_release:
                    assert 'release' in builder, builder
                    release_builders.append(builder)
                else:
                    assert 'release' not in builder, builder
                    main_builders.append(builder)
    print('Desugar jdk11 builder:\n  ' + DESUGAR_JDK11_BOT)
    print('Desugar jdk11 legacy builder:\n  ' + DESUGAR_JDK11_LEGACY_BOT)
    print('Desugar jdk8 builder:\n  ' + DESUGAR_JDK8_BOT)
    print('Smali builder:\n  ' + SMALI_BOT)
    print('Main builders:\n  ' + '\n  '.join(main_builders))
    print('Release builders:\n  ' + '\n  '.join(release_builders))
    return (main_builders, release_builders)


def sanity_check_url(url):
    a = urlopen(url)
    if a.getcode() != 200:
        raise Exception('Url: %s \n returned %s' % (url, a.getcode()))


def trigger_builders(builders, commit):
    commit_url = 'https://r8.googlesource.com/r8/+/%s' % commit
    sanity_check_url(commit_url)
    for builder in builders:
        cmd = ['bb', 'add', 'r8/ci/%s' % builder, '-commit', commit_url]
        subprocess.check_call(cmd)


def trigger_smali_builder(version):
    utils.check_basic_semver_version(
        version,
        'use semantic version of the smali version to built (pre-releases are not supported)',
        allowPrerelease=False)
    cmd = [
        'bb', 'add',
        'r8/ci/%s' % SMALI_BOT, '-p',
        'test_options=["--version", "%s"]' % version
    ]
    subprocess.check_call(cmd)


def trigger_cl(builders, cl_url):
    for builder in builders:
        cmd = ['bb', 'add', 'r8/ci/%s' % builder, '-cl', cl_url]
        subprocess.check_call(cmd)


def Main():
    (options, args) = ParseOptions()
    desugar = options.desugar_jdk11 or options.desugar_jdk11_legacy or options.desugar_jdk8
    requires_commit = not options.cl and not desugar and not options.smali
    if len(args) != 1 and requires_commit:
        print('Takes exactly one argument, the commit to run')
        return 1

    if options.cl and options.release:
        print('You can\'t run cls on the release bots')
        return 1

    if options.cl and desugar:
        print('You can\'t run cls on the desugar bot')
        return 1

    if options.cl and options.smali:
        print('You can\'t run cls on the smali bot')
        return 1

    if options.smali:
        if not options.release:
            print('Only release versions of smali can be built')
            return 1

        trigger_smali_builder(options.smali)
        return

    commit = None if not requires_commit else args[0]
    (main_builders, release_builders) = get_builders()
    builders = release_builders if options.release else main_builders
    if options.builder:
        builder = options.builder
        assert builder in main_builders or builder in release_builders
        builders = [options.builder]
    if desugar:
        assert options.desugar_jdk11 or options.desugar_jdk11_legacy or options.desugar_jdk8
        if options.desugar_jdk11:
            builders = [DESUGAR_JDK11_BOT]
        elif options.desugar_jdk11_legacy:
            builders = [DESUGAR_JDK11_LEGACY_BOT]
        else:
            builders = [DESUGAR_JDK8_BOT]
        commit = git_utils.GetHeadRevision(utils.REPO_ROOT, use_main=True)
    if options.cl:
        trigger_cl(builders, options.cl)
    else:
        assert commit
        trigger_builders(builders, commit)


if __name__ == '__main__':
    sys.exit(Main())
