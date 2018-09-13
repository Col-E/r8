#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script for checking impact of a change by comparing the sizes of generated
# classes in an apk.

import glob
import optparse
import os
import shutil
import sys
import toolhelper
import utils
import zipfile
import StringIO

USAGE = """%prog [options] app1 app2
  NOTE: This only makes sense if minification is disabled"""

def parse_options():
  result = optparse.OptionParser(usage=USAGE)
  result.add_option('--temp',
                    help='Temporary directory to store extracted classes in')
  result.add_option('--report',
                    help='Print comparison to this location instead of stdout')
  return result.parse_args()

def extract_apk(apk, output):
  if os.path.exists(output):
    shutil.rmtree(output)
  zipfile.ZipFile(apk).extractall(output)
  with utils.ChangedWorkingDirectory(output):
    dex = glob.glob('*.dex')
    return [os.path.join(output, dexfile) for dexfile in dex]

def ensure_exists(files):
  for f in files:
    if not os.path.exists(f):
      raise Exception('%s does not exist')

def extract_classes(input, output):
  if os.path.exists(output):
    shutil.rmtree(output)
  os.makedirs(output)
  args = ['--file-per-class',
          '--output', output]
  args.extend(input)
  if toolhelper.run('d8', args) is not 0:
    raise Exception('Failed running d8')

class FileInfo:
  def __init__(self, path, root):
    self.path = path
    self.full_path = os.path.join(root, path)
    self.size = os.path.getsize(self.full_path)

def generate_file_info(path):
  file_info_map = {}
  with utils.ChangedWorkingDirectory(path):
    for root, dirs, files in os.walk('.'):
      for f in files:
        assert f.endswith('dex')
        file_path = os.path.join(root, f)
        entry = FileInfo(file_path, path)
        file_info_map[file_path] = entry
  return file_info_map

def print_info(app, app_files, only_in_app, bigger_in_app, output):
  output.write('Only in %s\n' % app)
  only_app_sorted = sorted(only_in_app,
                           key=lambda a: app_files[a].size,
                           reverse=True)
  output.write('\n'.join(['  %s %s bytes' %
                          (x, app_files[x].size) for x in only_app_sorted]))
  output.write('\n\n')
  output.write('Bigger in %s\n' % app)
  # Sort by the percentage diff compared to size
  percent = lambda a: (0.0 + bigger_in_app.get(a))/app_files.get(a).size * 100
  for bigger in sorted(bigger_in_app, key=percent, reverse=True):
    output.write('  {0:.3f}% {1} bytes {2}\n'.format(percent(bigger),
                                                     bigger_in_app[bigger],
                                                     bigger))
  output.write('\n\n')


def compare(app1_classes_dir, app2_classes_dir, app1, app2, report):
  app1_files = generate_file_info(app1_classes_dir)
  app2_files = generate_file_info(app2_classes_dir)
  only_in_app1 = [k for k in app1_files if k not in app2_files]
  only_in_app2 = [k for k in app2_files if k not in app1_files]
  in_both = [k for k in app2_files if k in app1_files]
  assert len(app1_files) == len(only_in_app1) + len(in_both)
  assert len(app2_files) == len(only_in_app2) + len(in_both)
  bigger_in_app1 = {}
  bigger_in_app2 = {}
  same_size = []
  for f in in_both:
    app1_entry = app1_files[f]
    app2_entry = app2_files[f]
    if app1_entry.size > app2_entry.size:
      bigger_in_app1[f] = app1_entry.size - app2_entry.size
    elif app2_entry.size > app1_entry.size:
      bigger_in_app2[f] = app2_entry.size - app1_entry.size
    else:
      same_size.append(f)
  output = open(report, 'w') if report else sys.stdout
  print_info(app1, app1_files, only_in_app1, bigger_in_app1, output)
  print_info(app2, app2_files, only_in_app2, bigger_in_app2, output)
  output.write('Same size\n')
  output.write('\n'.join(['  %s' % x for x in same_size]))
  if report:
    output.close()

def Main():
  (options, args) = parse_options()
  if len(args) is not 2:
    print args
    print('Takes exactly two arguments, the two apps to compare')
    return 1
  app1 = args[0]
  app2 = args[1]
  ensure_exists([app1, app2])
  with utils.TempDir() as temporary:
    # If a temp dir is passed in, use that instead of the generated temporary
    output = options.temp if options.temp else temporary
    ensure_exists([output])
    app1_input = [app1]
    app2_input = [app2]
    if app1.endswith('apk'):
      app1_input = extract_apk(app1, os.path.join(output, 'app1'))
    if app2.endswith('apk'):
      app2_input = extract_apk(app2, os.path.join(output, 'app2'))
    app1_classes_dir = os.path.join(output, 'app1_classes')
    app2_classes_dir = os.path.join(output, 'app2_classes')

    extract_classes(app1_input, app1_classes_dir)
    extract_classes(app2_input, app2_classes_dir)
    compare(app1_classes_dir, app2_classes_dir, app1, app2, options.report)

if __name__ == '__main__':
  sys.exit(Main())
