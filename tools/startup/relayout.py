#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import apk_masseur
import apk_utils
import extractmarker
import toolhelper
import utils
import zip_utils

LOWEST_SUPPORTED_MIN_API = 21 # Android L (native multi dex)

def parse_options(argv):
  result = argparse.ArgumentParser(
      description='Relayout a given APK using a startup profile.')
  result.add_argument('--apk',
                      help='Path to the .apk',
                      required=True)
  result.add_argument('--desugared-library',
                      choices=['auto', 'true', 'false'],
                      default='auto',
                      help='Whether the last dex file of the app is desugared '
                           'library')
  result.add_argument('--no-build',
                      action='store_true',
                      default=False,
                      help='To disable building using gradle')
  result.add_argument('--out',
                      help='Destination of resulting apk',
                      required=True)
  result.add_argument('--profile',
                      help='Path to the startup profile')
  options, args = result.parse_known_args(argv)
  return options, args

def get_dex_to_relayout(options, temp):
  marker = extractmarker.extractmarker(options.apk, build=not options.no_build)
  if '~~L8' not in marker:
    return [options.apk], None
  dex_dir = os.path.join(temp, 'dex')
  dex_predicate = \
      lambda name : name.startswith('classes') and name.endswith('.dex')
  extracted_dex_files = \
      zip_utils.extract_all_that_matches(options.apk, dex_dir, dex_predicate)
  desugared_library_dex = 'classes%s.dex' % len(extracted_dex_files)
  assert desugared_library_dex in extracted_dex_files
  return [
      os.path.join(dex_dir, name) \
          for name in extracted_dex_files if name != desugared_library_dex], \
      os.path.join(dex_dir, desugared_library_dex)

def has_desugared_library_dex(options):
  if options.desugared_library == 'auto':
    marker = extractmarker.extractmarker(
        options.apk, build=not options.no_build)
    return '~~L8' in marker
  return options.desugared_library == 'true'

def main(argv):
  (options, args) = parse_options(argv)
  with utils.TempDir() as temp:
    dex = os.path.join(temp, 'dex.zip')
    d8_args = [
        '--min-api',
        str(max(apk_utils.get_min_api(options.apk), LOWEST_SUPPORTED_MIN_API)),
        '--output', dex,
        '--no-desugaring',
        '--release']
    if options.profile:
      d8_args.extend(['--startup-profile', options.profile])
    dex_to_relayout, desugared_library_dex = get_dex_to_relayout(options, temp)
    d8_args.extend(dex_to_relayout)
    toolhelper.run(
        'd8',
        d8_args,
        build=not options.no_build,
        main='com.android.tools.r8.D8')
    if desugared_library_dex is not None:
      dex_files = [name for name in \
          zip_utils.get_names_that_matches(dex, lambda x : True)]
      zip_utils.add_file_to_zip(
          desugared_library_dex, 'classes%s.dex' % str(len(dex_files) + 1), dex)
    apk_masseur.masseur(options.apk, dex=dex, out=options.out)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
