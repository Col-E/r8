#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys
import threading
import time

from enum import Enum

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import profile_utils
import utils

DEVNULL = subprocess.DEVNULL


class ProcessReader(threading.Thread):

    def __init__(self, process):
        threading.Thread.__init__(self)
        self.lines = []
        self.process = process

    def run(self):
        for line in self.process.stdout:
            line = line.decode('utf-8').strip()
            self.lines.append(line)

    def stop(self):
        self.process.kill()


class ScreenState(Enum):
    OFF_LOCKED = 1,
    OFF_UNLOCKED = 2
    ON_LOCKED = 3
    ON_UNLOCKED = 4

    def is_off(self):
        return self == ScreenState.OFF_LOCKED or self == ScreenState.OFF_UNLOCKED

    def is_on(self):
        return self == ScreenState.ON_LOCKED or self == ScreenState.ON_UNLOCKED

    def is_on_and_locked(self):
        return self == ScreenState.ON_LOCKED

    def is_on_and_unlocked(self):
        return self == ScreenState.ON_UNLOCKED


def broadcast(action, component, device_id=None):
    print('Sending broadcast %s' % action)
    cmd = create_adb_cmd('shell am broadcast -a %s %s' % (action, component),
                         device_id)
    return subprocess.check_output(cmd).decode('utf-8').strip().splitlines()


def build_apks_from_bundle(bundle, output, overwrite=False):
    print('Building %s' % bundle)
    cmd = [
        'java', '-jar', utils.BUNDLETOOL_JAR, 'build-apks',
        '--bundle=%s' % bundle,
        '--output=%s' % output
    ]
    if overwrite:
        cmd.append('--overwrite')
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def capture_screen(target, device_id=None):
    print('Taking screenshot to %s' % target)
    tmp = '/sdcard/screencap.png'
    cmd = create_adb_cmd('shell screencap -p %s' % tmp, device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)
    pull(tmp, target, device_id)


def create_adb_cmd(arguments, device_id=None):
    assert isinstance(arguments, list) or isinstance(arguments, str)
    cmd = ['adb']
    if device_id is not None:
        cmd.append('-s')
        cmd.append(device_id)
    cmd.extend(
        arguments if isinstance(arguments, list) else arguments.split(' '))
    return cmd


def capture_app_profile_data(app_id, device_id=None):
    ps_cmd = create_adb_cmd('shell ps -o NAME', device_id)
    stdout = subprocess.check_output(ps_cmd).decode('utf-8').strip()
    killed_any = False
    for process_name in stdout.splitlines():
        if process_name.startswith(app_id):
            print('Flushing profile for process %s' % process_name)
            killall_cmd = create_adb_cmd(
                'shell killall -s SIGUSR1 %s' % process_name, device_id)
            killall_result = subprocess.run(killall_cmd, capture_output=True)
            stdout = killall_result.stdout.decode('utf-8')
            stderr = killall_result.stderr.decode('utf-8')
            if killall_result.returncode == 0:
                killed_any = True
            else:
                print('Error: stdout: %s, stderr: %s' % (stdout, stderr))
            time.sleep(5)
    assert killed_any, 'Expected to find at least one process'


def check_app_has_profile_data(app_id, device_id=None):
    profile_path = get_profile_path(app_id)
    cmd = create_adb_cmd(
        'shell du /data/misc/profiles/cur/0/%s/primary.prof' % app_id,
        device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    size_str = stdout[:stdout.index('\t')]
    assert size_str.isdigit()
    size = int(size_str)
    if size == 4:
        raise ValueError('Expected size of profile at %s to be > 4K' %
                         profile_path)


def clear_logcat(device_id=None):
    cmd = create_adb_cmd('logcat -c', device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def clear_profile_data(app_id, device_id=None):
    cmd = create_adb_cmd('shell cmd package compile --reset %s' % app_id,
                         device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def drop_caches(device_id=None):
    cmd = create_adb_cmd(['shell', 'echo 3 > /proc/sys/vm/drop_caches'],
                         device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def ensure_screen_on(device_id=None):
    if get_screen_state(device_id).is_off():
        toggle_screen(device_id)
    assert get_screen_state(device_id).is_on()


def ensure_screen_off(device_id=None):
    if get_screen_state(device_id).is_on():
        toggle_screen(device_id)
    assert get_screen_state(device_id).is_off()


def force_compilation(app_id, device_id=None):
    print('Applying AOT (full)')
    cmd = create_adb_cmd('shell cmd package compile -m speed -f %s' % app_id,
                         device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def force_profile_compilation(app_id, device_id=None):
    print('Applying AOT (profile)')
    cmd = create_adb_cmd(
        'shell cmd package compile -m speed-profile -f %s' % app_id, device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def get_apk_path(app_id, device_id=None):
    cmd = create_adb_cmd('shell pm path %s' % app_id, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    if not stdout.startswith('package:'):
        raise ValueError('Expected stdout to start with "package:", was: %s' %
                         stdout)
    apk_path = stdout[len('package:'):]
    if not apk_path.endswith('.apk'):
        raise ValueError('Expected stdout to end with ".apk", was: %s' % stdout)
    return apk_path


def get_component_name(app_id, activity):
    if activity.startswith(app_id):
        return '%s/.%s' % (app_id, activity[len(app_id) + 1:])
    else:
        return '%s/%s' % (app_id, activity)


def get_meminfo(app_id, device_id=None):
    cmd = create_adb_cmd('shell dumpsys meminfo -s %s' % app_id, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    for line in stdout.splitlines():
        if 'TOTAL PSS: ' in line:
            elements = [s for s in line.replace('TOTAL ', 'TOTAL_').split()]
            assert elements[0] == 'TOTAL_PSS:', elements[0]
            assert elements[1].isdigit()
            assert elements[2] == 'TOTAL_RSS:'
            assert elements[3].isdigit()
            return {
                'total_pss': int(elements[1]),
                'total_rss': int(elements[3])
            }
    raise ValueError('Unexpected stdout: %s' % stdout)


def get_profile_data(app_id, device_id=None):
    with utils.TempDir() as temp:
        source = get_profile_path(app_id)
        target = os.path.join(temp, 'primary.prof')
        pull(source, target, device_id)
        with open(target, 'rb') as f:
            return f.read()


def get_profile_path(app_id):
    return '/data/misc/profiles/cur/0/%s/primary.prof' % app_id


def get_minor_major_page_faults(app_id, device_id=None):
    pid = get_pid(app_id, device_id)
    cmd = create_adb_cmd('shell ps -p %i -o MINFL,MAJFL' % pid, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8')
    lines_it = iter(stdout.splitlines())
    first_line = next(lines_it)
    assert first_line == ' MINFL  MAJFL'
    second_line = next(lines_it)
    minfl, majfl = second_line.split()
    assert minfl.isdigit()
    assert majfl.isdigit()
    return (int(minfl), int(majfl))


def get_pid(app_id, device_id=None):
    cmd = create_adb_cmd('shell pidof %s' % app_id, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    assert stdout.isdigit()
    pid = int(stdout)
    return pid


def get_screen_state(device_id=None):
    cmd = create_adb_cmd('shell dumpsys nfc', device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    screen_state_value = None
    for line in stdout.splitlines():
        if line.startswith('mScreenState='):
            value_start_index = len('mScreenState=')
            screen_state_value = line[value_start_index:]
    if screen_state_value is None:
        raise ValueError(
            'Expected to find mScreenState in: adb shell dumpsys nfc')
    if not hasattr(ScreenState, screen_state_value):
        raise ValueError(
            'Expected mScreenState to be a value of ScreenState, was: %s' %
            screen_state_value)
    return ScreenState[screen_state_value]


def get_classes_and_methods_from_app_profile(app_id, device_id=None):
    apk_path = get_apk_path(app_id, device_id)
    profile_path = get_profile_path(app_id)
    cmd = create_adb_cmd(
        'shell profman --dump-classes-and-methods'
        ' --profile-file=%s --apk=%s --dex-location=%s' %
        (profile_path, apk_path, apk_path), device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    lines = stdout.splitlines()
    return profile_utils.parse_art_profile(lines)


def get_screen_off_timeout(device_id=None):
    cmd = create_adb_cmd('shell settings get system screen_off_timeout',
                         device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    assert stdout.isdigit()
    screen_off_timeout = int(stdout)
    return screen_off_timeout


def grant(app_id, permission, device_id=None):
    cmd = create_adb_cmd('shell pm grant %s %s' % (app_id, permission),
                         device_id)
    subprocess.check_call(cmd)


def install(apk, device_id=None):
    print('Installing %s' % apk)
    cmd = create_adb_cmd('install %s' % apk, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8')
    assert 'Success' in stdout


def install_apks(apks, device_id=None, max_attempts=3):
    print('Installing %s' % apks)
    cmd = [
        'java', '-jar', utils.BUNDLETOOL_JAR, 'install-apks',
        '--apks=%s' % apks
    ]
    if device_id is not None:
        cmd.append('--device-id=%s' % device_id)
    for i in range(max_attempts):
        process_result = subprocess.run(cmd, capture_output=True)
        stdout = process_result.stdout.decode('utf-8')
        stderr = process_result.stderr.decode('utf-8')
        if process_result.returncode == 0:
            return
        print('Failed to install %s' % apks)
        print('Stdout: %s' % stdout)
        print('Stderr: %s' % stderr)
        print('Retrying...')
    raise Exception('Unable to install apks in %s attempts' % max_attempts)


def install_bundle(bundle, device_id=None):
    print('Installing %s' % bundle)
    with utils.TempDir() as temp:
        apks = os.path.join(temp, 'Bundle.apks')
        build_apks_from_bundle(bundle, apks)
        install_apks(apks, device_id)


def install_profile_using_adb(app_id, host_profile_path, device_id=None):
    device_profile_path = get_profile_path(app_id)
    cmd = create_adb_cmd('push %s %s' %
                         (host_profile_path, device_profile_path))
    subprocess.check_call(cmd)
    stop_app(app_id, device_id)
    force_profile_compilation(app_id, device_id)


def install_profile_using_profileinstaller(app_id, device_id=None):
    # This assumes that the profileinstaller library has been added to the app,
    # https://developer.android.com/jetpack/androidx/releases/profileinstaller.
    action = 'androidx.profileinstaller.action.INSTALL_PROFILE'
    component = '%s/androidx.profileinstaller.ProfileInstallReceiver' % app_id
    stdout = broadcast(action, component, device_id)
    assert len(stdout) == 2
    assert stdout[0] == ('Broadcasting: Intent { act=%s flg=0x400000 cmp=%s }' %
                         (action, component))
    assert stdout[1] == 'Broadcast completed: result=1', stdout[1]
    stop_app(app_id, device_id)
    force_profile_compilation(app_id, device_id)


def issue_key_event(key_event, device_id=None, sleep_in_seconds=1):
    cmd = create_adb_cmd('shell input keyevent %s' % key_event, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    assert len(stdout) == 0
    time.sleep(sleep_in_seconds)


def launch_activity(app_id,
                    activity,
                    device_id=None,
                    intent_data_uri=None,
                    wait_for_activity_to_launch=False):
    args = ['shell', 'am', 'start', '-n', '%s/%s' % (app_id, activity)]
    if intent_data_uri:
        args.extend(['-d', intent_data_uri])
    if wait_for_activity_to_launch:
        args.append('-W')
    cmd = create_adb_cmd(args, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    assert stdout.startswith('Starting: Intent {')
    expected_component = 'cmp=%s' % get_component_name(app_id, activity)
    assert expected_component in stdout, \
        'was %s, expected %s' % (stdout, expected_component)
    lines = stdout.splitlines()
    result = {}
    for line in lines:
        if line.startswith('TotalTime: '):
            total_time_str = line.removeprefix('TotalTime: ')
            assert total_time_str.isdigit()
            result['total_time'] = int(total_time_str)
    assert not wait_for_activity_to_launch or 'total_time' in result, lines
    return result


def navigate_to_home_screen(device_id=None):
    cmd = create_adb_cmd('shell input keyevent KEYCODE_HOME', device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def prepare_for_interaction_with_device(device_id=None, device_pin=None):
    # Increase screen off timeout to avoid device screen turns off.
    twenty_four_hours_in_millis = 24 * 60 * 60 * 1000
    previous_screen_off_timeout = get_screen_off_timeout(device_id)
    set_screen_off_timeout(twenty_four_hours_in_millis, device_id)

    # Unlock device.
    unlock(device_id, device_pin)

    teardown_options = {
        'previous_screen_off_timeout': previous_screen_off_timeout
    }
    return teardown_options


def pull(source, target, device_id=None):
    cmd = create_adb_cmd('pull %s %s' % (source, target), device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def root(device_id=None):
    cmd = create_adb_cmd('root', device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def set_screen_off_timeout(screen_off_timeout_in_millis, device_id=None):
    cmd = create_adb_cmd(
        'shell settings put system screen_off_timeout %i' %
        screen_off_timeout_in_millis, device_id)
    stdout = subprocess.check_output(cmd).decode('utf-8').strip()
    assert len(stdout) == 0


def start_logcat(device_id=None, format=None, filter=None, silent=False):
    args = ['logcat']
    if format:
        args.extend(['--format', format])
    if silent:
        args.append('-s')
    if filter:
        args.append(filter)
    cmd = create_adb_cmd(args, device_id)
    logcat_process = subprocess.Popen(cmd,
                                      bufsize=1024 * 1024,
                                      stdout=subprocess.PIPE,
                                      stderr=subprocess.PIPE)
    reader = ProcessReader(logcat_process)
    reader.start()
    return reader


def stop_logcat(logcat_reader):
    logcat_reader.stop()
    logcat_reader.join()
    return logcat_reader.lines


def stop_app(app_id, device_id=None):
    print('Shutting down %s' % app_id)
    cmd = create_adb_cmd('shell am force-stop %s' % app_id, device_id)
    subprocess.check_call(cmd, stdout=DEVNULL, stderr=DEVNULL)


def teardown_after_interaction_with_device(teardown_options, device_id=None):
    # Reset screen off timeout.
    set_screen_off_timeout(teardown_options['previous_screen_off_timeout'],
                           device_id)


def toggle_screen(device_id=None):
    issue_key_event('KEYCODE_POWER', device_id)


def uninstall(app_id, device_id=None):
    print('Uninstalling %s' % app_id)
    cmd = create_adb_cmd('uninstall %s' % app_id, device_id)
    process_result = subprocess.run(cmd, capture_output=True)
    stdout = process_result.stdout.decode('utf-8')
    stderr = process_result.stderr.decode('utf-8')
    if process_result.returncode == 0:
        assert 'Success' in stdout
    elif stdout.startswith('cmd: Failure calling service package: Broken pipe'):
        assert app_id == 'com.google.android.youtube'
        print('Waiting after broken pipe')
        time.sleep(15)
    else:
        expected_error = (
            'java.lang.IllegalArgumentException: Unknown package: %s' % app_id)
        assert 'Failure [DELETE_FAILED_INTERNAL_ERROR]' in stdout \
            or expected_error in stderr, \
            'stdout: %s, stderr: %s' % (stdout, stderr)


def unlock(device_id=None, device_pin=None):
    ensure_screen_on(device_id)
    screen_state = get_screen_state(device_id)
    assert screen_state.is_on(), 'was %s' % screen_state
    if screen_state.is_on_and_locked():
        if device_pin is not None:
            raise NotImplementedError(
                'Device unlocking with pin not implemented')
        issue_key_event('KEYCODE_MENU', device_id)
        screen_state = get_screen_state(device_id)
    assert screen_state.is_on_and_unlocked(), 'was %s' % screen_state


def parse_options(argv):
    result = argparse.ArgumentParser(description='Run adb utils.')
    result.add_argument('--capture-screen', help='Capture screen to given file')
    result.add_argument('--device-id', help='Device id (e.g., emulator-5554).')
    result.add_argument('--device-pin', help='Device pin code (e.g., 1234)')
    result.add_argument('--ensure-screen-off',
                        help='Ensure screen off',
                        action='store_true',
                        default=False)
    result.add_argument('--get-screen-state',
                        help='Get screen state',
                        action='store_true',
                        default=False)
    result.add_argument('--unlock',
                        help='Unlock device',
                        action='store_true',
                        default=False)
    options, args = result.parse_known_args(argv)
    return options, args


def main(argv):
    (options, args) = parse_options(argv)
    if options.capture_screen:
        capture_screen(options.capture_screen, options.device_id)
    if options.ensure_screen_off:
        ensure_screen_off(options.device_id)
    elif options.get_screen_state:
        print(get_screen_state(options.device_id))
    elif options.unlock:
        unlock(options.device_id, options.device_pin)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
