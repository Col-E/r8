// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize.invokeinterface;

import java.util.ArrayList;
import java.util.List;

public class Main {
  private static final int COUNT = 8;

  public static void main(String[] args) {
    I instance = new A0();
    List<I> l = new ArrayList<>();
    for (int i = 0; i < COUNT; i++) {
      l.add(instance);
    }

    int sum = 0;
    for (int i = 0; i < COUNT; i++) {
      sum += l.get(i).get();
    }
    System.out.println(sum);
  }
}
