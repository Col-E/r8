#!/usr/bin/env python3
# Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import sys

COMPANION_CLASS_SUFFIX = '$-CC'
EXTERNAL_SYNTHETIC_SUFFIX = '$$ExternalSynthetic'
SYNTHETIC_PREFIX = 'S'


# Parses a list of class and method descriptors, prefixed with one or more flags
# 'H' (hot), 'S' (startup), 'P' (post startup).
#
# Example:
#
# HSPLandroidx/compose/runtime/ComposerImpl;->updateValue(Ljava/lang/Object;)V
# HSPLandroidx/compose/runtime/ComposerImpl;->updatedNodeCount(I)I
# HLandroidx/compose/runtime/ComposerImpl;->validateNodeExpected()V
# PLandroidx/compose/runtime/CompositionImpl;->applyChanges()V
# HLandroidx/compose/runtime/ComposerKt;->findLocation(Ljava/util/List;I)I
# Landroidx/compose/runtime/ComposerImpl;
#
# See also https://developer.android.com/studio/profile/baselineprofiles.
def parse_art_profile(lines):
    art_profile = {}
    flags_to_name = {'H': 'hot', 'S': 'startup', 'P': 'post_startup'}
    for line in lines:
        line = line.strip()
        if not line:
            continue
        flags = {'hot': False, 'startup': False, 'post_startup': False}
        while line[0] in flags_to_name:
            flag_abbreviation = line[0]
            flag_name = flags_to_name.get(flag_abbreviation)
            flags[flag_name] = True
            line = line[1:]
        while line.startswith('['):
            line = line[1:]
        assert line.startswith('L'), line
        descriptor = line
        art_profile[descriptor] = flags
    return art_profile


def transform_art_profile_to_r8_startup_list(art_profile,
                                             generalize_synthetics=False):
    r8_startup_list = {}
    for startup_descriptor, flags in art_profile.items():
        transformed_startup_descriptor = transform_synthetic_descriptor(
            startup_descriptor) if generalize_synthetics else startup_descriptor
        r8_startup_list[transformed_startup_descriptor] = {
            'conditional_startup': False,
            'hot': flags['hot'],
            'startup': flags['startup'],
            'post_startup': flags['post_startup']
        }
    return r8_startup_list


def transform_synthetic_descriptor(descriptor):
    companion_class_index = descriptor.find(COMPANION_CLASS_SUFFIX)
    if companion_class_index >= 0:
        return SYNTHETIC_PREFIX + descriptor[0:companion_class_index] + ';'
    external_synthetic_index = descriptor.find(EXTERNAL_SYNTHETIC_SUFFIX)
    if external_synthetic_index >= 0:
        return SYNTHETIC_PREFIX + descriptor[0:external_synthetic_index] + ';'
    return descriptor


def filter_r8_startup_list(r8_startup_list, options):
    filtered_r8_startup_list = {}
    for startup_descriptor, flags in r8_startup_list.items():
        if not options.include_post_startup \
            and flags.get('post_startup') \
            and not flags.get('startup'):
            continue
        filtered_r8_startup_list[startup_descriptor] = flags
    return filtered_r8_startup_list


def parse_options(argv):
    result = argparse.ArgumentParser(
        description='Utilities for converting an ART profile into an R8 startup '
        'list.')
    result.add_argument('--art-profile', help='Path to the ART profile')
    result.add_argument(
        '--include-post-startup',
        help='Include post startup classes and methods in the R8 '
        'startup list',
        action='store_true',
        default=False)
    result.add_argument('--out', help='Where to store the R8 startup list')
    options, args = result.parse_known_args(argv)
    return options, args


def main(argv):
    (options, args) = parse_options(argv)
    with open(options.art_profile, 'r') as f:
        art_profile = parse_art_profile(f.read().splitlines())
    r8_startup_list = transform_art_profile_to_r8_startup_list(art_profile)
    filtered_r8_startup_list = filter_r8_startup_list(r8_startup_list, options)
    if options.out is not None:
        with open(options.out, 'w') as f:
            for startup_descriptor, flags in filtered_r8_startup_list.items():
                f.write(startup_descriptor)
                f.write('\n')
    else:
        for startup_descriptor, flags in filtered_r8_startup_list.items():
            print(startup_descriptor)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
