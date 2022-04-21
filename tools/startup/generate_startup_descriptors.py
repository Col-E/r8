#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import adb_utils

import argparse
import os
import sys
import time

def extend_startup_descriptors(startup_descriptors, iteration, options):
  (logcat, profile, profile_classes_and_methods) = \
      generate_startup_profile(options)
  if options.logcat:
    write_tmp_logcat(logcat, iteration, options)
    current_startup_descriptors = get_r8_startup_descriptors_from_logcat(logcat)
  else:
    write_tmp_profile(profile, iteration, options)
    write_tmp_profile_classes_and_methods(profile_classes_and_methods, iteration, options)
    current_startup_descriptors = \
        transform_classes_and_methods_to_r8_startup_descriptors(
            profile_classes_and_methods, options)
  write_tmp_startup_descriptors(current_startup_descriptors, iteration, options)
  number_of_new_startup_descriptors = add_r8_startup_descriptors(
      startup_descriptors, current_startup_descriptors)
  if options.out is not None:
    print(
        'Found %i new startup descriptors in iteration %i'
            % (number_of_new_startup_descriptors, iteration + 1))
  return number_of_new_startup_descriptors

def generate_startup_profile(options):
  logcat = None
  profile = None
  profile_classes_and_methods = None
  if options.use_existing_profile:
    # Verify presence of profile.
    adb_utils.check_app_has_profile_data(options.app_id, options.device_id)
    profile = adb_utils.get_profile_data(options.app_id, options.device_id)
    profile_classes_and_methods = \
        adb_utils.get_classes_and_methods_from_app_profile(
            options.app_id, options.device_id)
  else:
    # Unlock device.
    tear_down_options = adb_utils.prepare_for_interaction_with_device(
        options.device_id, options.device_pin)

    logcat_process = None
    if options.logcat:
      # Clear logcat and start capturing logcat.
      adb_utils.clear_logcat(options.device_id)
      logcat_process = adb_utils.start_logcat(
          options.device_id, format='raw', filter='r8:I *:S')
    else:
      # Clear existing profile data.
      adb_utils.clear_profile_data(options.app_id, options.device_id)

    # Launch activity to generate startup profile on device.
    adb_utils.launch_activity(
        options.app_id, options.main_activity, options.device_id)

    # Wait for activity startup.
    time.sleep(options.startup_duration)

    if options.logcat:
      # Get startup descriptors from logcat.
      logcat = adb_utils.stop_logcat(logcat_process)
    else:
      # Capture startup profile.
      adb_utils.capture_app_profile_data(options.app_id, options.device_id)
      profile = adb_utils.get_profile_data(options.app_id, options.device_id)
      profile_classes_and_methods = \
          adb_utils.get_classes_and_methods_from_app_profile(
              options.app_id, options.device_id)

    # Shutdown app.
    adb_utils.stop_app(options.app_id, options.device_id)
    adb_utils.tear_down_after_interaction_with_device(
        tear_down_options, options.device_id)

  return (logcat, profile, profile_classes_and_methods)

def get_r8_startup_descriptors_from_logcat(logcat):
  startup_descriptors = []
  for line in logcat:
    if line == '--------- beginning of main':
      continue
    if line == '--------- beginning of system':
      continue
    if not line.startswith('L') or not line.endswith(';'):
      print('Unrecognized line in logcat: %s' % line)
      continue
    startup_descriptors.append(line)
  return startup_descriptors

def transform_classes_and_methods_to_r8_startup_descriptors(
    classes_and_methods, options):
  startup_descriptors = []
  for class_or_method in classes_and_methods:
    descriptor = class_or_method.get('descriptor')
    flags = class_or_method.get('flags')
    if flags.get('post_startup') \
        and not flags.get('startup') \
        and not options.include_post_startup:
      continue
    startup_descriptors.append(descriptor)
  return startup_descriptors

def add_r8_startup_descriptors(startup_descriptors, startup_descriptors_to_add):
  previous_number_of_startup_descriptors = len(startup_descriptors)
  startup_descriptors.update(startup_descriptors_to_add)
  new_number_of_startup_descriptors = len(startup_descriptors)
  return new_number_of_startup_descriptors \
      - previous_number_of_startup_descriptors

def write_tmp_binary_artifact(artifact, iteration, options, name):
  if not options.tmp_dir:
    return
  out_dir = os.path.join(options.tmp_dir, str(iteration))
  os.makedirs(out_dir, exist_ok=True)
  path = os.path.join(out_dir, name)
  with open(path, 'wb') as f:
    f.write(artifact)

def write_tmp_textual_artifact(artifact, iteration, options, name, item_to_string=None):
  if not options.tmp_dir:
    return
  out_dir = os.path.join(options.tmp_dir, str(iteration))
  os.makedirs(out_dir, exist_ok=True)
  path = os.path.join(out_dir, name)
  with open(path, 'w') as f:
    for item in artifact:
      f.write(item if item_to_string is None else item_to_string(item))
      f.write('\n')

def write_tmp_logcat(logcat, iteration, options):
  write_tmp_textual_artifact(logcat, iteration, options, 'logcat.txt')

def write_tmp_profile(profile, iteration, options):
  write_tmp_binary_artifact(profile, iteration, options, 'primary.prof')

def write_tmp_profile_classes_and_methods(
    profile_classes_and_methods, iteration, options):
  def item_to_string(item):
    descriptor = item.get('descriptor')
    flags = item.get('flags')
    return '%s%s%s%s' % (
        'H' if flags.get('hot') else '',
        'S' if flags.get('startup') else '',
        'P' if flags.get('post_startup') else '',
        descriptor)
  write_tmp_textual_artifact(
      profile_classes_and_methods,
      iteration,
      options,
      'profile.txt',
      item_to_string)

def write_tmp_startup_descriptors(startup_descriptors, iteration, options):
  write_tmp_textual_artifact(
      startup_descriptors, iteration, options, 'startup-descriptors.txt')

def parse_options(argv):
  result = argparse.ArgumentParser(
      description='Generate a perfetto trace file.')
  result.add_argument('--apk',
                      help='Path to the APK')
  result.add_argument('--app-id',
                      help='The application ID of interest',
                      required=True)
  result.add_argument('--device-id',
                      help='Device id (e.g., emulator-5554).')
  result.add_argument('--device-pin',
                      help='Device pin code (e.g., 1234)')
  result.add_argument('--logcat',
                      action='store_true',
                      default=False)
  result.add_argument('--include-post-startup',
                      help='Include post startup classes and methods in the R8 '
                           'startup descriptors',
                      action='store_true',
                      default=False)
  result.add_argument('--iterations',
                      help='Number of profiles to generate',
                      default=1,
                      type=int)
  result.add_argument('--main-activity',
                      help='Main activity class name')
  result.add_argument('--out',
                      help='File where to store startup descriptors (defaults '
                           'to stdout)')
  result.add_argument('--startup-duration',
                      help='Duration in seconds before shutting down app',
                      default=15,
                      type=int)
  result.add_argument('--tmp-dir',
                      help='Directory where to store intermediate artifacts'
                            ' (by default these are not emitted)')
  result.add_argument('--until-stable',
                      help='Repeat profile generation until no new startup '
                           'descriptors are found',
                      action='store_true',
                      default=False)
  result.add_argument('--until-stable-iterations',
                      help='Number of times that profile generation must must '
                           'not find new startup descriptors before exiting',
                      default=1,
                      type=int)
  result.add_argument('--use-existing-profile',
                      help='Do not launch app to generate startup profile',
                      action='store_true',
                      default=False)
  options, args = result.parse_known_args(argv)
  assert options.main_activity is not None or options.use_existing_profile, \
      'Argument --main-activity is required except when running with ' \
      '--use-existing-profile.'
  return options, args

def main(argv):
  (options, args) = parse_options(argv)
  adb_utils.root(options.device_id)
  if options.apk:
    adb_utils.uninstall(options.app_id, options.device_id)
    adb_utils.install(options.apk, options.device_id)
  startup_descriptors = set()
  if options.until_stable:
    iteration = 0
    stable_iterations = 0
    while True:
      diff = extend_startup_descriptors(startup_descriptors, iteration, options)
      if diff == 0:
        stable_iterations = stable_iterations + 1
        if stable_iterations == options.until_stable_iterations:
          break
      else:
        stable_iterations = 0
      iteration = iteration + 1
  else:
    for iteration in range(options.iterations):
      extend_startup_descriptors(startup_descriptors, iteration, options)
  if options.out is not None:
    with open(options.out, 'w') as f:
      for startup_descriptor in startup_descriptors:
        f.write(startup_descriptor)
        f.write('\n')
  else:
    for startup_descriptor in startup_descriptors:
      print(startup_descriptor)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
