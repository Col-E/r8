// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b72485384;

import java.util.function.Function;

public class Main {

  public static void main(String[] args) {
    GenericOuter<String> outer = new GenericOuter<>();
    GenericOuter<String>.GenericInner<String> inner =
        outer.makeInner(Function.identity(), "Hello World!");
    System.out.println(inner.innerGetter(inner));
    System.out.println(outer.outerGetter(inner));
  }
}
