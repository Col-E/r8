#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import adb_utils

import argparse
import sys
import time

def extend_startup_descriptors(startup_descriptors, iteration, options):
  generate_startup_profile_on_device(options)
  classes_and_methods = adb_utils.get_classes_and_methods_from_app_profile(
      options.app_id, options.device_id)
  current_startup_descriptors = \
      transform_classes_and_methods_to_r8_startup_descriptors(
          classes_and_methods, options)
  number_of_new_startup_descriptors = add_r8_startup_descriptors(
      startup_descriptors, current_startup_descriptors)
  if options.out is not None:
    print(
        'Found %i new startup descriptors in iteration %i'
            % (number_of_new_startup_descriptors, iteration + 1))

def generate_startup_profile_on_device(options):
  if not options.use_existing_profile:
    # Clear existing profile data.
    adb_utils.clear_profile_data(options.app_id, options.device_id)

    # Unlock device.
    tear_down_options = adb_utils.prepare_for_interaction_with_device(
        options.device_id, options.device_pin)

    # Launch activity to generate startup profile on device.
    adb_utils.launch_activity(
        options.app_id, options.main_activity, options.device_id)

    # Wait for activity startup.
    time.sleep(options.startup_duration)

    # Capture startup profile.
    adb_utils.capture_app_profile_data(options.app_id, options.device_id)

    # Shutdown app.
    adb_utils.stop_app(options.app_id, options.device_id)

    adb_utils.tear_down_after_interaction_with_device(
        tear_down_options, options.device_id)

  # Verify presence of profile.
  adb_utils.check_app_has_profile_data(options.app_id, options.device_id)

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

def parse_options(argv):
  result = argparse.ArgumentParser(
      description='Generate a perfetto trace file.')
  result.add_argument('--app-id',
                      help='The application ID of interest',
                      required=True)
  result.add_argument('--device-id',
                      help='Device id (e.g., emulator-5554).')
  result.add_argument('--device-pin',
                      help='Device pin code (e.g., 1234)')
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
  result.add_argument('--until-stable',
                      help='Repeat profile generation until no new startup '
                           'descriptors are found',
                      action='store_true',
                      default=False)
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
  startup_descriptors = set()
  if options.until_stable:
    iteration = 0
    while True:
      diff = extend_startup_descriptors(startup_descriptors, iteration, options)
      if diff == 0:
        break
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
