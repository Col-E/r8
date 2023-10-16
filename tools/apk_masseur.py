#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import glob
import optparse
import os
import shutil
import sys

import apk_utils
import utils
import zip_utils

USAGE = 'usage: %prog [options] <apk>'


def parse_options():
    parser = optparse.OptionParser(usage=USAGE)
    parser.add_option('--clear-profile',
                      help='To remove baseline.prof and baseline.profm from '
                      'assets/dexopt/',
                      default=False,
                      action='store_true')
    parser.add_option('--dex',
                      help='Directory or archive with dex files to use instead '
                      'of those in the apk',
                      default=None)
    parser.add_option(
        '--desugared-library-dex',
        help='Path to desugared library dex file to use or archive '
        'containing a single classes.dex file',
        default=None)
    parser.add_option(
        '--resources',
        help=('pattern that matches resources to use instead of ' +
              'those in the apk'),
        default=None)
    parser.add_option('--out',
                      help='output file (default ./$(basename <apk>))',
                      default=None)
    parser.add_option('--keystore',
                      help='keystore file (default ~/.android/app.keystore)',
                      default=None)
    parser.add_option(
        '--install',
        help='install the generated apk with adb options -t -r -d',
        default=False,
        action='store_true')
    parser.add_option('--adb-options',
                      help='additional adb options when running adb',
                      default=None)
    parser.add_option('--quiet', help='disable verbose logging', default=False)
    parser.add_option('--sign-before-align',
                      help='Sign the apk before aligning',
                      default=False,
                      action='store_true')
    (options, args) = parser.parse_args()
    if len(args) != 1:
        parser.error('Expected <apk> argument, got: ' + ' '.join(args))
    apk = args[0]
    return (options, apk)


def is_archive(file):
    return file.endswith('.zip') or file.endswith('.jar')


def repack(apk, clear_profile, processed_out, desugared_library_dex, resources,
           temp, quiet, logging):
    processed_apk = os.path.join(temp, 'processed.apk')
    shutil.copyfile(apk, processed_apk)

    if clear_profile:
        zip_utils.remove_files_from_zip(
            ['assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'],
            processed_apk)

    if not processed_out:
        utils.Print('Using original dex as is', quiet=quiet)
        return processed_apk

    utils.Print('Repacking APK with dex files from {}'.format(processed_out),
                quiet=quiet)

    # Delete original dex files in APK.
    with utils.ChangedWorkingDirectory(temp, quiet=quiet):
        cmd = ['zip', '-d', 'processed.apk', '*.dex']
        utils.RunCmd(cmd, quiet=quiet, logging=logging)

    # Unzip the jar or zip file into `temp`.
    if is_archive(processed_out):
        cmd = ['unzip', processed_out, '-d', temp]
        if quiet:
            cmd.insert(1, '-q')
        utils.RunCmd(cmd, quiet=quiet, logging=logging)
        processed_out = temp
    elif desugared_library_dex:
        for dex_name in glob.glob('*.dex', root_dir=processed_out):
            src = os.path.join(processed_out, dex_name)
            dst = os.path.join(temp, dex_name)
            shutil.copyfile(src, dst)
        processed_out = temp

    if desugared_library_dex:
        desugared_library_dex_index = len(glob.glob('*.dex', root_dir=temp)) + 1
        desugared_library_dex_name = 'classes%s.dex' % desugared_library_dex_index
        desugared_library_dex_dst = os.path.join(temp,
                                                 desugared_library_dex_name)
        if is_archive(desugared_library_dex):
            zip_utils.extract_member(desugared_library_dex, 'classes.dex',
                                     desugared_library_dex_dst)
        else:
            shutil.copyfile(desugared_library_dex, desugared_library_dex_dst)

    # Insert the new dex and resource files from `processed_out` into the APK.
    with utils.ChangedWorkingDirectory(processed_out, quiet=quiet):
        dex_files = glob.glob('*.dex')
        dex_files.sort()
        resource_files = glob.glob(resources) if resources else []
        cmd = ['zip', '-u', '-0', processed_apk] + dex_files + resource_files
        utils.RunCmd(cmd, quiet=quiet, logging=logging)
    return processed_apk


def sign(unsigned_apk, keystore, temp, quiet, logging):
    signed_apk = os.path.join(temp, 'unaligned.apk')
    return apk_utils.sign_with_apksigner(unsigned_apk,
                                         signed_apk,
                                         keystore,
                                         quiet=quiet,
                                         logging=logging)


def align(signed_apk, temp, quiet, logging):
    utils.Print('Aligning', quiet=quiet)
    aligned_apk = os.path.join(temp, 'aligned.apk')
    return apk_utils.align(signed_apk, aligned_apk)


def masseur(apk,
            clear_profile=False,
            dex=None,
            desugared_library_dex=None,
            resources=None,
            out=None,
            adb_options=None,
            sign_before_align=False,
            keystore=None,
            install=False,
            quiet=False,
            logging=True):
    if not out:
        out = os.path.basename(apk)
    if not keystore:
        keystore = apk_utils.default_keystore()
    with utils.TempDir() as temp:
        processed_apk = None
        if dex or clear_profile:
            processed_apk = repack(apk, clear_profile, dex,
                                   desugared_library_dex, resources, temp,
                                   quiet, logging)
        else:
            assert not desugared_library_dex
            utils.Print('Signing original APK without modifying apk',
                        quiet=quiet)
            processed_apk = os.path.join(temp, 'processed.apk')
            shutil.copyfile(apk, processed_apk)
        if sign_before_align:
            signed_apk = sign(processed_apk,
                              keystore,
                              temp,
                              quiet=quiet,
                              logging=logging)
            aligned_apk = align(signed_apk, temp, quiet=quiet, logging=logging)
            utils.Print('Writing result to {}'.format(out), quiet=quiet)
            shutil.copyfile(aligned_apk, out)
        else:
            aligned_apk = align(processed_apk,
                                temp,
                                quiet=quiet,
                                logging=logging)
            signed_apk = sign(aligned_apk,
                              keystore,
                              temp,
                              quiet=quiet,
                              logging=logging)
            utils.Print('Writing result to {}'.format(out), quiet=quiet)
            shutil.copyfile(signed_apk, out)
        if install:
            adb_cmd = ['adb']
            if adb_options:
                adb_cmd.extend(
                    [option for option in adb_options.split(' ') if option])
            adb_cmd.extend(['install', '-t', '-r', '-d', out])
            utils.RunCmd(adb_cmd, quiet=quiet, logging=logging)


def main():
    (options, apk) = parse_options()
    masseur(apk, **vars(options))
    return 0


if __name__ == '__main__':
    sys.exit(main())
