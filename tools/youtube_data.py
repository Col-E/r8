# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import glob
import os
import utils

ANDROID_H_MR2_API = '13'
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

V14_19_BASE = os.path.join(BASE, 'youtube.android_14.19')
V14_19_PREFIX = os.path.join(V14_19_BASE, 'YouTubeRelease')

V14_44_BASE = os.path.join(BASE, 'youtube.android_14.44')
V14_44_PREFIX = os.path.join(V14_44_BASE, 'YouTubeRelease')

V15_08_BASE = os.path.join(BASE, 'youtube.android_15.08')
V15_08_PREFIX = os.path.join(V15_08_BASE, 'YouTubeRelease')

V15_09_BASE = os.path.join(BASE, 'youtube.android_15.09')
V15_09_PREFIX = os.path.join(V15_09_BASE, 'YouTubeRelease')

V15_33_BASE = os.path.join(BASE, 'youtube.android_15.33')
V15_33_PREFIX = os.path.join(V15_33_BASE, 'YouTubeRelease')

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
  '14.19': {
    'dex' : {
      'inputs': [os.path.join(V14_19_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V14_19_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      'inputs': ['%s_deploy.jar' % V14_19_PREFIX],
      'pgconf': [
          '%s_proguard.config' % V14_19_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'maindexrules' : [
          os.path.join(V14_19_BASE, 'mainDexClasses.rules'),
          os.path.join(V14_19_BASE, 'main-dex-classes-release-optimized.pgcfg'),
          os.path.join(V14_19_BASE, 'main_dex_YouTubeRelease_proguard.cfg')],
      'min-api' : ANDROID_H_MR2_API,
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V14_19_PREFIX],
      'pgmap': '%s_proguard.map' % V14_19_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '14.44': {
    'dex' : {
      'inputs': [os.path.join(V14_44_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V14_44_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      # When -injars and -libraryjars are used for specifying inputs library
      # sanitization is on by default. For this version of YouTube -injars and
      # -libraryjars are not used, but library sanitization is still required.
      'sanitize_libraries': True,
      'inputs': ['%s_deploy.jar' % V14_44_PREFIX],
      'libraries' : [os.path.join(V14_44_BASE, 'legacy_YouTubeRelease_combined_library_jars.jar')],
      'pgconf': [
          '%s_proguard.config' % V14_44_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'maindexrules' : [
          os.path.join(V14_44_BASE, 'mainDexClasses.rules'),
          os.path.join(V14_44_BASE, 'main-dex-classes-release-optimized.pgcfg'),
          os.path.join(V14_44_BASE, 'main_dex_YouTubeRelease_proguard.cfg')],
      'min-api' : ANDROID_H_MR2_API,
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V14_44_PREFIX],
      'pgmap': '%s_proguard.map' % V14_44_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '15.08': {
    'dex' : {
      'inputs': [os.path.join(V15_08_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V15_08_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      # When -injars and -libraryjars are used for specifying inputs library
      # sanitization is on by default. For this version of YouTube -injars and
      # -libraryjars are not used, but library sanitization is still required.
      'sanitize_libraries': True,
      'inputs': ['%s_deploy.jar' % V15_08_PREFIX],
      'libraries' : [os.path.join(V15_08_BASE, 'legacy_YouTubeRelease_combined_library_jars.jar')],
      'pgconf': [
          '%s_proguard.config' % V15_08_PREFIX,
          '%s_proto_safety.pgcfg' % V15_08_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'maindexrules' : [
          os.path.join(V15_08_BASE, 'mainDexClasses.rules'),
          os.path.join(V15_08_BASE, 'main-dex-classes-release-optimized.pgcfg'),
          os.path.join(V15_08_BASE, 'main_dex_YouTubeRelease_proguard.cfg')],
      'min-api' : ANDROID_H_MR2_API,
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V15_08_PREFIX],
      'pgmap': '%s_proguard.map' % V15_08_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '15.09': {
    'dex' : {
      'inputs': [os.path.join(V15_09_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V15_09_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      # When -injars and -libraryjars are used for specifying inputs library
      # sanitization is on by default. For this version of YouTube -injars and
      # -libraryjars are not used, but library sanitization is still required.
      'sanitize_libraries': True,
      'inputs': ['%s_deploy.jar' % V15_09_PREFIX],
      'libraries' : [os.path.join(V15_09_BASE, 'legacy_YouTubeRelease_combined_library_jars.jar')],
      'pgconf': [
          '%s_proguard.config' % V15_09_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'maindexrules' : [
          os.path.join(V15_09_BASE, 'mainDexClasses.rules'),
          os.path.join(V15_09_BASE, 'main-dex-classes-release-optimized.pgcfg'),
          os.path.join(V15_09_BASE, 'main_dex_YouTubeRelease_proguard.cfg')],
      'min-api' : ANDROID_H_MR2_API,
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V15_09_PREFIX],
      'pgmap': '%s_proguard.map' % V15_09_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '15.33': {
    'dex' : {
      'inputs': [os.path.join(V15_33_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V15_33_PREFIX,
      'libraries' : [ANDROID_JAR],
      'min-api' : ANDROID_L_API,
    },
    'deploy' : {
      # When -injars and -libraryjars are used for specifying inputs library
      # sanitization is on by default. For this version of YouTube -injars and
      # -libraryjars are not used, but library sanitization is still required.
      'sanitize_libraries': True,
      'inputs': ['%s_deploy.jar' % V15_33_PREFIX],
      'libraries' : [os.path.join(V15_33_BASE, 'legacy_YouTubeRelease_combined_library_jars.jar')],
      'pgconf': [
          '%s_proguard.config' % V15_33_PREFIX,
          '%s_proguard_missing_classes.config' % V15_33_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY],
      'maindexrules' : [
          os.path.join(V15_33_BASE, 'mainDexClasses.rules'),
          os.path.join(V15_33_BASE, 'main-dex-classes-release-optimized.pgcfg'),
          os.path.join(V15_33_BASE, 'main_dex_YouTubeRelease_proguard.cfg')],
      'min-api' : ANDROID_H_MR2_API,
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V15_33_PREFIX],
      'pgmap': '%s_proguard.map' % V15_33_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
}
