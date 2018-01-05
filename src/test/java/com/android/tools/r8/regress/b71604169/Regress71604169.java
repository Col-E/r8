// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b71604169;

public class Regress71604169 {
  public interface Creator<C> {
    C create(Object o);
  }

  public static class X {
    Object o;

    X(Object o) {
      this.o = o;
      System.out.print(o);
    }
  }

  public static <C> C create(Creator<C> creator)  {
    return creator.create("Hello, world!");
  }

  public static void main(String[] args) {
    create(X::new);
  }
}
