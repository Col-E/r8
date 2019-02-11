#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import sys
import utils

JDK_DIR = os.path.join(utils.REPO_ROOT, 'third_party', 'openjdk')

def GetJdkHome():
  root = os.path.join(JDK_DIR, 'openjdk-9.0.4')
  if utils.IsLinux():
    return os.path.join(root, 'linux')
  elif utils.IsOsX():
    return os.path.join(root, 'osx')
  elif utils.IsWindows():
    return os.path.join(root, 'windows')
  else:
    return os.environ['JAVA_HOME']
  return jdkHome

def GetJavaExecutable(jdkHome=None):
  jdkHome = jdkHome if jdkHome else GetJdkHome()
  executable = 'java.exe' if utils.IsWindows() else 'java'
  return os.path.join(jdkHome, 'bin', executable) if jdkHome else executable

def GetJavacExecutable(jdkHome=None):
  jdkHome = jdkHome if jdkHome else GetJdkHome()
  executable = 'javac.exe' if utils.IsWindows() else 'javac'
  return os.path.join(jdkHome, 'bin', executable) if jdkHome else executable

def EnsureJdk():
  jdkHome = GetJdkHome()
  jdkTgz = jdkHome + '.tar.gz'
  jdkSha1 = jdkTgz + '.sha1'
  utils.EnsureDepFromGoogleCloudStorage(jdkHome, jdkTgz, jdkSha1, 'JDK')

def Main():
  print GetJdkHome()

if __name__ == '__main__':
  sys.exit(Main())
