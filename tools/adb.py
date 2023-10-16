#!/usr/bin/env python3
# Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import subprocess
import time
import utils


def install_apk_on_emulator(apk, emulator_id, quiet=False):
    cmd = ['adb', '-s', emulator_id, 'install', '-r', '-d', apk]
    if quiet:
        subprocess.check_output(cmd)
    else:
        subprocess.check_call(cmd)


def uninstall_apk_on_emulator(app_id, emulator_id):
    process = subprocess.Popen(['adb', '-s', emulator_id, 'uninstall', app_id],
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
    stdout, stderr = process.communicate()
    stdout = stdout.decode('UTF-8')
    stderr = stderr.decode('UTF-8')

    if stdout.strip() == 'Success':
        # Successfully uninstalled
        return

    if 'Unknown package: {}'.format(app_id) in stderr:
        # Application not installed
        return

    # Check if the app is listed in packages
    packages = subprocess.check_output(
        ['adb', 'shell', 'pm', 'list', 'packages'])
    if not 'package:' + app_id in packages:
        return

    raise Exception(
        'Unexpected result from `adb uninstall {}\nStdout: {}\nStderr: {}'.
        format(app_id, stdout, stderr))


def wait_for_emulator(emulator_id):
    stdout = subprocess.check_output(['adb', 'devices']).decode('UTF-8')
    if '{}\tdevice'.format(emulator_id) in stdout:
        return True

    print('Emulator \'{}\' not connected; waiting for connection'.format(
        emulator_id))

    time_waited = 0
    while True:
        time.sleep(10)
        time_waited += 10
        stdout = subprocess.check_output(['adb', 'devices']).decode('UTF-8')
        if '{}\tdevice'.format(emulator_id) not in stdout:
            print('... still waiting for connection')
            if time_waited >= 5 * 60:
                return False
        else:
            return True


def run_monkey(app_id, emulator_id, apk, monkey_events, quiet, enable_logging):
    if not wait_for_emulator(emulator_id):
        return False

    install_apk_on_emulator(apk, emulator_id, quiet)

    # Intentionally using a constant seed such that the monkey generates the same
    # event sequence for each shrinker.
    random_seed = 42

    cmd = [
        'adb', '-s', emulator_id, 'shell', 'monkey', '-p', app_id, '-s',
        str(random_seed),
        str(monkey_events)
    ]

    try:
        stdout = utils.RunCmd(cmd, quiet=quiet, logging=enable_logging)
        succeeded = ('Events injected: {}'.format(monkey_events) in stdout)
    except subprocess.CalledProcessError as e:
        succeeded = False

    uninstall_apk_on_emulator(app_id, emulator_id)

    return succeeded


def run_instrumented(app_id,
                     test_id,
                     emulator_id,
                     apk,
                     test_apk,
                     quiet,
                     enable_logging,
                     test_runner='androidx.test.runner.AndroidJUnitRunner'):
    if not wait_for_emulator(emulator_id):
        return None

    install_apk_on_emulator(apk, emulator_id, quiet)
    install_apk_on_emulator(test_apk, emulator_id, quiet)

    cmd = [
        'adb', '-s', emulator_id, 'shell', 'am', 'instrument', '-w',
        '{}/{}'.format(test_id, test_runner)
    ]

    try:
        stdout = utils.RunCmd(cmd, quiet=quiet, logging=enable_logging)
        # The runner will print OK (X tests) if completed succesfully
        succeeded = any("OK (" in s for s in stdout)
    except subprocess.CalledProcessError as e:
        succeeded = False

    uninstall_apk_on_emulator(test_id, emulator_id)
    uninstall_apk_on_emulator(app_id, emulator_id)

    return succeeded
