#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

'''
Compare the R8 API used by the API usage sample to the API kept by @Keep.
'''

import argparse
import os
import subprocess
import utils

parser = argparse.ArgumentParser(description=__doc__.strip(),
                                 formatter_class=argparse.RawTextHelpFormatter)
parser.add_argument('-o', '--output-dir')

API_SAMPLE_JAR = 'tests/d8_api_usage_sample.jar'


def main(output_dir=None):
  if output_dir is None:
    output_dir = ''

  printseeds_path = os.path.join(output_dir, 'keep-seeds.txt')
  printseeds_args = [
    'java', '-jar', utils.R8_JAR, 'printseeds',
    utils.RT_JAR, utils.R8_JAR, utils.R8LIB_KEEP_RULES,
  ]
  write_sorted_lines(printseeds_args, printseeds_path)

  printuses_path = os.path.join(output_dir, 'sample-uses.txt')
  printuses_args = [
    'java', '-jar', utils.R8_JAR, 'printuses',
    utils.RT_JAR, utils.R8_JAR, API_SAMPLE_JAR,
  ]
  write_sorted_lines(printuses_args, printuses_path)

  print_diff(printseeds_path, printuses_path)


def write_sorted_lines(cmd_args, output_path):
  utils.PrintCmd(cmd_args)
  output_lines = subprocess.check_output(cmd_args).splitlines(True)
  print("Write output to %s" % output_path)
  output_lines.sort()
  with open(output_path, 'w') as fp:
    for line in output_lines:
      fp.write(line)


def print_diff(printseeds_path, printuses_path):
  with open(printseeds_path) as fp:
    seeds = set(fp.read().splitlines())
  with open(printuses_path) as fp:
    uses = set(fp.read().splitlines())
  only_in_seeds = seeds - uses
  only_in_uses = uses - seeds
  if only_in_seeds:
    print("%s lines with '-' are marked @Keep " % len(only_in_seeds) +
          "but not used by sample.")
  if only_in_uses:
    print("%s lines with '+' are used by sample " % len(only_in_uses) +
          "but are missing @Keep annotations.")
  for line in sorted(only_in_seeds):
    print('-' + line)
  for line in sorted(only_in_uses):
    print('+' + line)
  if not only_in_seeds and not only_in_uses:
    print('Sample uses the entire set of members marked @Keep. Well done!')


if __name__ == '__main__':
  main(**vars(parser.parse_args()))
