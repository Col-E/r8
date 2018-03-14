// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import java.util.function.Function;

public class LambdaTest {
  public static void main(String[] args) {
    twicePrint(args.length, i -> i + 21);
  }

  private static void twicePrint(int v, Function<Integer, Integer> f) {
    System.out.println(f.andThen(f).apply(v));
  }
}
