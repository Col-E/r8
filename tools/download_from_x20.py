#!/usr/bin/env python3
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script for downloading from x20 a dependency in the same way we use cloud
# storage.

import optparse
import os
import shutil
import subprocess
import sys
import tarfile
import utils

GMSCORE_DEPS = '/google/data/ro/teams/r8/deps'


def parse_options():
    return optparse.OptionParser().parse_args()


def download(src, dest):
    print('Downloading %s to %s' % (src, dest))
    shutil.copyfile(src, dest)
    utils.unpack_archive(dest)


def Main():
    (options, args) = parse_options()
    assert len(args) == 1
    sha1_file = args[0]
    dest = sha1_file[:-5]
    print('Ensuring %s' % dest)
    with open(sha1_file, 'r') as input_sha:
        sha1 = input_sha.readline()
    if os.path.exists(dest) and utils.get_sha1(dest) == sha1:
        print('sha1 matches, not downloading')
        dest_dir = utils.extract_dir(dest)
        if os.path.exists(dest_dir):
            print('destination directory exists, no extraction')
        else:
            utils.unpack_archive(dest)
        return
    src = os.path.join(GMSCORE_DEPS, sha1)
    if not os.path.exists(src):
        print('File (%s) does not exist on x20' % src)
        print('Maybe pass -Pno_internal to your gradle invocation')
        return 42
    download(src, dest)


if __name__ == '__main__':
    sys.exit(Main())
