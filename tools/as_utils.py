#!/usr/bin/env python
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from distutils.version import LooseVersion
import os

import utils

def add_r8_dependency(checkout_dir):
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
          added_r8_dependency = True
        elif 'com.android.tools.build:gradle:' in stripped:
          gradle_version = stripped[stripped.rindex(':')+1:-1]
          if not added_r8_dependency:
            indent = ''.ljust(line.index('classpath'))
            f.write('{}classpath files(\'{}\')\n'.format(indent, utils.R8_JAR))
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
      if utils.R8_JAR not in line:
        f.write(line)
