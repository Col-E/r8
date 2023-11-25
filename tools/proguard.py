#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Run ProGuard, Google's internal version

import os
import subprocess
import sys

import jdk
import utils

# Internal constants, these should not be used outside this script.
# Use the friendly utility methods below.
PG_DIR = os.path.join(utils.THIRD_PARTY, 'proguard')
DEFAULT = 'pg6'
DEFAULT_ALIAS = 'pg'
VERSIONS = {
    'pg5':
        os.path.join(PG_DIR, 'proguard5.2.1', 'lib', 'proguard.jar'),
    'pg6':
        os.path.join(PG_DIR, 'proguard6.0.1', 'lib', 'proguard.jar'),
    'pg7':
        os.path.join(PG_DIR, 'proguard-7.0.0', 'lib', 'proguard.jar'),
    'pg_internal':
        os.path.join(PG_DIR, 'proguard_internal_159423826',
                     'ProGuard_deploy.jar'),
}
# Add alias for the default version.
VERSIONS[DEFAULT_ALIAS] = VERSIONS[DEFAULT]


# Get versions sorted (nice for argument lists)
def getVersions():
    versions = list(VERSIONS.keys())
    versions.sort()
    return versions


def isValidVersion(version):
    return version in VERSIONS


def getValidatedVersion(version):
    if not isValidVersion(version):
        raise ValueError("Invalid PG version: '%s'" % version)
    return version


def getJar(version=DEFAULT):
    return VERSIONS[getValidatedVersion(version)]


def getRetraceJar(version=DEFAULT):
    if version == 'pg_internal':
        raise ValueError("No retrace in internal distribution")
    return getJar().replace('proguard.jar', 'retrace.jar')


def getCmd(args, version=DEFAULT, jvmArgs=None):
    cmd = []
    if jvmArgs:
        cmd.extend(jvmArgs)
    cmd.extend([jdk.GetJavaExecutable(), '-jar', getJar(version)])
    cmd.extend(args)
    return cmd


def run(args,
        version=DEFAULT,
        track_memory_file=None,
        stdout=None,
        stderr=None):
    cmd = []
    if track_memory_file:
        cmd.extend(['tools/track_memory.sh', track_memory_file])
    cmd.extend(getCmd(args, version))
    utils.PrintCmd(cmd)
    subprocess.call(cmd, stdout=stdout, stderr=stderr)


def Main():
    run(sys.argv[1:])


if __name__ == '__main__':
    sys.exit(Main())
