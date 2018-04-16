// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir;

public class PhiDefinitionsTest {

  public static Class[] CLASSES = {
      PhiDefinitionsTest.class,
      MethodWriter.class,
  };

  static class MethodWriter {
    public int exceptionCount;
  }

  public static void main(String[] args) {
    if (args.length >= 42) {
      System.out.println(new PhiDefinitionsTest().readMethod(args.length));
    }
  }

  private int readMethod(int u) {
    u += 6;
    int exception = 0;
    for (int i = count(u); i > 0; --i) {
      exception = count(u);
      for (int j = count(); j > 0; --j) {
        read(exception);
        exception += 2;
      }
      u += 6 + count(u + 4);
    }
    u += 2;
    MethodWriter mv = visitMethod();
    if (cond() && cond() && cond()) {
      MethodWriter mw = mv;
      boolean sameExceptions = false;
      if (count() == mw.exceptionCount) {
        sameExceptions = true;
        for (int j = count(); j >= 0; --j) {
          exception -= 2;
        }
      }
      if (cond()) {
        return u;
      }
    }
    return u;
  }

  private native MethodWriter visitMethod();

  private native boolean cond();

  private native String read(int i);

  private native int count(int arg);

  private native int count();
}
