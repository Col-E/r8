# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import glob
import os
import utils

ANDROID_L_API = '21'
BASE = os.path.join(utils.THIRD_PARTY, 'gmail')

V170604_16_BASE = os.path.join(BASE, 'gmail_android_170604.16')
V170604_16_PREFIX = os.path.join(V170604_16_BASE, 'Gmail_release_unstripped')

V180826_15_BASE = os.path.join(BASE, 'gmail_android_180826.15')
V180826_15_PREFIX = os.path.join(V180826_15_BASE, 'Gmail_release_unstripped')

# NOTE: We always use android.jar for SDK v25 for now.
ANDROID_JAR = utils.get_android_jar(25)

VERSIONS = {
  '170604.16': {
    'dex' : {
      'inputs': [os.path.join(V170604_16_BASE, 'Gmail_release_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V170604_16_PREFIX,
      'libraries' : [ANDROID_JAR],
    },
    'deploy' : {
      'inputs': ['%s_deploy.jar' % V170604_16_PREFIX],
      'pgconf': ['%s_proguard.config' % V170604_16_PREFIX],
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V170604_16_PREFIX],
      'pgmap': '%s_proguard.map' % V170604_16_PREFIX,
    }
  },
  '180826.15': {
    'dex' : {
      'flags': '--no-desugaring',
      'inputs': [os.path.join(V180826_15_BASE, 'Gmail_release_unsigned.apk')],
      'main-dex-list': os.path.join(V180826_15_BASE, 'main_dex_list.txt') ,
      'pgmap': '%s_proguard.map' % V180826_15_PREFIX,
      'libraries' : [ANDROID_JAR],
    },
    'deploy' : {
      'flags': '--no-desugaring',
      'inputs': ['%s_deploy.jar' % V180826_15_PREFIX],
      'pgconf': [
          '%s_proguard.config' % V180826_15_PREFIX,
          '%s/proguardsettings/Gmail_proguard.config' % utils.THIRD_PARTY],
      'min-api' : ANDROID_L_API,
      'allow-type-errors' : 1,
    },
    'proguarded' : {
      'flags': '--no-desugaring',
      'inputs': ['%s_proguard.jar' % V180826_15_PREFIX],
      'main-dex-list': os.path.join(V180826_15_BASE, 'main_dex_list.txt') ,
      'pgmap': '%s_proguard.map' % V180826_15_PREFIX,
    }
  },
}
