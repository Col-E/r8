#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import shutil
import subprocess
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import apk_masseur
import apk_utils
import extractmarker
import toolhelper
import utils
import zip_utils


def parse_options(argv):
    result = argparse.ArgumentParser(
        description='Instrument the dex files of a given apk to print what is '
        'executed.')
    result.add_argument('--apk', help='Path to the .apk', required=True)
    result.add_argument('--dex-files',
                        action='append',
                        help='Name of dex files to instrument')
    result.add_argument('--discard',
                        action='append',
                        help='Name of dex files to discard')
    result.add_argument('--out',
                        help='Destination of resulting apk',
                        required=True)
    options, args = result.parse_known_args(argv)
    return options, args


def add_instrumented_dex(dex_file, instrumented_dex_index, instrumented_dir):
    dex_name = get_dex_name(instrumented_dex_index)
    destination = os.path.join(instrumented_dir, dex_name)
    shutil.move(dex_file, destination)


def get_dex_name(dex_index):
    assert dex_index > 0
    return 'classes.dex' if dex_index == 1 else ('classes%s.dex' % dex_index)


def instrument_dex_file(dex_file, include_instrumentation_server, options,
                        tmp_dir):
    d8_cmd = [
        'java', '-cp', utils.R8_JAR,
        '-Dcom.android.tools.r8.startup.instrumentation.instrument=1',
        '-Dcom.android.tools.r8.startup.instrumentation.instrumentationtag=R8'
    ]
    if not include_instrumentation_server:
        # We avoid injecting the InstrumentationServer by specifying it should only
        # be added if foo.bar.Baz is in the program.
        d8_cmd.append(
            '-Dcom.android.tools.r8.startup.instrumentation.instrumentationserversyntheticcontext=foo.bar.Baz'
        )
    d8_cmd.extend([
        'com.android.tools.r8.D8', '--min-api',
        str(apk_utils.get_min_api(options.apk)), '--output', tmp_dir,
        '--release', dex_file
    ])
    subprocess.check_call(d8_cmd)
    instrumented_dex_files = []
    instrumented_dex_index = 1
    while True:
        instrumented_dex_name = get_dex_name(instrumented_dex_index)
        instrumented_dex_file = os.path.join(tmp_dir, instrumented_dex_name)
        if not os.path.exists(instrumented_dex_file):
            break
        instrumented_dex_files.append(instrumented_dex_file)
        instrumented_dex_index = instrumented_dex_index + 1
    assert len(instrumented_dex_files) > 0
    return instrumented_dex_files


def should_discard_dex_file(dex_name, options):
    return options.discard is not None and dex_name in options.discard


def should_instrument_dex_file(dex_name, options):
    return options.dex_files is not None and dex_name in options.dex_files


def main(argv):
    options, args = parse_options(argv)
    with utils.TempDir() as tmp_dir:
        # Extract the dex files of the apk.
        uninstrumented_dir = os.path.join(tmp_dir, 'uninstrumented')
        os.mkdir(uninstrumented_dir)

        dex_predicate = \
            lambda name : name.startswith('classes') and name.endswith('.dex')
        zip_utils.extract_all_that_matches(options.apk, uninstrumented_dir,
                                           dex_predicate)

        # Instrument each dex one by one.
        instrumented_dir = os.path.join(tmp_dir, 'instrumented')
        os.mkdir(instrumented_dir)

        include_instrumentation_server = True
        instrumented_dex_index = 1
        uninstrumented_dex_index = 1
        while True:
            dex_name = get_dex_name(uninstrumented_dex_index)
            dex_file = os.path.join(uninstrumented_dir, dex_name)
            if not os.path.exists(dex_file):
                break
            if not should_discard_dex_file(dex_name, options):
                if should_instrument_dex_file(dex_name, options):
                    with utils.TempDir() as tmp_instrumentation_dir:
                        instrumented_dex_files = \
                            instrument_dex_file(
                                dex_file,
                                include_instrumentation_server,
                                options,
                                tmp_instrumentation_dir)
                        for instrumented_dex_file in instrumented_dex_files:
                            add_instrumented_dex(instrumented_dex_file,
                                                 instrumented_dex_index,
                                                 instrumented_dir)
                            instrumented_dex_index = instrumented_dex_index + 1
                        include_instrumentation_server = False
                else:
                    add_instrumented_dex(dex_file, instrumented_dex_index,
                                         instrumented_dir)
                    instrumented_dex_index = instrumented_dex_index + 1
            uninstrumented_dex_index = uninstrumented_dex_index + 1

        assert instrumented_dex_index > 1

        # Masseur APK.
        apk_masseur.masseur(options.apk, dex=instrumented_dir, out=options.out)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
