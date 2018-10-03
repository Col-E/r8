# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import glob
import os
import utils

ANDROID_L_API = '21'
BASE = os.path.join(utils.THIRD_PARTY, 'youtube')

V12_10_BASE = os.path.join(BASE, 'youtube.android_12.10')
V12_10_PREFIX = os.path.join(V12_10_BASE, 'YouTubeRelease')

V12_17_BASE = os.path.join(BASE, 'youtube.android_12.17')
V12_17_PREFIX = os.path.join(V12_17_BASE, 'YouTubeRelease')

V12_22_BASE = os.path.join(BASE, 'youtube.android_12.22')
V12_22_PREFIX = os.path.join(V12_22_BASE, 'YouTubeRelease')

V13_37_BASE = os.path.join(BASE, 'youtube.android_13.37')
V13_37_PREFIX = os.path.join(V13_37_BASE, 'YouTubeRelease')

# NOTE: we always use android.jar for SDK v25, later we might want to revise it
#       to use proper android.jar version for each of youtube version separately.
ANDROID_JAR = utils.get_android_jar(25)

VERSIONS = {
  '12.10': {
    'dex' : {
      'inputs': [os.path.join(V12_10_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V12_10_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      'inputs': ['%s_deploy.jar' % V12_10_PREFIX],
      'pgconf': ['%s_proguard.config' % V12_10_PREFIX,
                 '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'min-api' : ANDROID_L_API,
    }
    # The 'proguarded' version cannot be handled by D8/R8 because there are
    # parameter annotations for parameters that do not exist, which is not
    # handled gracefully by ASM (see b/116089492).
    #'proguarded' : {
    #  'inputs': ['%s_proguard.jar' % V12_10_PREFIX],
    #  'pgmap': '%s_proguard.map' % V12_10_PREFIX,
    #  'min-api' : ANDROID_L_API,
    #}
  },
  '12.17': {
    'dex' : {
      'inputs': [os.path.join(V12_17_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V12_17_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      'inputs': ['%s_deploy.jar' % V12_17_PREFIX],
      'pgconf': ['%s_proguard.config' % V12_17_PREFIX,
                 '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'min-api' : ANDROID_L_API,
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V12_17_PREFIX],
      'pgmap': '%s_proguard.map' % V12_17_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '12.22': {
    'dex' : {
      'inputs': [os.path.join(V12_22_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V12_22_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      'inputs': ['%s_deploy.jar' % V12_22_PREFIX],
      'pgconf': [
          '%s_proguard.config' % V12_22_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'maindexrules' : [
          os.path.join(V12_22_BASE, 'mainDexClasses.rules'),
          os.path.join(V12_22_BASE, 'main-dex-classes-release.cfg'),
          os.path.join(V12_22_BASE, 'main_dex_YouTubeRelease_proguard.cfg')],
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V12_22_PREFIX],
      'pgmap': '%s_proguard.map' % V12_22_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '13.37': {
    'dex' : {
      'inputs': [os.path.join(V13_37_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V13_37_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      'inputs': ['%s_deploy.jar' % V13_37_PREFIX],
      'pgconf': [
          '%s_proguard.config' % V13_37_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      # Build for native multi dex, as Currently R8 cannot meet the main-dex
      # constraints.
      #'maindexrules' : [
      #    os.path.join(V13_37_BASE, 'mainDexClasses.rules'),
      #    os.path.join(V13_37_BASE, 'main-dex-classes-release-optimized.cfg'),
      #    os.path.join(V13_37_BASE, 'main_dex_YouTubeRelease_proguard.cfg')],
      'min-api' : ANDROID_L_API,
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V13_37_PREFIX],
      'pgmap': '%s_proguard.map' % V13_37_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
}
