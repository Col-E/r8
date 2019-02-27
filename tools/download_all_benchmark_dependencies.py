#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Utility script to make it easier to update what golem builds.

import gradle
import sys
import utils

BUILD_TARGETS = ['downloadDeps', 'downloadAndroidCts', 'downloadDx']

def Main():
  gradle.RunGradle(BUILD_TARGETS)
  utils.DownloadFromX20(utils.OPENSOURCE_APPS_SHA_FILE)
  utils.DownloadFromX20(utils.ANDROID_SDK + '.tar.gz.sha1')

if __name__ == '__main__':
  sys.exit(Main())
