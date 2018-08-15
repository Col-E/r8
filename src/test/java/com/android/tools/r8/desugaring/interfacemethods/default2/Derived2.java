// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods.default2;

import java.util.Comparator;

public class Derived2 implements DerivedComparator2<String> {
  @Override
  public int compare(String a, String b) {
    return a.compareTo(b);
  }

  @Override
  public Comparator<String> reversed() {
    return TestMainDefault2.shouldCallStaticOnInterface
        ? DerivedComparator2.super.reversed().reversed() : this;
  }
}
