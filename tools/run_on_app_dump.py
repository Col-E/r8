#!/usr/bin/env python3
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import hashlib
import os
import shutil
import sys
import time
import zipfile
from datetime import datetime

import adb
import apk_masseur
import as_utils
import compiledump
import gradle
import jdk
import thread_utils
from thread_utils import print_thread
import update_prebuilds_in_android
import utils

# TODO(b/300387869): Cleanup targets
GOLEM_BUILD_TARGETS = [utils.GRADLE_TASK_R8LIB,
                       utils.GRADLE_TASK_RETRACE]
SHRINKERS = ['r8', 'r8-full', 'r8-nolib', 'r8-nolib-full']

class AttrDict(dict):
  def __getattr__(self, name):
    return self.get(name, None)

# To generate the files for a new app, navigate to the app source folder and
# run:
# ./gradlew clean :app:assembleRelease -Dcom.android.tools.r8.dumpinputtodirectory=<path>
# and store the dump and the apk.
# If the app has instrumented tests, adding `testBuildType "release"` and
# running:
# ./gradlew assembleAndroidTest -Dcom.android.tools.r8.dumpinputtodirectory=<path>
# will also generate dumps and apk for tests.

class App(object):
  def __init__(self, fields):
    defaults = {
      'id': None,
      'name': None,
      'collections': [],
      'dump_app': None,
      'apk_app': None,
      'dump_test': None,
      'apk_test': None,
      'skip': False,
      'url': None,  # url is not used but nice to have for updating apps
      'revision': None,
      'folder': None,
      'skip_recompilation': False,
      'compiler_properties': [],
      'internal': False,
      'golem_duration': None,
    }
    # This below does not work in python3
    defaults.update(fields.items())
    self.__dict__ = defaults


class AppCollection(object):
  def __init__(self, fields):
    defaults = {
      'name': None
    }
    # This below does not work in python3
    defaults.update(fields.items())
    self.__dict__ = defaults


APPS = [
  App({
    'id': 'com.numix.calculator',
    'name': 'Calculator',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    # Compiling tests fail: Library class android.content.res.XmlResourceParser
    # implements program class org.xmlpull.v1.XmlPullParser. Nothing to really
    # do about that.
    'id_test': 'com.numix.calculator.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-release-androidTest.apk',
    'url': 'https://github.com/numixproject/android-suite/tree/master/Calculator',
    'revision': 'f58e1b53f7278c9b675d5855842c6d8a44cccb1f',
    'folder': 'android-suite-calculator',
  }),
  App({
    'id': 'dev.dworks.apps.anexplorer.pro',
    'name': 'AnExplorer',
    'dump_app': 'dump_app.zip',
    'apk_app': 'AnExplorer-googleMobileProRelease-4.0.3.apk',
    'url': 'https://github.com/christofferqa/AnExplorer',
    'revision': '365927477b8eab4052a1882d5e358057ae3dee4d',
    'folder': 'anexplorer',
  }),
  App({
    'id': 'de.danoeh.antennapod',
    'name': 'AntennaPod',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-free-release.apk',
    # TODO(b/172452102): Tests and monkey do not work
    'id_test': 'de.danoeh.antennapod.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-free-release-androidTest.apk',
    'url': 'https://github.com/christofferqa/AntennaPod.git',
    'revision': '77e94f4783a16abe9cc5b78dc2d2b2b1867d8c06',
    'folder': 'antennapod',
  }),
  App({
    'id': 'com.example.applymapping',
    'name': 'applymapping',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'id_test': 'com.example.applymapping.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-release-androidTest.apk',
    'url': 'https://github.com/mkj-gram/applymapping',
    'revision': 'e3ae14b8c16fa4718e5dea8f7ad00937701b3c48',
    'folder': 'applymapping',
  }),
  App({
    'id': 'com.chanapps.four.activity',
    'name': 'chanu',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/mkj-gram/chanu.git',
    'revision': '6e53458f167b6d78398da60c20fd0da01a232617',
    'folder': 'chanu',
    # The app depends on a class file that has access flags interface but
    # not abstract
    'compiler_properties': ['-Dcom.android.tools.r8.allowInvalidCfAccessFlags=true']
  }),
  App({
    'id': 'com.example.myapplication',
    'name': 'empty-activity',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/christofferqa/empty_android_activity.git',
    'revision': '2d297ec3373dadb03cbae916b9feba4792563156',
    'folder': 'empty-activity',
  }),
  App({
    'id': 'com.example.emptycomposeactivity',
    'name': 'empty-compose-activity',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/christofferqa/empty_android_compose_activity.git',
    'revision': '3c8111b8b7d6e9184049a07e2b96702d7b33d03e',
    'folder': 'empty-compose-activity',
  }),
  # TODO(b/172539375): Monkey runner fails on recompilation.
  App({
    'id': 'com.google.firebase.example.fireeats',
    'name': 'FriendlyEats',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/firebase/friendlyeats-android',
    'revision': '7c6dd016fc31ea5ecb948d5166b8479efc3775cc',
    'folder': 'friendlyeats',
  }),
  App({
    'id': 'com.google.samples.apps.sunflower',
    'name': 'Sunflower',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-debug.apk',
    # TODO(b/172549283): Compiling tests fails
    'id_test': 'com.google.samples.apps.sunflower.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-debug-androidTest.apk',
    'url': 'https://github.com/android/sunflower',
    'revision': '0c4c88fdad2a74791199dffd1a6559559b1dbd4a',
    'folder': 'sunflower',
  }),
  # TODO(b/172565385): Monkey runner fails on recompilation
  App({
    'id': 'com.google.samples.apps.iosched',
    'name': 'iosched',
    'dump_app': 'dump_app.zip',
    'apk_app': 'mobile-release.apk',
    'url': 'https://github.com/christofferqa/iosched.git',
    'revision': '581cbbe2253711775dbccb753cdb53e7e506cb02',
    'folder': 'iosched',
  }),
  App({
    'id': 'fr.neamar.kiss',
    'name': 'KISS',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    # TODO(b/172569220): Running tests fails due to missing keep rules
    'id_test': 'fr.neamar.kiss.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'app-release-androidTest.apk',
    'url': 'https://github.com/Neamar/KISS',
    'revision': '8ccffaadaf0d0b8fc4418ed2b4281a0935d3d971',
    'folder': 'kiss',
  }),
  # TODO(b/172577344): Monkey runner not working.
  App({
    'id': 'io.github.hidroh.materialistic',
    'name': 'materialistic',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/christofferqa/materialistic.git',
    'revision': '2b2b2ee25ce9e672d5aab1dc90a354af1522b1d9',
    'folder': 'materialistic',
  }),
  App({
    'id': 'com.avjindersinghsekhon.minimaltodo',
    'name': 'MinimalTodo',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/christofferqa/Minimal-Todo',
    'revision': '9d8c73746762cd376b718858ec1e8783ca07ba7c',
    'folder': 'minimal-todo',
  }),
  App({
    'id': 'net.nurik.roman.muzei',
    'name': 'muzei',
    'dump_app': 'dump_app.zip',
    'apk_app': 'muzei-release.apk',
    'url': 'https://github.com/romannurik/muzei',
    'revision': '9eac6e98aebeaf0ae40bdcd85f16dd2886551138',
    'folder': 'muzei',
  }),
  # TODO(b/172806281): Monkey runner does not work.
  App({
    'id': 'org.schabi.newpipe',
    'name': 'NewPipe',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/TeamNewPipe/NewPipe',
    'revision': 'f4435f90313281beece70c544032f784418d85fa',
    'folder': 'newpipe',
  }),
  # TODO(b/172806808): Monkey runner does not work.
  App({
    'id': 'io.rover.app.debug',
    'name': 'Rover',
    'dump_app': 'dump_app.zip',
    'apk_app': 'example-app-release-unsigned.apk',
    'url': 'https://github.com/RoverPlatform/rover-android',
    'revision': '94342117097770ea3ca2c6df6ab496a1a55c3ce7',
    'folder': 'rover-android',
  }),
  # TODO(b/172808159): Monkey runner does not work
  App({
    'id': 'com.google.android.apps.santatracker',
    'name': 'SantaTracker',
    'dump_app': 'dump_app.zip',
    'apk_app': 'santa-tracker-release.apk',
    'url': 'https://github.com/christofferqa/santa-tracker-android',
    'revision': '8dee74be7d9ee33c69465a07088c53087d24a6dd',
    'folder': 'santa-tracker',
  }),
  App({
    'id': 'org.thoughtcrime.securesms',
    'name': 'Signal',
    'dump_app': 'dump_app.zip',
    'apk_app': 'Signal-Android-play-prod-universal-release-4.76.2.apk',
    # TODO(b/172812839): Instrumentation test fails.
    'id_test': 'org.thoughtcrime.securesms.test',
    'dump_test': 'dump_test.zip',
    'apk_test': 'Signal-Android-play-prod-release-androidTest.apk',
    'url': 'https://github.com/signalapp/Signal-Android',
    'revision': '91ca19f294362ccee2c2b43c247eba228e2b30a1',
    'folder': 'signal-android',
  }),
  # TODO(b/172815827): Monkey runner does not work
  App({
    'id': 'com.simplemobiletools.calendar.pro',
    'name': 'Simple-Calendar',
    'dump_app': 'dump_app.zip',
    'apk_app': 'calendar-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-Calendar',
    'revision': '906209874d0a091c7fce5a57972472f272d6b068',
    'folder': 'simple-calendar',
  }),
  # TODO(b/172815534): Monkey runner does not work
  App({
    'id': 'com.simplemobiletools.camera.pro',
    'name': 'Simple-Camera',
    'dump_app': 'dump_app.zip',
    'apk_app': 'camera-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-Camera',
    'revision': 'ebf9820c51e960912b3238287e30a131244fdee6',
    'folder': 'simple-camera',
  }),
  App({
    'id': 'com.simplemobiletools.filemanager.pro',
    'name': 'Simple-File-Manager',
    'dump_app': 'dump_app.zip',
    'apk_app': 'file-manager-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-File-Manager',
    'revision': '2b7fa68ea251222cc40cf6d62ad1de260a6f54d9',
    'folder': 'simple-file-manager',
  }),
  App({
    'id': 'com.simplemobiletools.gallery.pro',
    'name': 'Simple-Gallery',
    'dump_app': 'dump_app.zip',
    'apk_app': 'gallery-326-foss-release.apk',
    'url': 'https://github.com/SimpleMobileTools/Simple-Gallery',
    'revision': '564e56b20d33b28d0018c8087ec705beeb60785e',
    'folder': 'simple-gallery',
  }),
  App({
    'id': 'com.example.sqldelight.hockey',
    'name': 'SQLDelight',
    'dump_app': 'dump_app.zip',
    'apk_app': 'android-release.apk',
    'url': 'https://github.com/christofferqa/sqldelight',
    'revision': '2e67a1126b6df05e4119d1e3a432fde51d76cdc8',
    'folder': 'sqldelight',
  }),
  # TODO(b/172824096): Monkey runner does not work.
  App({
    'id': 'eu.kanade.tachiyomi',
    'name': 'Tachiyomi',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-dev-release.apk',
    'url': 'https://github.com/inorichi/tachiyomi',
    'revision': '8aa6486bf76ab9a61a5494bee284b1a5e9180bf3',
    'folder': 'tachiyomi',
  }),
  # TODO(b/172862042): Monkey runner does not work.
  App({
    'id': 'app.tivi',
    'name': 'Tivi',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release.apk',
    'url': 'https://github.com/chrisbanes/tivi',
    'revision': '5c6d9ed338885c59b1fc64050d92d056417bb4de',
    'folder': 'tivi',
    'golem_duration': 300
  }),
  App({
    'id': 'com.keylesspalace.tusky',
    'name': 'Tusky',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-blue-release.apk',
    'url': 'https://github.com/tuskyapp/Tusky',
    'revision': '814a9b8f9bacf8d26f712b06a0313a3534a2be95',
    'folder': 'tusky',
  }),
  App({
    'id': 'org.wikipedia',
    'name': 'Wikipedia',
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-prod-release.apk',
    'url': 'https://github.com/wikimedia/apps-android-wikipedia',
    'revision': '0fa7cad843c66313be8e25790ef084cf1a1fa67e',
    'folder': 'wikipedia',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'androidx.compose.samples.crane',
    'name': 'compose-crane',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/crane',
    'golem_duration': 240
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.jetcaster',
    'name': 'compose-jetcaster',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetcaster',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.compose.jetchat',
    'name': 'compose-jetchat',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetchat',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.jetnews',
    'name': 'compose-jetnews',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetnews',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.jetsnack',
    'name': 'compose-jetsnack',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetsnack',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.compose.jetsurvey',
    'name': 'compose-jetsurvey',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/jetsurvey',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.owl',
    'name': 'compose-owl',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/owl',
  }),
  # TODO(b/173167253): Check if monkey testing works.
  App({
    'id': 'com.example.compose.rally',
    'name': 'compose-rally',
    'collections': ['compose-samples'],
    'dump_app': 'dump_app.zip',
    'apk_app': 'app-release-unsigned.apk',
    'url': 'https://github.com/android/compose-samples',
    'revision': '779cf9e187b8ee2c6b620b2abb4524719b3f10f8',
    'folder': 'android/compose-samples/rally',
  }),
]


APP_COLLECTIONS = [
  AppCollection({
    'name': 'compose-samples',
  })
]


def remove_print_lines(file):
  with open(file) as f:
    lines = f.readlines()
  with open(file, 'w') as f:
    for line in lines:
      if '-printconfiguration' not in line:
        f.write(line)


def download_sha(app_sha, internal, quiet=False):
  if internal:
    utils.DownloadFromX20(app_sha)
  else:
    utils.DownloadFromGoogleCloudStorage(app_sha, quiet=quiet)


def is_logging_enabled_for(app, options):
  if options.no_logging:
    return False
  if options.app_logging_filter and app.name not in options.app_logging_filter:
    return False
  return True


def is_minified_r8(shrinker):
  return '-nolib' not in shrinker


def is_full_r8(shrinker):
  return '-full' in shrinker


def version_is_built_jar(version):
  return version != 'main' and version != 'source'


def compute_size_of_dex_files_in_package(path):
  dex_size = 0
  z = zipfile.ZipFile(path, 'r')
  for filename in z.namelist():
    if filename.endswith('.dex'):
      dex_size += z.getinfo(filename).file_size
  return dex_size


def dump_for_app(app_dir, app):
  return os.path.join(app_dir, app.dump_app)


def dump_test_for_app(app_dir, app):
  return os.path.join(app_dir, app.dump_test)


def get_r8_jar(options, temp_dir, shrinker):
  if (options.version == 'source'):
    return None
  jar = os.path.abspath(
      os.path.join(
          temp_dir,
          '..',
          'r8lib.jar' if is_minified_r8(shrinker) else 'r8.jar'))
  return jar


def get_results_for_app(app, options, temp_dir, worker_id):
  app_folder = app.folder if app.folder else app.name + "_" + app.revision
  # Golem extraction will extract to the basename under the benchmarks dir.
  app_location = os.path.basename(app_folder) if options.golem else app_folder
  opensource_basedir = (os.path.join('benchmarks', app.name) if options.golem
                        else utils.OPENSOURCE_DUMPS_DIR)
  app_dir = (os.path.join(utils.INTERNAL_DUMPS_DIR, app_location) if app.internal
              else os.path.join(opensource_basedir, app_location))
  if not os.path.exists(app_dir) and not options.golem:
    # Download the app from google storage.
    download_sha(app_dir + ".tar.gz.sha1", app.internal)

  # Ensure that the dumps are in place
  assert os.path.isfile(dump_for_app(app_dir, app)), "Could not find dump " \
                                                     "for app " + app.name

  result = {}
  result['status'] = 'success'
  result_per_shrinker = build_app_with_shrinkers(
    app, options, temp_dir, app_dir, worker_id=worker_id)
  for shrinker, shrinker_result in result_per_shrinker.items():
    result[shrinker] = shrinker_result
  return result


def build_app_with_shrinkers(app, options, temp_dir, app_dir, worker_id):
  result_per_shrinker = {}
  for shrinker in options.shrinker:
    results = []
    build_app_and_run_with_shrinker(
      app, options, temp_dir, app_dir, shrinker, results, worker_id=worker_id)
    result_per_shrinker[shrinker] = results
  if len(options.apps) > 1:
    print_thread('', worker_id)
    log_results_for_app(app, result_per_shrinker, options, worker_id=worker_id)
    print_thread('', worker_id)

  return result_per_shrinker


def is_last_build(index, compilation_steps):
  return index == compilation_steps - 1


def build_app_and_run_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                                    results, worker_id):
  print_thread(
      '[{}] Building {} with {}'.format(
          datetime.now().strftime("%H:%M:%S"),
          app.name,
          shrinker),
      worker_id)
  print_thread(
      'To compile locally: '
          'tools/run_on_app_dump.py --shrinker {} --r8-compilation-steps {} '
          '--app {} --minify {} --optimize {} --shrink {}'.format(
              shrinker,
              options.r8_compilation_steps,
              app.name,
              options.minify,
              options.optimize,
              options.shrink),
      worker_id)
  print_thread(
      'HINT: use --shrinker r8-nolib --no-build if you have a local R8.jar',
      worker_id)
  recomp_jar = None
  status = 'success'
  if options.r8_compilation_steps < 1:
    return
  compilation_steps = 1 if app.skip_recompilation else options.r8_compilation_steps
  for compilation_step in range(0, compilation_steps):
    if status != 'success':
      break
    print_thread(
        'Compiling {} of {}'.format(compilation_step + 1, compilation_steps),
        worker_id)
    result = {}
    try:
      start = time.time()
      (app_jar, mapping, new_recomp_jar) = \
        build_app_with_shrinker(
          app, options, temp_dir, app_dir, shrinker, compilation_step,
          compilation_steps, recomp_jar, worker_id=worker_id)
      end = time.time()
      dex_size = compute_size_of_dex_files_in_package(app_jar)
      result['build_status'] = 'success'
      result['recompilation_status'] = 'success'
      result['output_jar'] = app_jar
      result['output_mapping'] = mapping
      result['dex_size'] = dex_size
      result['duration'] = int((end - start) * 1000)  # Wall time
      if (new_recomp_jar is None
          and not is_last_build(compilation_step, compilation_steps)):
        result['recompilation_status'] = 'failed'
        warn('Failed to build {} with {}'.format(app.name, shrinker))
      recomp_jar = new_recomp_jar
    except Exception as e:
      warn('Failed to build {} with {}'.format(app.name, shrinker))
      if e:
        print_thread('Error: ' + str(e), worker_id)
      result['build_status'] = 'failed'
      status = 'failed'

    original_app_apk = os.path.join(app_dir, app.apk_app)
    app_apk_destination = os.path.join(
      temp_dir,"{}_{}.apk".format(app.id, compilation_step))

    if result.get('build_status') == 'success' and options.monkey:
      # Make a copy of the given APK, move the newly generated dex files into the
      # copied APK, and then sign the APK.
      apk_masseur.masseur(
        original_app_apk, dex=app_jar, resources='META-INF/services/*',
        out=app_apk_destination,
        quiet=options.quiet, logging=is_logging_enabled_for(app, options),
        keystore=options.keystore)

      result['monkey_status'] = 'success' if adb.run_monkey(
        app.id, options.emulator_id, app_apk_destination, options.monkey_events,
        options.quiet, is_logging_enabled_for(app, options)) else 'failed'

    if (result.get('build_status') == 'success'
        and options.run_tests and app.dump_test):
      if not os.path.isfile(app_apk_destination):
        apk_masseur.masseur(
          original_app_apk, dex=app_jar, resources='META-INF/services/*',
          out=app_apk_destination,
          quiet=options.quiet, logging=is_logging_enabled_for(app, options),
          keystore=options.keystore)

      # Compile the tests with the mapping file.
      test_jar = build_test_with_shrinker(
        app, options, temp_dir, app_dir,shrinker, compilation_step,
        result['output_mapping'])
      if not test_jar:
        result['instrumentation_test_status'] = 'compilation_failed'
      else:
        original_test_apk = os.path.join(app_dir, app.apk_test)
        test_apk_destination = os.path.join(
          temp_dir,"{}_{}.test.apk".format(app.id_test, compilation_step))
        apk_masseur.masseur(
          original_test_apk, dex=test_jar, resources='META-INF/services/*',
          out=test_apk_destination,
          quiet=options.quiet, logging=is_logging_enabled_for(app, options),
          keystore=options.keystore)
        result['instrumentation_test_status'] = 'success' if adb.run_instrumented(
          app.id, app.id_test, options.emulator_id, app_apk_destination,
          test_apk_destination, options.quiet,
          is_logging_enabled_for(app, options)) else 'failed'

    results.append(result)
    if result.get('recompilation_status') != 'success':
      break

def get_jdk_home(options, app):
  if options.golem:
    return os.path.join('benchmarks', app.name, 'linux')
  return None

def build_app_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                            compilation_step_index, compilation_steps,
                            prev_recomp_jar, worker_id):
  def config_files_consumer(files):
    for file in files:
      compiledump.clean_config(file, options)
      remove_print_lines(file)
  args = AttrDict({
    'dump': dump_for_app(app_dir, app),
    'r8_jar': get_r8_jar(options, temp_dir, shrinker),
    'r8_flags': options.r8_flags,
    'disable_assertions': options.disable_assertions,
    'version': options.version,
    'compiler': 'r8full' if is_full_r8(shrinker) else 'r8',
    'debug_agent': options.debug_agent,
    'program_jar': prev_recomp_jar,
    'nolib': not is_minified_r8(shrinker),
    'config_files_consumer': config_files_consumer,
    'properties': app.compiler_properties,
    'disable_desugared_lib': False,
    'print_times': options.print_times,
  })

  app_jar = os.path.join(
    temp_dir, '{}_{}_{}_dex_out.jar'.format(
      app.name, shrinker, compilation_step_index))
  app_mapping = os.path.join(
    temp_dir, '{}_{}_{}_dex_out.jar.map'.format(
      app.name, shrinker, compilation_step_index))
  recomp_jar = None
  jdkhome = get_jdk_home(options, app)
  with utils.TempDir() as compile_temp_dir:
    compile_result = compiledump.run1(
        compile_temp_dir, args, [], jdkhome, worker_id=worker_id)
    out_jar = os.path.join(compile_temp_dir, "out.jar")
    out_mapping = os.path.join(compile_temp_dir, "out.jar.map")

    if compile_result != 0 or not os.path.isfile(out_jar):
      assert False, 'Compilation of {} failed'.format(dump_for_app(app_dir, app))
    shutil.move(out_jar, app_jar)
    shutil.move(out_mapping, app_mapping)

    if compilation_step_index < compilation_steps - 1:
      args['classfile'] = True
      args['min_api'] = "10000"
      args['disable_desugared_lib'] = True
      compile_result = compiledump.run1(compile_temp_dir, args, [], jdkhome)
      if compile_result == 0:
        recomp_jar = os.path.join(
          temp_dir, '{}_{}_{}_cf_out.jar'.format(
            app.name, shrinker, compilation_step_index))
        shutil.move(out_jar, recomp_jar)

  return (app_jar, app_mapping, recomp_jar)


def build_test_with_shrinker(app, options, temp_dir, app_dir, shrinker,
                             compilation_step_index, mapping):

  def rewrite_files(files):
    add_applymapping = True
    for file in files:
      compiledump.clean_config(file, options)
      remove_print_lines(file)
      with open(file) as f:
        lines = f.readlines()
      with open(file, 'w') as f:
        for line in lines:
          if '-applymapping' not in line:
            f.write(line + '\n')
        if add_applymapping:
          f.write("-applymapping " + mapping + '\n')
          add_applymapping = False

  args = AttrDict({
    'dump': dump_test_for_app(app_dir, app),
    'r8_jar': get_r8_jar(options, temp_dir, shrinker),
    'disable_assertions': options.disable_assertions,
    'version': options.version,
    'compiler': 'r8full' if is_full_r8(shrinker) else 'r8',
    'debug_agent': options.debug_agent,
    'nolib': not is_minified_r8(shrinker),
    # The config file will have an -applymapping reference to an old map.
    # Update it to point to mapping file build in the compilation of the app.
    'config_files_consumer': rewrite_files,
  })

  test_jar = os.path.join(
    temp_dir, '{}_{}_{}_test_out.jar'.format(
      app.name, shrinker, compilation_step_index))

  with utils.TempDir() as compile_temp_dir:
    jdkhome = get_jdk_home(options, app)
    compile_result = compiledump.run1(compile_temp_dir, args, [], jdkhome)
    out_jar = os.path.join(compile_temp_dir, "out.jar")
    if compile_result != 0 or not os.path.isfile(out_jar):
      return None
    shutil.move(out_jar, test_jar)

  return test_jar


def log_results_for_apps(result_per_shrinker_per_app, options):
  print('')
  app_errors = 0
  for (app, result_per_shrinker) in result_per_shrinker_per_app:
    app_errors += (1 if log_results_for_app(app, result_per_shrinker, options)
                   else 0)
  return app_errors


def log_results_for_app(app, result_per_shrinker, options, worker_id=None):
  if options.print_dexsegments:
    log_segments_for_app(app, result_per_shrinker, options, worker_id=worker_id)
    return False
  else:
    return log_comparison_results_for_app(app, result_per_shrinker, options, worker_id=worker_id)


def log_segments_for_app(app, result_per_shrinker, options, worker_id):
  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    for result in result_per_shrinker.get(shrinker):
      benchmark_name = '{}-{}'.format(options.print_dexsegments, app.name)
      utils.print_dexsegments(
        benchmark_name, [result.get('output_jar')], worker_id=worker_id)
      duration = result.get('duration')
      print_thread(
        '%s-Total(RunTimeRaw): %s ms' % (benchmark_name, duration),
        worker_id)
      print_thread(
        '%s-Total(CodeSize): %s' % (benchmark_name, result.get('dex_size')),
        worker_id)


def percentage_diff_as_string(before, after):
  if after < before:
    return '-' + str(round((1.0 - after / before) * 100)) + '%'
  else:
    return '+' + str(round((after - before) / before * 100)) + '%'


def log_comparison_results_for_app(app, result_per_shrinker, options, worker_id):
  print_thread(app.name + ':', worker_id)
  app_error = False
  if result_per_shrinker.get('status', 'success') != 'success':
    error_message = result_per_shrinker.get('error_message')
    print_thread('  skipped ({})'.format(error_message), worker_id)
    return

  proguard_result = result_per_shrinker.get('pg', {})
  proguard_dex_size = float(proguard_result.get('dex_size', -1))

  for shrinker in SHRINKERS:
    if shrinker not in result_per_shrinker:
      continue
    compilation_index = 1
    for result in result_per_shrinker.get(shrinker):
      build_status = result.get('build_status')
      if build_status != 'success' and build_status is not None:
        app_error = True
        warn('  {}-#{}: {}'.format(shrinker, compilation_index, build_status))
        continue

      if options.golem:
        print_thread(
          '%s(RunTimeRaw): %s ms' % (app.name, result.get('duration')),
          worker_id)
        print_thread(
          '%s(CodeSize): %s' % (app.name, result.get('dex_size')), worker_id)
        continue

      print_thread('  {}-#{}:'.format(shrinker, compilation_index), worker_id)
      dex_size = result.get('dex_size')
      msg = '    dex size: {}'.format(dex_size)
      if options.print_runtimeraw:
        print_thread(
            '    run time raw: {} ms'.format(result.get('duration')), worker_id)
      if dex_size != proguard_dex_size and proguard_dex_size >= 0:
        msg = '{} ({}, {})'.format(
          msg, dex_size - proguard_dex_size,
          percentage_diff_as_string(proguard_dex_size, dex_size))
        success(msg) if dex_size < proguard_dex_size else warn(msg)
      else:
        print_thread(msg, worker_id)

      if options.monkey:
        monkey_status = result.get('monkey_status')
        if monkey_status != 'success':
          app_error = True
          warn('    monkey: {}'.format(monkey_status))
        else:
          success('    monkey: {}'.format(monkey_status))

      if options.run_tests and 'instrumentation_test_status' in result:
        test_status = result.get('instrumentation_test_status')
        if test_status != 'success':
          warn('    instrumentation_tests: {}'.format(test_status))
        else:
          success('    instrumentation_tests: {}'.format(test_status))

      recompilation_status = result.get('recompilation_status', '')
      if recompilation_status == 'failed':
        app_error = True
        warn('    recompilation {}-#{}: failed'.format(shrinker,
                                                       compilation_index))
        continue

      compilation_index += 1

  return app_error


def parse_options(argv):
  result = argparse.ArgumentParser(description = 'Run/compile dump artifacts.')
  result.add_argument('--app',
                      help='What app to run on',
                      choices=[app.name for app in APPS],
                      action='append')
  result.add_argument('--app-collection', '--app_collection',
                      help='What app collection to run',
                      choices=[collection.name for collection in
                               APP_COLLECTIONS],
                      action='append')
  result.add_argument('--app-logging-filter', '--app_logging_filter',
                      help='The apps for which to turn on logging',
                      action='append')
  result.add_argument('--bot',
                      help='Running on bot, use third_party dependency.',
                      default=False,
                      action='store_true')
  result.add_argument('--generate-golem-config', '--generate_golem_config',
                      help='Generate a new config for golem.',
                      default=False,
                      action='store_true')
  result.add_argument('--debug-agent',
                      help='Enable Java debug agent and suspend compilation '
                           '(default disabled)',
                      default=False,
                      action='store_true')
  result.add_argument('--disable-assertions', '--disable_assertions', '-da',
                      help='Disable Java assertions when running the compiler '
                           '(default enabled)',
                      default=False,
                      action='store_true')
  result.add_argument('--emulator-id', '--emulator_id',
                      help='Id of the emulator to use',
                      default='emulator-5554')
  result.add_argument('--golem',
                      help='Running on golem, do not download',
                      default=False,
                      action='store_true')
  result.add_argument('--hash',
                      help='The commit of R8 to use')
  result.add_argument('--internal',
                      help='Run internal apps if set, otherwise run opensource',
                      default=False,
                      action='store_true')
  result.add_argument('--keystore',
                      help='Path to app.keystore',
                      default=os.path.join(utils.TOOLS_DIR, 'debug.keystore'))
  result.add_argument('--keystore-password', '--keystore_password',
                      help='Password for app.keystore',
                      default='android')
  result.add_argument('--minify',
                      help='Force enable/disable minification' +
                           ' (defaults to app proguard config)',
                      choices=['default', 'force-enable', 'force-disable'],
                      default='default')
  result.add_argument('--monkey',
                      help='Whether to install and run app(s) with monkey',
                      default=False,
                      action='store_true')
  result.add_argument('--monkey-events', '--monkey_events',
                      help='Number of events that the monkey should trigger',
                      default=250,
                      type=int)
  result.add_argument('--no-build', '--no_build',
                      help='Run without building first (only when using ToT)',
                      default=False,
                      action='store_true')
  result.add_argument('--no-logging', '--no_logging',
                      help='Disable logging except for errors',
                      default=False,
                      action='store_true')
  result.add_argument('--optimize',
                      help='Force enable/disable optimizations' +
                           ' (defaults to app proguard config)',
                      choices=['default', 'force-enable', 'force-disable'],
                      default='default')
  result.add_argument('--print-times',
                      help='Print timing information from r8',
                      default=False,
                      action='store_true')
  result.add_argument('--print-dexsegments',
                      metavar='BENCHMARKNAME',
                      help='Print the sizes of individual dex segments as ' +
                           '\'<BENCHMARKNAME>-<APP>-<segment>(CodeSize): '
                           '<bytes>\'')
  result.add_argument('--print-runtimeraw',
                      metavar='BENCHMARKNAME',
                      help='Print the line \'<BENCHMARKNAME>(RunTimeRaw):' +
                           ' <elapsed> ms\' at the end where <elapsed> is' +
                           ' the elapsed time in milliseconds.')
  result.add_argument('--quiet',
                      help='Disable verbose logging',
                      default=False,
                      action='store_true')
  result.add_argument('--r8-compilation-steps', '--r8_compilation_steps',
                      help='Number of times R8 should be run on each app',
                      default=2,
                      type=int)
  result.add_argument('--r8-flags', '--r8_flags',
                      help='Additional option(s) for the compiler.')
  result.add_argument('--run-tests', '--run_tests',
                      help='Whether to run instrumentation tests',
                      default=False,
                      action='store_true')
  result.add_argument('--shrink',
                      help='Force enable/disable shrinking' +
                           ' (defaults to app proguard config)',
                      choices=['default', 'force-enable', 'force-disable'],
                      default='default')
  result.add_argument('--sign-apks', '--sign_apks',
                      help='Whether the APKs should be signed',
                      default=False,
                      action='store_true')
  result.add_argument('--shrinker',
                      help='The shrinkers to use (by default, all are run)',
                      action='append')
  result.add_argument('--temp',
                      help='A directory to use for temporaries and outputs.',
                      default=None)
  result.add_argument('--version',
                      default='main',
                      help='The version of R8 to use (e.g., 1.4.51)')
  result.add_argument('--workers',
                      help='Number of workers to use',
                      default=1,
                      type=int)
  (options, args) = result.parse_known_args(argv)

  if options.app or options.app_collection:
    if not options.app:
      options.app = []
    if not options.app_collection:
      options.app_collection = []
    options.apps = [
        app
        for app in APPS
        if app.name in options.app
           or any(collection in options.app_collection
                  for collection in app.collections)]
    del options.app
    del options.app_collection
  else:
    options.apps = [app for app in APPS if app.internal == options.internal]

  if options.app_logging_filter:
    for app_name in options.app_logging_filter:
      assert any(app.name == app_name for app in options.apps)
  if options.shrinker:
    for shrinker in options.shrinker:
      assert shrinker in SHRINKERS, (
          'Shrinker must be one of %s' % ', '.join(SHRINKERS))
  else:
    options.shrinker = [shrinker for shrinker in SHRINKERS]

  if options.hash or version_is_built_jar(options.version):
    # No need to build R8 if a specific version should be used.
    options.no_build = True
    if 'r8-nolib' in options.shrinker:
      warn('Skipping shrinker r8-nolib because a specific version '
           + 'of r8 was specified')
      options.shrinker.remove('r8-nolib')
    if 'r8-nolib-full' in options.shrinker:
      warn('Skipping shrinker r8-nolib-full because a specific version '
           + 'of r8 was specified')
      options.shrinker.remove('r8-nolib-full')
  return (options, args)


def print_indented(s, indent):
  print(' ' * indent + s)


def get_sha256(gz_file):
  with open(gz_file, 'rb') as f:
    bytes = f.read() # read entire file as bytes
    return hashlib.sha256(bytes).hexdigest();


def get_sha_from_file(sha_file):
  with open(sha_file, 'r') as f:
    return f.readlines()[0]


def print_golem_config(options):
  print('// AUTOGENERATED FILE from tools/run_on_app_dump.py in R8 repo')
  print('part of r8_config;')
  print('')
  print('final Suite dumpsSuite = Suite("OpenSourceAppDumps");')
  print('')
  print('createOpenSourceAppBenchmarks() {')
  print_indented('final cpus = ["Lenovo M90"];', 2)
  print_indented('final targetsCompat = ["R8"];', 2)
  print_indented('final targetsFull = ["R8-full-minify-optimize-shrink"];', 2)
  # Avoid calculating this for every app
  jdk_gz = jdk.GetJdkHome() + '.tar.gz'
  add_golem_resource(2, jdk_gz, 'openjdk')
  for app in options.apps:
    if app.folder and not app.internal:
      indentation = 2;
      print_indented('{', indentation)
      indentation = 4
      print_indented('final name = "%s";' % app.name, indentation)
      print_indented('final benchmark =', indentation)
      print_indented(
          'StandardBenchmark(name, [Metric.RunTimeRaw, Metric.CodeSize]);',
          indentation + 4)
      if app.golem_duration != None:
        print_indented(
            'final timeout = const Duration(seconds: %s);' % app.golem_duration,
            indentation)
        print_indented(
            'ExecutionManagement.addTimeoutConstraint'
            '(timeout, benchmark: benchmark);', indentation)
      app_gz = os.path.join(utils.OPENSOURCE_DUMPS_DIR, app.folder + '.tar.gz')
      name = 'appResource'
      add_golem_resource(indentation, app_gz, name)
      print_golem_config_target('Compat', 'r8', app, indentation)
      print_golem_config_target(
        'Full',
        'r8-full',
        app,
        indentation,
        minify='force-enable',
        optimize='force-enable',
        shrink='force-enable')
      print_indented('dumpsSuite.addBenchmark(name);', indentation)
      indentation = 2
      print_indented('}', indentation)
  print('}')

def print_golem_config_target(
    target, shrinker, app, indentation,
    minify='default', optimize='default', shrink='default'):
  options="options" + target
  print_indented(
      'final %s = benchmark.addTargets(noImplementation, targets%s);'
        % (options, target),
      indentation)
  print_indented('%s.cpus = cpus;' % options, indentation)
  print_indented('%s.isScript = true;' % options, indentation)
  print_indented('%s.fromRevision = 9700;' % options, indentation);
  print_indented('%s.mainFile = "tools/run_on_app_dump.py "' % options,
                 indentation)
  print_indented('"--golem --disable-assertions --quiet --shrinker %s --app %s "'
                   % (shrinker, app.name),
                 indentation + 4)
  print_indented('"--minify %s --optimize %s --shrink %s";'
                   % (minify, optimize, shrink),
                 indentation + 4)
  print_indented('%s.resources.add(appResource);' % options, indentation)
  print_indented('%s.resources.add(openjdk);' % options, indentation)

def add_golem_resource(indentation, gz, name, sha256=None):
  sha = gz + '.sha1'
  if not sha256:
    # Golem uses a sha256 of the file in the cache, and you need to specify that.
    download_sha(sha, False, quiet=True)
    sha256 = get_sha256(gz)
  sha = get_sha_from_file(sha)
  print_indented('final %s = BenchmarkResource("",' % name, indentation)
  print_indented('type: BenchmarkResourceType.storage,', indentation + 4)
  print_indented('uri: "gs://r8-deps/%s",' % sha, indentation + 4)
  # Make dart formatter happy.
  if indentation > 2:
    print_indented('hash:', indentation + 4)
    print_indented('"%s",' % sha256, indentation + 8)
  else:
    print_indented('hash: "%s",' % sha256, indentation + 4)
  print_indented('extract: "gz");', indentation + 4);

def main(argv):
  (options, args) = parse_options(argv)

  if options.bot:
    options.no_logging = True
    options.shrinker = ['r8', 'r8-full']
    print(options.shrinker)

  if options.golem:
    options.disable_assertions = True
    options.no_build = True
    options.r8_compilation_steps = 1
    options.quiet = True
    options.no_logging = True

  if options.generate_golem_config:
    print_golem_config(options)
    return 0

  with utils.TempDir() as temp_dir:
    if options.temp:
      temp_dir = options.temp
      os.makedirs(temp_dir, exist_ok=True)
    if options.hash:
      # Download r8-<hash>.jar from
      # https://storage.googleapis.com/r8-releases/raw/.
      target = 'r8-{}.jar'.format(options.hash)
      update_prebuilds_in_android.download_hash(
        temp_dir, 'com/android/tools/r8/' + options.hash, target)
      as_utils.MoveFile(
        os.path.join(temp_dir, target), os.path.join(temp_dir, 'r8lib.jar'),
        quiet=options.quiet)
    elif version_is_built_jar(options.version):
        # Download r8-<version>.jar from
        # https://storage.googleapis.com/r8-releases/raw/.
        target = 'r8-{}.jar'.format(options.version)
        update_prebuilds_in_android.download_version(
          temp_dir, 'com/android/tools/r8/' + options.version, target)
        as_utils.MoveFile(
          os.path.join(temp_dir, target), os.path.join(temp_dir, 'r8lib.jar'),
          quiet=options.quiet)
    elif options.version == 'main':
      if not options.no_build:
        gradle.RunGradle(['R8Retrace', 'r8', '-Pno_internal'])
        build_r8lib = False
        for shrinker in options.shrinker:
          if is_minified_r8(shrinker):
            build_r8lib = True
        if build_r8lib:
          gradle.RunGradle(['r8lib', '-Pno_internal'])
      # Make a copy of r8.jar and r8lib.jar such that they stay the same for
      # the entire execution of this script.
      if 'r8-nolib' in options.shrinker or 'r8-nolib-full' in options.shrinker:
        assert os.path.isfile(utils.R8_JAR), 'Cannot build without r8.jar'
        shutil.copyfile(utils.R8_JAR, os.path.join(temp_dir, 'r8.jar'))
      if 'r8' in options.shrinker or 'r8-full' in options.shrinker:
        assert os.path.isfile(utils.R8LIB_JAR), 'Cannot build without r8lib.jar'
        shutil.copyfile(utils.R8LIB_JAR, os.path.join(temp_dir, 'r8lib.jar'))

    jobs = []
    result_per_shrinker_per_app = []
    for app in options.apps:
      if app.skip:
        continue
      result = {}
      result_per_shrinker_per_app.append((app, result))
      jobs.append(create_job(app, options, result, temp_dir))
    thread_utils.run_in_parallel(
        jobs,
        number_of_workers=options.workers,
        stop_on_first_failure=False)
    errors = log_results_for_apps(result_per_shrinker_per_app, options)
    if errors > 0:
      dest = 'gs://r8-test-results/r8-libs/' + str(int(time.time()))
      utils.upload_file_to_cloud_storage(os.path.join(temp_dir, 'r8lib.jar'), dest)
      print('R8lib saved to %s' % dest)
    return errors

def create_job(app, options, result, temp_dir):
  return lambda worker_id: run_job(
      app, options, result, temp_dir, worker_id)

def run_job(app, options, result, temp_dir, worker_id):
  job_temp_dir = os.path.join(temp_dir, str(worker_id or 0))
  os.makedirs(job_temp_dir, exist_ok=True)
  result.update(get_results_for_app(app, options, job_temp_dir, worker_id))
  return 0

def success(message):
  CGREEN = '\033[32m'
  CEND = '\033[0m'
  print(CGREEN + message + CEND)


def warn(message):
  CRED = '\033[91m'
  CEND = '\033[0m'
  print(CRED + message + CEND)


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
