#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from distutils.version import LooseVersion
import os
import shutil

import utils

def add_r8_dependency(checkout_dir, minified):
  build_file = os.path.join(checkout_dir, 'build.gradle')
  assert os.path.isfile(build_file), 'Expected a file to be present at {}'.format(build_file)

  with open(build_file) as f:
    lines = f.readlines()

  added_r8_dependency = False
  is_inside_dependencies = False

  with open(build_file, 'w') as f:
    gradle_version = None
    for line in lines:
      stripped = line.strip()
      if stripped == 'dependencies {':
        assert not is_inside_dependencies, 'Unexpected line with \'dependencies {\''
        is_inside_dependencies = True
      if is_inside_dependencies:
        if utils.R8_JAR in stripped:
          if minified:
            # Skip line to avoid dependency on r8.jar
            continue
          added_r8_dependency = True
        elif utils.R8LIB_JAR in stripped:
          if not minified:
            # Skip line to avoid dependency on r8lib.jar
            continue
          added_r8_dependency = True
        elif 'com.android.tools.build:gradle:' in stripped:
          gradle_version = stripped[stripped.rindex(':')+1:-1]
          if not added_r8_dependency:
            indent = ''.ljust(line.index('classpath'))
            jar = utils.R8LIB_JAR if minified else utils.R8_JAR
            f.write('{}classpath files(\'{}\')\n'.format(indent, jar))
            added_r8_dependency = True
        elif stripped == '}':
          is_inside_dependencies = False
      f.write(line)

  assert added_r8_dependency, 'Unable to add R8 as a dependency'
  assert gradle_version
  assert LooseVersion(gradle_version) >= LooseVersion('3.2'), (
      'Unsupported gradle version: {} (must use at least gradle '
          + 'version 3.2)').format(gradle_version)

def remove_r8_dependency(checkout_dir):
  build_file = os.path.join(checkout_dir, 'build.gradle')
  assert os.path.isfile(build_file), 'Expected a file to be present at {}'.format(build_file)
  with open(build_file) as f:
    lines = f.readlines()
  with open(build_file, 'w') as f:
    for line in lines:
      if (utils.R8_JAR not in line) and (utils.R8LIB_JAR not in line):
        f.write(line)

def Move(src, dst):
  print('Moving `{}` to `{}`'.format(src, dst))
  dst_parent = os.path.dirname(dst)
  if not os.path.isdir(dst_parent):
    os.makedirs(dst_parent)
  elif os.path.isdir(dst):
    shutil.rmtree(dst)
  elif os.path.isfile(dst):
    os.remove(dst)
  os.rename(src, dst)

def MoveDir(src, dst):
  assert os.path.isdir(src)
  Move(src, dst)

def MoveFile(src, dst):
  assert os.path.isfile(src)
  Move(src, dst)

def MoveProfileReportTo(dest_dir, build_stdout):
  html_file = None
  profile_message = 'See the profiling report at: '
  for line in build_stdout:
    if profile_message in line:
      html_file = line[len(profile_message):]
      if html_file.startswith('file://'):
        html_file = html_file[len('file://'):]
      break

  if not html_file:
    return

  assert os.path.isfile(html_file), 'Expected to find HTML file at {}'.format(
      html_file)
  MoveFile(html_file, os.path.join(dest_dir, 'index.html'))

  html_dir = os.path.dirname(html_file)
  for dir_name in ['css', 'js']:
    MoveDir(os.path.join(html_dir, dir_name), os.path.join(dest_dir, dir_name))
