# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import glob
import os
import utils

ANDROID_L_API = '21'
BASE = os.path.join(utils.THIRD_PARTY, 'gmscore')

V10_BASE = os.path.join(BASE, 'gmscore_v10')
V10_PREFIX = os.path.join(V10_BASE, 'GmsCore_prod_alldpi_release_all_locales')

LATEST_BASE = os.path.join(BASE, 'latest')
LATEST_PREFIX = os.path.join(LATEST_BASE, 'GmsCore_prod_alldpi_release_all_locales')

LATEST_VERSION = 'latest'

VERSIONS = {
  'v10': {
    'dex' : {
      'flags': '--no-desugaring',
      'inputs': [os.path.join(V10_BASE, 'armv7_GmsCore_prod_alldpi_release.apk')],
      'main-dex-list': os.path.join(V10_BASE, 'main_dex_list.txt') ,
      'pgmap': '%s_proguard.map' % V10_PREFIX,
    },
    'deploy' : {
      'inputs': ['%s_deploy.jar' % V10_PREFIX],
      'pgconf': ['%s_proguard.config' % V10_PREFIX,
                 utils.IGNORE_WARNINGS_RULES],
      'min-api' : ANDROID_L_API,
    },
    'proguarded' : {
      'flags': '--no-desugaring',
      'inputs': ['%s_proguard.jar' % V10_PREFIX],
      'main-dex-list': os.path.join(V10_BASE, 'main_dex_list.txt') ,
      'pgmap': '%s_proguard.map' % V10_PREFIX,
    }
  },
  'latest': {
    'deploy' : {
      'inputs': ['%s_deploy.jar' % LATEST_PREFIX],
      'pgconf': [
          '%s_proguard.config' % LATEST_PREFIX,
          '%s/proguardsettings/GmsCore_proguard.config' % utils.THIRD_PARTY,
          utils.IGNORE_WARNINGS_RULES],
      'min-api' : ANDROID_L_API,
    },
    'proguarded' : {
      'flags': '--no-desugaring',
      'inputs': ['%s_proguard.jar' % LATEST_PREFIX],
      'main-dex-list': os.path.join(LATEST_BASE, 'main_dex_list.txt') ,
      'pgmap': '%s_proguard.map' % LATEST_PREFIX,
    }
  },
}
