#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import defines
import os
import sys

JDK_DIR = os.path.join(defines.THIRD_PARTY, 'openjdk')

def GetJdkHome():
  root = os.path.join(JDK_DIR, 'openjdk-9.0.4')
  if defines.IsLinux():
    return os.path.join(root, 'linux')
  elif defines.IsOsX():
    return os.path.join(root, 'osx')
  elif defines.IsWindows():
    return os.path.join(root, 'windows')
  else:
    return os.environ['JAVA_HOME']

def GetJdk8Home():
  root = os.path.join(JDK_DIR, 'jdk8')
  if defines.IsLinux():
    return os.path.join(root, 'linux-x86')
  elif defines.IsOsX():
    return os.path.join(root, 'darwin-x86')
  else:
    return os.environ['JAVA_HOME']

def GetJavaExecutable(jdkHome=None):
  jdkHome = jdkHome if jdkHome else GetJdkHome()
  executable = 'java.exe' if defines.IsWindows() else 'java'
  return os.path.join(jdkHome, 'bin', executable) if jdkHome else executable

def GetJavacExecutable(jdkHome=None):
  jdkHome = jdkHome if jdkHome else GetJdkHome()
  executable = 'javac.exe' if defines.IsWindows() else 'javac'
  return os.path.join(jdkHome, 'bin', executable) if jdkHome else executable

def Main():
  print GetJdkHome()

if __name__ == '__main__':
  sys.exit(Main())
