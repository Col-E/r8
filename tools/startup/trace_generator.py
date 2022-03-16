#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import sys
import time

try:
  from perfetto.trace_processor import TraceProcessor
except ImportError:
  sys.exit(
      'Unable to analyze perfetto trace without the perfetto library. '
      'Install instructions:\n'
      '    sudo apt install python3-pip\n'
      '    pip3 install perfetto')

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import adb_utils
import perfetto_utils
import utils

def setup(options):
  # Increase screen off timeout to avoid device screen turns off.
  twenty_four_hours_in_millis = 24 * 60 * 60 * 1000
  previous_screen_off_timeout = adb_utils.get_screen_off_timeout(
      options.device_id)
  adb_utils.set_screen_off_timeout(
      twenty_four_hours_in_millis, options.device_id)

  # Unlock device.
  adb_utils.unlock(options.device_id, options.device_pin)

  tear_down_options = {
    'previous_screen_off_timeout': previous_screen_off_timeout
  }
  return tear_down_options

def tear_down(options, tear_down_options):
  # Reset screen off timeout.
  adb_utils.set_screen_off_timeout(
      tear_down_options['previous_screen_off_timeout'],
      options.device_id)

def run_all(options, tmp_dir):
  # Launch app while collecting information.
  data_avg = {}
  for iteration in range(options.iterations):
    print('Starting iteration %i' % iteration)
    out_dir = os.path.join(options.out_dir, str(iteration))
    prepare_for_run(out_dir, options)
    data = run(out_dir, options, tmp_dir)
    add_data(data_avg, data)
    print("Result:")
    print(data)
    print("Done")
  for key, value in data_avg.items():
    if isinstance(value, int):
      data_avg[key] = value / options.iterations
  print("Average result:")
  print(data_avg)
  write_data(options.out_dir, data_avg)

def prepare_for_run(out_dir, options):
  adb_utils.root(options.device_id)
  adb_utils.uninstall(options.app_id, options.device_id)
  adb_utils.install(options.apk, options.device_id)
  adb_utils.clear_profile_data(options.app_id, options.device_id)
  if options.aot:
    adb_utils.force_compilation(options.app_id, options.device_id)
  elif options.aot_profile:
    adb_utils.launch_activity(
        options.app_id, options.main_activity, options.device_id)
    time.sleep(options.aot_profile_sleep)
    adb_utils.stop_app(options.app_id, options.device_id)
    adb_utils.force_profile_compilation(options.app_id, options.device_id)

  adb_utils.drop_caches(options.device_id)
  os.makedirs(out_dir, exist_ok=True)

def run(out_dir, options, tmp_dir):
  assert adb_utils.get_screen_state().is_on_and_unlocked()

  # Start perfetto trace collector.
  perfetto_process, perfetto_trace_path = perfetto_utils.record_android_trace(
      out_dir, tmp_dir)

  # Launch main activity.
  launch_activity_result = adb_utils.launch_activity(
      options.app_id,
      options.main_activity,
      options.device_id,
      wait_for_activity_to_launch=True)

  # Wait for perfetto trace collector to stop.
  perfetto_utils.stop_record_android_trace(perfetto_process, out_dir)

  # Get minor and major page faults from app process.
  data = compute_data(launch_activity_result, perfetto_trace_path, options)
  write_data(out_dir, data)
  return data

def add_data(sum_data, data):
  for key, value in data.items():
    if key == 'time':
      continue
    if key in sum_data:
      if key == 'app_id':
        assert sum_data[key] == value
      else:
        existing_value = sum_data[key]
        assert isinstance(value, int)
        assert isinstance(existing_value, int)
        sum_data[key] = existing_value + value
    else:
      sum_data[key] = value

def compute_data(launch_activity_result, perfetto_trace_path, options):
  minfl, majfl = adb_utils.get_minor_major_page_faults(
      options.app_id, options.device_id)
  data = {
    'app_id': options.app_id,
    'time': time.ctime(time.time()),
    'minfl': minfl,
    'majfl': majfl
  }
  startup_data = compute_startup_data(
      launch_activity_result, perfetto_trace_path, options)
  return data | startup_data

def compute_startup_data(launch_activity_result, perfetto_trace_path, options):
  startup_data = {
    'time_to_activity_started_ms': launch_activity_result.get('total_time')
  }
  perfetto_startup_data = {}
  if not options.no_perfetto:
    trace_processor = TraceProcessor(file_path=perfetto_trace_path)

    # Compute time to first frame according to the builtin android_startup metric.
    startup_metric = trace_processor.metric(['android_startup'])
    time_to_first_frame_ms = \
        startup_metric.android_startup.startup[0].to_first_frame.dur_ms

    # Compute time to first and last doFrame event.
    bind_application_slice = perfetto_utils.find_unique_slice_by_name(
        'bindApplication', options, trace_processor)
    activity_start_slice = perfetto_utils.find_unique_slice_by_name(
        'activityStart', options, trace_processor)
    do_frame_slices = perfetto_utils.find_slices_by_name(
        'Choreographer#doFrame', options, trace_processor)
    first_do_frame_slice = next(do_frame_slices)
    *_, last_do_frame_slice = do_frame_slices

    perfetto_startup_data = {
      'time_to_first_frame_ms': round(time_to_first_frame_ms),
      'time_to_first_choreographer_do_frame_ms':
          round(perfetto_utils.get_slice_end_since_start(
              first_do_frame_slice, bind_application_slice)),
      'time_to_last_choreographer_do_frame_ms':
          round(perfetto_utils.get_slice_end_since_start(
              last_do_frame_slice, bind_application_slice))
    }

  # Return combined startup data.
  return startup_data | perfetto_startup_data

def write_data(out_dir, data):
  data_path = os.path.join(out_dir, 'data.txt')
  with open(data_path, 'w') as f:
    for key, value in data.items():
      f.write('%s=%s\n' % (key, str(value)))

def parse_options(argv):
  result = argparse.ArgumentParser(
      description='Generate a perfetto trace file.')
  result.add_argument('--app-id',
                      help='The application ID of interest',
                      required=True)
  result.add_argument('--aot',
                      help='Enable force compilation',
                      default=False,
                      action='store_true')
  result.add_argument('--aot-profile',
                      help='Enable force compilation using profiles',
                      default=False,
                      action='store_true')
  result.add_argument('--aot-profile-sleep',
                      help='Duration in seconds before forcing compilation',
                      default=15,
                      type=int)
  result.add_argument('--apk',
                      help='Path to the APK',
                      required=True)
  result.add_argument('--device-id',
                      help='Device id (e.g., emulator-5554).')
  result.add_argument('--device-pin',
                      help='Device pin code (e.g., 1234)')
  result.add_argument('--iterations',
                      help='Number of traces to generate',
                      default=1,
                      type=int)
  result.add_argument('--main-activity',
                      help='Main activity class name',
                      required=True)
  result.add_argument('--no-perfetto',
                      help='Disables perfetto trace generation',
                      action='store_true',
                      default=False)
  result.add_argument('--out-dir',
                      help='Directory to store trace files in',
                      required=True)
  options, args = result.parse_known_args(argv)
  assert (not options.aot) or (not options.aot_profile)
  return options, args

def main(argv):
  (options, args) = parse_options(argv)
  with utils.TempDir() as tmp_dir:
    tear_down_options = adb_utils.prepare_for_interaction_with_device(
        options.device_id, options.device_pin)
    run_all(options, tmp_dir)
    adb_utils.tear_down_after_interaction_with_device(
        tear_down_options, options.device_id)

if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))