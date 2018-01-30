// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package classloader;

import java.lang.reflect.Method;

import dalvik.system.PathClassLoader;

// Command line application which take three arguments:
//
//  Parent dex file
//  Child dex file
//  Main class name
//
// The application will create a classloader hierachy with the parent dex file above the
// system class loader, and the child dex file above the parent dex file. The it will load the
// Main class from the child dex file class loader and run its main method.
public class Runner {
  public static void main(String[] args) throws Exception {
    String parentFile = args[0];
    String childFile = args[1];
    String childClassName = args[2];
    ClassLoader parentClassLoader =
        new PathClassLoader(parentFile, ClassLoader.getSystemClassLoader());
    ClassLoader childClassLoader = new PathClassLoader(childFile, parentClassLoader);

    Class<?> childClass = childClassLoader.loadClass(childClassName);
    runMain(childClass, new String[0]);
  }

  private static void runMain(Class<?> clazz, String[] args) throws Exception {
    Method m = clazz.getMethod("main", String[].class);
    m.invoke(null, new Object[] { args });
  }
}
