#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import adb_utils
import profile_utils

import argparse
import os
import sys
import time

class Device:

  def __init__(self, device_id, device_pin):
    self.device_id = device_id
    self.device_pin = device_pin

def extend_startup_descriptors(startup_descriptors, iteration, device, options):
  (logcat, profile, profile_classes_and_methods) = \
      generate_startup_profile(device, options)
  if options.logcat:
    write_tmp_logcat(logcat, iteration, options)
    current_startup_descriptors = get_r8_startup_descriptors_from_logcat(
        logcat, options)
  else:
    write_tmp_profile(profile, iteration, options)
    write_tmp_profile_classes_and_methods(
        profile_classes_and_methods, iteration, options)
    current_startup_descriptors = \
        profile_utils.transform_art_profile_to_r8_startup_list(
            profile_classes_and_methods, options.generalize_synthetics)
  write_tmp_startup_descriptors(current_startup_descriptors, iteration, options)
  new_startup_descriptors = add_r8_startup_descriptors(
      startup_descriptors, current_startup_descriptors)
  number_of_new_startup_descriptors = \
      len(new_startup_descriptors) - len(startup_descriptors)
  if options.out is not None:
    print(
        'Found %i new startup descriptors in iteration %i'
            % (number_of_new_startup_descriptors, iteration + 1))
  return new_startup_descriptors

def generate_startup_profile(device, options):
  logcat = None
  profile = None
  profile_classes_and_methods = None
  if options.use_existing_profile:
    # Verify presence of profile.
    adb_utils.check_app_has_profile_data(options.app_id, device.device_id)
    profile = adb_utils.get_profile_data(options.app_id, device.device_id)
    profile_classes_and_methods = \
        adb_utils.get_classes_and_methods_from_app_profile(
            options.app_id, device.device_id)
  else:
    # Unlock device.
    tear_down_options = adb_utils.prepare_for_interaction_with_device(
        device.device_id, device.device_pin)

    logcat_process = None
    if options.logcat:
      # Clear logcat and start capturing logcat.
      adb_utils.clear_logcat(device.device_id)
      logcat_process = adb_utils.start_logcat(
          device.device_id, format='tag', filter='R8:I ActivityTaskManager:I *:S')
    else:
      # Clear existing profile data.
      adb_utils.clear_profile_data(options.app_id, device.device_id)

    # Launch activity to generate startup profile on device.
    adb_utils.launch_activity(
        options.app_id, options.main_activity, device.device_id)

    # Wait for activity startup.
    time.sleep(options.startup_duration)

    if options.logcat:
      # Get startup descriptors from logcat.
      logcat = adb_utils.stop_logcat(logcat_process)
    else:
      # Capture startup profile.
      adb_utils.capture_app_profile_data(options.app_id, device.device_id)
      profile = adb_utils.get_profile_data(options.app_id, device.device_id)
      profile_classes_and_methods = \
          adb_utils.get_classes_and_methods_from_app_profile(
              options.app_id, device.device_id)

    # Shutdown app.
    adb_utils.stop_app(options.app_id, device.device_id)
    adb_utils.teardown_after_interaction_with_device(
        tear_down_options, device.device_id)

  return (logcat, profile, profile_classes_and_methods)

def get_r8_startup_descriptors_from_logcat(logcat, options):
  post_startup = False
  startup_descriptors = {}
  for line in logcat:
    line_elements = parse_logcat_line(line)
    if line_elements is None:
      continue
    (priority, tag, message) = line_elements
    if tag == 'ActivityTaskManager':
      if message.startswith('START') \
          or message.startswith('Activity pause timeout for') \
          or message.startswith('Activity top resumed state loss timeout for') \
          or message.startswith('Force removing') \
          or message.startswith(
              'Launch timeout has expired, giving up wake lock!'):
        continue
      elif message.startswith('Displayed %s/' % options.app_id):
        print('Entering post startup: %s' % message)
        post_startup = True
        continue
    elif tag == 'R8':
      if is_startup_descriptor(message):
        startup_descriptors[message] = {
          'conditional_startup': False,
          'hot': False,
          'post_startup': post_startup,
          'startup': True
        }
        continue
    # Reaching here means we didn't expect this line.
    report_unrecognized_logcat_line(line)
  return startup_descriptors

def is_startup_descriptor(string):
  # The descriptor should start with the holder (possibly prefixed with 'S').
  if not any(string.startswith('%sL' % flags) for flags in ['', 'S']):
    return False
  # The descriptor should end with ';', a primitive type, or void.
  if not string.endswith(';') \
      and not any(string.endswith(c) for c in get_primitive_descriptors()) \
      and not string.endswith('V'):
    return False
  return True

def get_primitive_descriptors():
  return ['Z', 'B', 'S', 'C', 'I', 'F', 'J', 'D']

def parse_logcat_line(line):
  if line == '--------- beginning of kernel':
    return None
  if line == '--------- beginning of main':
    return None
  if line == '--------- beginning of system':
    return None

  priority = None
  tag = None

  try:
    priority_end = line.index('/')
    priority = line[0:priority_end]
    line = line[priority_end + 1:]
  except ValueError:
    return report_unrecognized_logcat_line(line)

  try:
    tag_end = line.index(':')
    tag = line[0:tag_end].strip()
    line = line[tag_end + 1 :]
  except ValueError:
    return report_unrecognized_logcat_line(line)

  message = line.strip()
  return (priority, tag, message)

def report_unrecognized_logcat_line(line):
  print('Unrecognized line in logcat: %s' % line)

def add_r8_startup_descriptors(old_startup_descriptors, startup_descriptors_to_add):
  new_startup_descriptors = {}
  if len(old_startup_descriptors) == 0:
    for startup_descriptor, flags in startup_descriptors_to_add.items():
      new_startup_descriptors[startup_descriptor] = flags.copy()
  else:
    # Merge the new startup descriptors with the old descriptors in a way so
    # that new startup descriptors are added next to the startup descriptors
    # they are close to in the newly generated list of startup descriptors.
    startup_descriptors_to_add_after_key = {}
    startup_descriptors_to_add_in_the_end = {}
    closest_seen_startup_descriptor = None
    for startup_descriptor, flags in startup_descriptors_to_add.items():
      if startup_descriptor in old_startup_descriptors:
        closest_seen_startup_descriptor = startup_descriptor
      else:
        if closest_seen_startup_descriptor is None:
          # Insert this new startup descriptor in the end of the result.
          startup_descriptors_to_add_in_the_end[startup_descriptor] = flags
        else:
          # Record that this should be inserted after
          # closest_seen_startup_descriptor.
          pending_startup_descriptors = \
              startup_descriptors_to_add_after_key.setdefault(
                  closest_seen_startup_descriptor, {})
          pending_startup_descriptors[startup_descriptor] = flags
    for startup_descriptor, flags in old_startup_descriptors.items():
      # Merge flags if this also exists in startup_descriptors_to_add.
      if startup_descriptor in startup_descriptors_to_add:
        merged_flags = flags.copy()
        other_flags = startup_descriptors_to_add[startup_descriptor]
        assert not other_flags['conditional_startup']
        merged_flags['hot'] = \
            merged_flags['hot'] or other_flags['hot']
        merged_flags['startup'] = \
            merged_flags['startup'] or other_flags['startup']
        merged_flags['post_startup'] = \
            merged_flags['post_startup'] or other_flags['post_startup']
        new_startup_descriptors[startup_descriptor] = merged_flags
      else:
        new_startup_descriptors[startup_descriptor] = flags.copy()
      # Flush startup descriptors that followed this item in the new trace.
      if startup_descriptor in startup_descriptors_to_add_after_key:
        pending_startup_descriptors = \
            startup_descriptors_to_add_after_key[startup_descriptor]
        for pending_startup_descriptor, pending_flags \
            in pending_startup_descriptors.items():
          new_startup_descriptors[pending_startup_descriptor] = \
              pending_flags.copy()
    # Insert remaining new startup descriptors in the end.
    for startup_descriptor, flags \
        in startup_descriptors_to_add_in_the_end.items():
      assert startup_descriptor not in new_startup_descriptors
      new_startup_descriptors[startup_descriptor] = flags.copy()
  return new_startup_descriptors

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
    (descriptor, flags) = item
    return '%s%s%s%s' % (
        'H' if flags.get('hot') else '',
        'S' if flags.get('startup') else '',
        'P' if flags.get('post_startup') else '',
        descriptor)
  write_tmp_textual_artifact(
      profile_classes_and_methods.items(),
      iteration,
      options,
      'profile.txt',
      item_to_string)

def write_tmp_startup_descriptors(startup_descriptors, iteration, options):
  lines = [
      startup_descriptor_to_string(startup_descriptor, flags)
      for startup_descriptor, flags in startup_descriptors.items()]
  write_tmp_textual_artifact(
      lines, iteration, options, 'startup-descriptors.txt')

def startup_descriptor_to_string(startup_descriptor, flags):
  result = ''
  if flags['hot']:
    result += 'H'
  if flags['startup']:
    result += 'S'
  if flags['post_startup']:
    result += 'P'
  result += startup_descriptor
  return result

def should_include_startup_descriptor(descriptor, flags, options):
  if flags.get('conditional_startup') \
      and not options.include_conditional_startup:
    return False
  if flags.get('post_startup') \
      and not flags.get('startup') \
      and not options.include_post_startup:
    return False
  return True

def parse_options(argv):
  result = argparse.ArgumentParser(
      description='Generate a perfetto trace file.')
  result.add_argument('--apk',
                      help='Path to the .apk')
  result.add_argument('--apks',
                      help='Path to the .apks')
  result.add_argument('--app-id',
                      help='The application ID of interest',
                      required=True)
  result.add_argument('--bundle',
                      help='Path to the .aab')
  result.add_argument('--device-id',
                      help='Device id (e.g., emulator-5554).',
                      action='append')
  result.add_argument('--device-pin',
                      help='Device pin code (e.g., 1234)',
                      action='append')
  result.add_argument('--generalize-synthetics',
                      help='Whether synthetics should be abstracted into their '
                           'synthetic contexts',
                      action='store_true',
                      default=False)
  result.add_argument('--grant-post-notification-permission',
                      help='Grants the android.permission.POST_NOTIFICATIONS '
                           'permission before launching the app',
                      default=False,
                      action='store_true')
  result.add_argument('--logcat',
                      action='store_true',
                      default=False)
  result.add_argument('--include-conditional-startup',
                      help='Include conditional startup classes and methods in '
                           'the R8 startup descriptors',
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

  # Read the device pins.
  device_pins = options.device_pin or []
  del options.device_pin

  # Convert the device ids and pins into a list of devices.
  options.devices = []
  if options.device_id is None:
    # Assume a single device is attached.
    options.devices.append(
        Device(None, device_pins[0] if len(device_pins) > 0 else None))
  else:
    for i in range(len(options.device_id)):
      device_id = options.device_id[i]
      device_pin = device_pins[i] if i < len(device_pins) else None
      options.devices.append(Device(device_id, device_pin))
  del options.device_id

  paths = [
      path for path in [options.apk, options.apks, options.bundle]
      if path is not None]
  assert len(paths) <= 1, 'Expected at most one .apk, .apks, or .aab file.'
  assert options.main_activity is not None or options.use_existing_profile, \
      'Argument --main-activity is required except when running with ' \
      '--use-existing-profile.'

  return options, args

def run_on_device(device, options, startup_descriptors):
  adb_utils.root(device.device_id)
  if options.apk:
    adb_utils.uninstall(options.app_id, device.device_id)
    adb_utils.install(options.apk, device.device_id)
  elif options.apks:
    adb_utils.uninstall(options.app_id, device.device_id)
    adb_utils.install_apks(options.apks, device.device_id)
  elif options.bundle:
    adb_utils.uninstall(options.app_id, device.device_id)
    adb_utils.install_bundle(options.bundle, device.device_id)
  # Grant notifications.
  if options.grant_post_notification_permission:
    adb_utils.grant(
        options.app_id,
        'android.permission.POST_NOTIFICATIONS',
        device.device_id)
  if options.until_stable:
    iteration = 0
    stable_iterations = 0
    while True:
      old_startup_descriptors = startup_descriptors
      startup_descriptors = extend_startup_descriptors(
          old_startup_descriptors, iteration, device, options)
      diff = len(startup_descriptors) - len(old_startup_descriptors)
      if diff == 0:
        stable_iterations = stable_iterations + 1
        if stable_iterations == options.until_stable_iterations:
          break
      else:
        stable_iterations = 0
      iteration = iteration + 1
  else:
    for iteration in range(options.iterations):
      startup_descriptors = extend_startup_descriptors(
          startup_descriptors, iteration, device, options)
  return startup_descriptors

def main(argv):
  (options, args) = parse_options(argv)
  startup_descriptors = {}
  for device in options.devices:
    startup_descriptors = run_on_device(device, options, startup_descriptors)
  if options.out is not None:
    with open(options.out, 'w') as f:
      for startup_descriptor, flags in startup_descriptors.items():
        if should_include_startup_descriptor(startup_descriptor, flags, options):
          f.write(startup_descriptor_to_string(startup_descriptor, flags))
          f.write('\n')
  else:
    for startup_descriptor, flags in startup_descriptors.items():
      if should_include_startup_descriptor(startup_descriptor, flags, options):
        print(startup_descriptor_to_string(startup_descriptor, flags))

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
