#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import datetime
import os
import re
import statistics
import sys
import time

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import adb_utils
import apk_utils
import perfetto_utils
import utils


def setup(options):
    # Increase screen off timeout to avoid device screen turns off.
    twenty_four_hours_in_millis = 24 * 60 * 60 * 1000
    previous_screen_off_timeout = adb_utils.get_screen_off_timeout(
        options.device_id)
    adb_utils.set_screen_off_timeout(twenty_four_hours_in_millis,
                                     options.device_id)

    # Unlock device.
    adb_utils.unlock(options.device_id, options.device_pin)

    teardown_options = {
        'previous_screen_off_timeout': previous_screen_off_timeout
    }
    return teardown_options


def teardown(options, teardown_options):
    # Reset screen off timeout.
    adb_utils.set_screen_off_timeout(
        teardown_options['previous_screen_off_timeout'], options.device_id)


def run_all(apk_or_apks, options, tmp_dir):
    # Launch app while collecting information.
    data_total = {}
    for iteration in range(1, options.iterations + 1):
        print('Starting iteration %i' % iteration)
        out_dir = os.path.join(options.out_dir, str(iteration))
        teardown_options = setup_for_run(apk_or_apks, out_dir, options)
        data = run(out_dir, options, tmp_dir)
        teardown_for_run(out_dir, options, teardown_options)
        add_data(data_total, data)
        print('Result:')
        print(data)
        print(compute_data_summary(data_total))
        print('Done')
    print('Average result:')
    data_summary = compute_data_summary(data_total)
    print(data_summary)
    write_data_to_dir(options.out_dir, data_summary)
    if options.out:
        write_data_to_file(options.out, data_summary)


def compute_data_summary(data_total):
    data_summary = {}
    for key, value in data_total.items():
        if not isinstance(value, list):
            data_summary[key] = value
            continue
        data_summary['%s_avg' % key] = round(statistics.mean(value), 1)
        data_summary['%s_med' % key] = statistics.median(value)
        data_summary['%s_min' % key] = min(value)
        data_summary['%s_max' % key] = max(value)
    return data_summary


def setup_for_run(apk_or_apks, out_dir, options):
    adb_utils.root(options.device_id)

    print('Installing')
    adb_utils.uninstall(options.app_id, options.device_id)
    if apk_or_apks['apk']:
        adb_utils.install(apk_or_apks['apk'], options.device_id)
    else:
        assert apk_or_apks['apks']
        adb_utils.install_apks(apk_or_apks['apks'], options.device_id)

    os.makedirs(out_dir, exist_ok=True)

    # Grant notifications.
    if options.grant_post_notification_permission:
        adb_utils.grant(options.app_id, 'android.permission.POST_NOTIFICATIONS',
                        options.device_id)

    # AOT compile.
    if options.aot:
        print('AOT compiling')
        if options.baseline_profile:
            adb_utils.clear_profile_data(options.app_id, options.device_id)
            if options.baseline_profile_install == 'adb':
                adb_utils.install_profile_using_adb(options.app_id,
                                                    options.baseline_profile,
                                                    options.device_id)
            else:
                assert options.baseline_profile_install == 'profileinstaller'
                adb_utils.install_profile_using_profileinstaller(
                    options.app_id, options.device_id)
        else:
            adb_utils.force_compilation(options.app_id, options.device_id)

    # Cooldown and then unlock device.
    if options.cooldown > 0:
        print('Cooling down for %i seconds' % options.cooldown)
        assert adb_utils.get_screen_state(options.device_id).is_off()
        time.sleep(options.cooldown)
        teardown_options = adb_utils.prepare_for_interaction_with_device(
            options.device_id, options.device_pin)
    else:
        teardown_options = None

    # Prelaunch for hot startup.
    if options.hot_startup:
        print('Prelaunching')
        adb_utils.launch_activity(options.app_id,
                                  options.main_activity,
                                  options.device_id,
                                  wait_for_activity_to_launch=False)
        time.sleep(options.startup_duration)
        adb_utils.navigate_to_home_screen(options.device_id)
        time.sleep(1)

    # Drop caches before run.
    adb_utils.drop_caches(options.device_id)
    return teardown_options


def teardown_for_run(out_dir, options, teardown_options):
    assert adb_utils.get_screen_state(options.device_id).is_on_and_unlocked()

    if options.capture_screen:
        target = os.path.join(out_dir, 'screen.png')
        adb_utils.capture_screen(target, options.device_id)

    if options.cooldown > 0:
        adb_utils.teardown_after_interaction_with_device(
            teardown_options, options.device_id)
        adb_utils.ensure_screen_off(options.device_id)
    else:
        assert teardown_options is None


def run(out_dir, options, tmp_dir):
    assert adb_utils.get_screen_state(options.device_id).is_on_and_unlocked()

    # Start logcat for time to fully drawn.
    logcat_process = None
    if options.fully_drawn_logcat_message:
        adb_utils.clear_logcat(options.device_id)
        logcat_process = adb_utils.start_logcat(
            options.device_id,
            format='time',
            filter='%s ActivityTaskManager:I' %
            options.fully_drawn_logcat_filter,
            silent=True)

    # Start perfetto trace collector.
    perfetto_process = None
    perfetto_trace_path = None
    if options.perfetto:
        perfetto_process, perfetto_trace_path = perfetto_utils.record_android_trace(
            out_dir, tmp_dir, options.device_id)

    # Launch main activity.
    launch_activity_result = adb_utils.launch_activity(
        options.app_id,
        options.main_activity,
        options.device_id,
        intent_data_uri=options.intent_data_uri,
        wait_for_activity_to_launch=True)

    # Wait for app to be fully drawn.
    logcat = None
    if logcat_process is not None:
        wait_until_fully_drawn(logcat_process, options)
        logcat = adb_utils.stop_logcat(logcat_process)

    # Wait for perfetto trace collector to stop.
    if options.perfetto:
        perfetto_utils.stop_record_android_trace(perfetto_process, out_dir)

    # Get minor and major page faults from app process.
    data = compute_data(launch_activity_result, logcat, perfetto_trace_path,
                        options)
    write_data_to_dir(out_dir, data)
    return data


def wait_until_fully_drawn(logcat_process, options):
    print('Waiting until app is fully drawn')
    while True:
        is_fully_drawn = any(
            is_app_fully_drawn_logcat_message(line, options) \
            for line in logcat_process.lines)
        if is_fully_drawn:
            break
        time.sleep(1)
    print('Done')


def compute_time_to_fully_drawn_from_time_to_first_frame(logcat, options):
    displayed_time = None
    fully_drawn_time = None
    for line in logcat:
        if is_app_displayed_logcat_message(line, options):
            displayed_time = get_timestamp_from_logcat_message(line)
        elif is_app_fully_drawn_logcat_message(line, options):
            fully_drawn_time = get_timestamp_from_logcat_message(line)
    assert displayed_time is not None
    assert fully_drawn_time is not None
    assert fully_drawn_time >= displayed_time
    return fully_drawn_time - displayed_time


def get_timestamp_from_logcat_message(line):
    time_end_index = len('00-00 00:00:00.000')
    time_format = '%m-%d %H:%M:%S.%f'
    time_str = line[0:time_end_index] + '000'
    time_seconds = datetime.datetime.strptime(time_str, time_format).timestamp()
    return int(time_seconds * 1000)


def is_app_displayed_logcat_message(line, options):
    substring = 'Displayed %s' % adb_utils.get_component_name(
        options.app_id, options.main_activity)
    return substring in line


def is_app_fully_drawn_logcat_message(line, options):
    return re.search(options.fully_drawn_logcat_message, line)


def add_data(data_total, data):
    for key, value in data.items():
        if key == 'app_id':
            assert data_total.get(key, value) == value
            data_total[key] = value
        if key == 'time':
            continue
        if key in data_total:
            if key == 'app_id':
                assert data_total[key] == value
            else:
                existing_value = data_total[key]
                assert isinstance(value, int)
                assert isinstance(existing_value, list)
                existing_value.append(value)
        else:
            assert isinstance(value, int), key
            data_total[key] = [value]


def compute_data(launch_activity_result, logcat, perfetto_trace_path, options):
    minfl, majfl = adb_utils.get_minor_major_page_faults(
        options.app_id, options.device_id)
    meminfo = adb_utils.get_meminfo(options.app_id, options.device_id)
    data = {
        'app_id': options.app_id,
        'time': time.ctime(time.time()),
        'minfl': minfl,
        'majfl': majfl
    }
    data.update(meminfo)
    startup_data = compute_startup_data(launch_activity_result, logcat,
                                        perfetto_trace_path, options)
    return data | startup_data


def compute_startup_data(launch_activity_result, logcat, perfetto_trace_path,
                         options):
    time_to_first_frame = launch_activity_result.get('total_time')
    startup_data = {'adb_startup': time_to_first_frame}

    # Time to fully drawn.
    if options.fully_drawn_logcat_message:
        startup_data['time_to_fully_drawn'] = \
            compute_time_to_fully_drawn_from_time_to_first_frame(logcat, options) \
                + time_to_first_frame

    # Perfetto stats.
    perfetto_startup_data = {}
    if options.perfetto:
        TraceProcessor = perfetto_utils.get_trace_processor()
        trace_processor = TraceProcessor(file_path=perfetto_trace_path)

        # Compute time to first frame according to the builtin android_startup
        # metric.
        startup_metric = trace_processor.metric(['android_startup'])
        time_to_first_frame_ms = \
            startup_metric.android_startup.startup[0].to_first_frame.dur_ms
        perfetto_startup_data['perfetto_startup'] = round(
            time_to_first_frame_ms)

        if not options.hot_startup:
            # Compute time to first and last doFrame event.
            bind_application_slice = perfetto_utils.find_unique_slice_by_name(
                'bindApplication', options, trace_processor)
            activity_start_slice = perfetto_utils.find_unique_slice_by_name(
                'activityStart', options, trace_processor)
            do_frame_slices = perfetto_utils.find_slices_by_name(
                'Choreographer#doFrame', options, trace_processor)
            first_do_frame_slice = next(do_frame_slices)
            *_, last_do_frame_slice = do_frame_slices

            perfetto_startup_data.update({
                'time_to_first_choreographer_do_frame_ms':
                    round(
                        perfetto_utils.get_slice_end_since_start(
                            first_do_frame_slice, bind_application_slice)),
                'time_to_last_choreographer_do_frame_ms':
                    round(
                        perfetto_utils.get_slice_end_since_start(
                            last_do_frame_slice, bind_application_slice))
            })

    # Return combined startup data.
    return startup_data | perfetto_startup_data


def write_data_to_dir(out_dir, data):
    data_path = os.path.join(out_dir, 'data.txt')
    write_data_to_file(data_path, data)


def write_data_to_file(out_file, data):
    with open(out_file, 'w') as f:
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
    result.add_argument('--apk', help='Path to the .apk')
    result.add_argument('--apks', help='Path to the .apks')
    result.add_argument('--bundle', help='Path to the .aab')
    result.add_argument('--capture-screen',
                        help='Take a screenshot after each test',
                        default=False,
                        action='store_true')
    result.add_argument('--cooldown',
                        help='Seconds to wait before running each iteration',
                        default=0,
                        type=int)
    result.add_argument('--device-id', help='Device id (e.g., emulator-5554).')
    result.add_argument('--device-pin', help='Device pin code (e.g., 1234)')
    result.add_argument('--fully-drawn-logcat-filter',
                        help='Logcat filter for the fully drawn message '
                        '(e.g., "tag:I")')
    result.add_argument('--fully-drawn-logcat-message',
                        help='Logcat message that indicates that the app is '
                        'fully drawn (regexp)')
    result.add_argument('--grant-post-notification-permission',
                        help='Grants the android.permission.POST_NOTIFICATIONS '
                        'permission before launching the app',
                        default=False,
                        action='store_true')
    result.add_argument('--hot-startup',
                        help='Measure hot startup instead of cold startup',
                        default=False,
                        action='store_true')
    result.add_argument('--intent-data-uri',
                        help='Value to use for the -d argument to the intent '
                        'that is used to launch the app')
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
    result.add_argument('--out', help='File to store result in')
    result.add_argument('--out-dir',
                        help='Directory to store trace files in',
                        required=True)
    result.add_argument('--baseline-profile',
                        help='Baseline profile (.prof) in binary format')
    result.add_argument('--baseline-profile-metadata',
                        help='Baseline profile metadata (.profm) in binary '
                        'format')
    result.add_argument('--baseline-profile-install',
                        help='Whether to install profile using adb or '
                        'profileinstaller',
                        choices=['adb', 'profileinstaller'],
                        default='profileinstaller')
    result.add_argument('--startup-duration',
                        help='Duration in seconds before shutting down app',
                        default=15,
                        type=int)
    options, args = result.parse_known_args(argv)
    setattr(options, 'perfetto', not options.no_perfetto)

    paths = [
        path for path in [options.apk, options.apks, options.bundle]
        if path is not None
    ]
    assert len(paths) == 1, 'Expected exactly one .apk, .apks, or .aab file.'

    # Build .apks file up front to avoid building the bundle upon each install.
    if options.bundle:
        os.makedirs(options.out_dir, exist_ok=True)
        options.apks = os.path.join(options.out_dir, 'Bundle.apks')
        adb_utils.build_apks_from_bundle(options.bundle,
                                         options.apks,
                                         overwrite=True)
        del options.bundle

    # Profile is only used with --aot.
    assert options.aot or not options.baseline_profile

    # Fully drawn logcat filter and message is absent or both present.
    assert (options.fully_drawn_logcat_filter is None) == \
        (options.fully_drawn_logcat_message is None)

    return options, args


def global_setup(options):
    # If there is no cooldown then unlock the screen once. Otherwise we turn off
    # the screen during the cooldown and unlock the screen before each iteration.
    teardown_options = None
    if options.cooldown == 0:
        teardown_options = adb_utils.prepare_for_interaction_with_device(
            options.device_id, options.device_pin)
        assert adb_utils.get_screen_state(options.device_id).is_on()
    else:
        adb_utils.ensure_screen_off(options.device_id)
    return teardown_options


def global_teardown(options, teardown_options):
    if options.cooldown == 0:
        adb_utils.teardown_after_interaction_with_device(
            teardown_options, options.device_id)
    else:
        assert teardown_options is None


def main(argv):
    (options, args) = parse_options(argv)
    with utils.TempDir() as tmp_dir:
        apk_or_apks = {'apk': options.apk, 'apks': options.apks}
        if options.baseline_profile \
            and options.baseline_profile_install == 'profileinstaller':
            assert not options.apks, 'Unimplemented'
            apk_or_apks['apk'] = apk_utils.add_baseline_profile_to_apk(
                options.apk, options.baseline_profile,
                options.baseline_profile_metadata, tmp_dir)
        teardown_options = global_setup(options)
        run_all(apk_or_apks, options, tmp_dir)
        global_teardown(options, teardown_options)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
