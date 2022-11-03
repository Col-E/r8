// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.testsource;

import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepTarget;

@KeepEdge(
    consequences = {
      // Keep the class to allow lookup of it.
      @KeepTarget(classConstant = KeepClassAndDefaultConstructorSource.class),
      // Keep the default constructor.
      @KeepTarget(classConstant = KeepClassAndDefaultConstructorSource.class, methodName = "<init>")
    })
public class KeepClassAndDefaultConstructorSource {

  public static class A {
    public A() {
      System.out.println("A is alive!");
    }
  }

  public static void main(String[] args) throws Exception {
    Class<?> aClass =
        Class.forName(
            KeepClassAndDefaultConstructorSource.class.getPackage().getName()
                + (args.length > 0 ? ".." : ".")
                + "KeepClassAndDefaultConstructorSource$A");
    aClass.getDeclaredConstructor().newInstance();
  }
}
