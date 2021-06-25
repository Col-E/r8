# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import utils

ANDROID_H_MR2_API = '13'
ANDROID_L_API = '21'
BASE = os.path.join(utils.THIRD_PARTY, 'youtube')

V15_33_BASE = os.path.join(BASE, 'youtube.android_15.33')
V15_33_PREFIX = os.path.join(V15_33_BASE, 'YouTubeRelease')

V16_12_BASE = os.path.join(BASE, 'youtube.android_16.12')
V16_12_PREFIX = os.path.join(V16_12_BASE, 'YouTubeRelease')

V16_20_BASE = os.path.join(BASE, 'youtube.android_16.20')
V16_20_PREFIX = os.path.join(V16_20_BASE, 'YouTubeRelease')

LATEST_VERSION = '16.20'

VERSIONS = {
  '15.33': {
    'dex' : {
      'inputs': [os.path.join(V15_33_BASE, 'YouTubeRelease_unsigned.apk')],
      'pgmap': '%s_proguard.map' % V15_33_PREFIX,
      'libraries' : [utils.get_android_jar(25)],
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
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY,
          utils.IGNORE_WARNINGS_RULES],
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
  '16.12': {
    'deploy' : {
      'sanitize_libraries': False,
      'inputs': ['%s_deploy.jar' % V16_12_PREFIX],
      'libraries' : [
          os.path.join(
              V16_12_BASE,
              'legacy_YouTubeRelease_combined_library_jars_filtered.jar')],
      'pgconf': [
          '%s_proguard.config' % V16_12_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY,
          utils.IGNORE_WARNINGS_RULES],
      'min-api' : ANDROID_L_API,
      'android_java8_libs': {
        'config': '%s/desugar_jdk_libs/full_desugar_jdk_libs.json' % V16_12_BASE,
        'program': [
            '%s/desugar_jdk_libs/jdk_libs_to_desugar.jar' % V16_12_BASE,
            '%s/desugar_jdk_libs/desugar_jdk_libs_configuration.jar' % V16_12_BASE],
        'library': '%s/android_jar/lib-v30/android.jar' % utils.THIRD_PARTY,
        'pgconf': [
          '%s/desugar_jdk_libs/base.pgcfg' % V16_12_BASE,
          '%s/desugar_jdk_libs/minify_desugar_jdk_libs.pgcfg' % V16_12_BASE
        ]
      }
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V16_12_PREFIX],
      'pgmap': '%s_proguard.map' % V16_12_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
  '16.20': {
    'deploy' : {
      'sanitize_libraries': False,
      'inputs': ['%s_deploy.jar' % V16_20_PREFIX],
      'libraries' : [
          os.path.join(
              V16_20_BASE,
              'legacy_YouTubeRelease_combined_library_jars_filtered.jar')],
      'pgconf': [
          '%s_proguard.config' % V16_20_PREFIX,
          '%s/proguardsettings/YouTubeRelease_proguard.config' % utils.THIRD_PARTY,
          utils.IGNORE_WARNINGS_RULES],
      'min-api' : ANDROID_L_API,
      'android_java8_libs': {
        'config': '%s/desugar_jdk_libs/full_desugar_jdk_libs.json' % V16_20_BASE,
        # Intentionally not adding desugar_jdk_libs_configuration.jar since it
        # is part of jdk_libs_to_desugar.jar in YouTube 16.20.
        'program': ['%s/desugar_jdk_libs/jdk_libs_to_desugar.jar' % V16_20_BASE],
        'library': '%s/android_jar/lib-v30/android.jar' % utils.THIRD_PARTY,
        'pgconf': [
          '%s/desugar_jdk_libs/base.pgcfg' % V16_20_BASE,
          '%s/desugar_jdk_libs/minify_desugar_jdk_libs.pgcfg' % V16_20_BASE
        ]
      }
    },
    'proguarded' : {
      'inputs': ['%s_proguard.jar' % V16_20_PREFIX],
      'pgmap': '%s_proguard.map' % V16_20_PREFIX,
      'min-api' : ANDROID_L_API,
    }
  },
}

def GetLatestVersion():
  return LATEST_VERSION

def GetName():
  return 'youtube'

def GetMemoryData(version):
  assert version == '16.20'
  return {
      'find-xmx-min': 2800,
      'find-xmx-max': 3200,
      'find-xmx-range': 64,
      'oom-threshold': 3000,
      # TODO(b/143431825): Youtube can OOM randomly in memory configurations
      #  that should work.
      'skip-find-xmx-max': True,
  }
