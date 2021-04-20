// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature.testclasses;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;

@NeverClassInline
public class Foo<T extends Comparable<T>> implements J, K<T> {

  @Override
  @NeverInline
  public String bar(String t) {
    System.out.println("Foo::bar");
    return t;
  }
}
