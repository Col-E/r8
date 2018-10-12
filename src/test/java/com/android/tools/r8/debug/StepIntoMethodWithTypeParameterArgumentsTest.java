// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import java.util.Collection;
import java.util.List;

public class StepIntoMethodWithTypeParameterArgumentsTest {

  public static List<Object> field = null;

  public static void foo(List<String> strings) {
    Collection<Object> objects = field;
  }

  public static void main(String[] args) {
    foo(null);
  }
}
