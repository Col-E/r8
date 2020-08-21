#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Utility script to make it easier to update what golem builds.

import gradle
import sys
import utils
import os
import retrace_benchmark

BUILD_TARGETS = ['downloadDeps', 'downloadAndroidCts', 'downloadDx']

def Main():
  gradle.RunGradle(BUILD_TARGETS)
  utils.DownloadFromX20(
      os.path.join(utils.THIRD_PARTY, 'gradle-plugin') + '.tar.gz.sha1')
  utils.DownloadFromX20(
      os.path.join(
          utils.THIRD_PARTY, 'benchmarks', 'android-sdk') + '.tar.gz.sha1')
  utils.DownloadFromX20(
      os.path.join(utils.THIRD_PARTY, 'remapper') + '.tar.gz.sha1')
  utils.DownloadFromGoogleCloudStorage(utils.SAMPLE_LIBRARIES_SHA_FILE)
  utils.DownloadFromGoogleCloudStorage(utils.OPENSOURCE_APPS_SHA_FILE)
  utils.DownloadFromGoogleCloudStorage(utils.ANDROID_SDK + '.tar.gz.sha1',
                                       bucket='r8-deps-internal',
                                       auth=True)
  retrace_benchmark.download_benchmarks()

if __name__ == '__main__':
  sys.exit(Main())
