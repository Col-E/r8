#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import sys

import defines

JDK_DIR = os.path.join(defines.THIRD_PARTY, 'openjdk')

ALL_JDKS = [
    'openjdk-9.0.4', 'jdk-11', 'jdk-15', 'jdk-16', 'jdk-17', 'jdk-18', 'jdk-20'
]


def GetJdkHome():
    return GetJdk11Home()


def GetJdkRoot():
    return GetJdk11Root()


def GetJdk11Root():
    root = os.path.join(JDK_DIR, 'jdk-11')
    os_root = GetOSPath(root)
    return os_root if os_root else os.environ['JAVA_HOME']


def GetOSPath(root):
    if defines.IsLinux():
        return os.path.join(root, 'linux')
    elif defines.IsOsX():
        return os.path.join(root, 'osx')
    elif defines.IsWindows():
        return os.path.join(root, 'windows')
    else:
        return None


def GetAllJdkDirs():
    dirs = []
    for jdk in ALL_JDKS:
        root = GetOSPath(os.path.join(JDK_DIR, jdk))
        # Some jdks are not available on windows, don't try to get these.
        if os.path.exists(root + '.tar.gz.sha1'):
            dirs.append(root)
    return dirs


def GetJdk11Home():
    root = GetJdk11Root()
    # osx has the home inside Contents/Home in the bundle
    if defines.IsOsX():
        return os.path.join(root, 'Contents', 'Home')
    else:
        return root


def GetJdk9Home():
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
    print(GetJdkHome())


if __name__ == '__main__':
    sys.exit(Main())
