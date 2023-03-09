// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.annotations.testclasses;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@NotNullTestRuntime
@NotNullTestClass
public class TestClassWithTypeAndGenericAnnotations<@NotNullTestRuntime @NotNullTestClass T>
    extends @NotNullTestRuntime @NotNullTestClass Object
    implements @NotNullTestRuntime @NotNullTestClass SuperInterface<
        @NotNullTestRuntime @NotNullTestClass T> {

  @NotNullTestRuntime @NotNullTestClass
  List<@NotNullTestRuntime @NotNullTestClass Object> field = null;

  @NotNullTestRuntime
  @NotNullTestClass
  <@NotNullTestRuntime @NotNullTestClass S>
      List<@NotNullTestRuntime @NotNullTestClass Object> method(
          @NotNullTestRuntime @NotNullTestClass int foo,
          @NotNullTestRuntime @NotNullTestClass
              List<@NotNullTestRuntime @NotNullTestClass String> bar,
          S s)
          throws @NotNullTestClass @NotNullTestRuntime RuntimeException,
              @NotNullTestClass @NotNullTestRuntime IOException {
    @NotNullTestRuntime
    @NotNullTestClass
    Object local = System.currentTimeMillis() > 0 ? new Object() : foo;
    ArrayList<@NotNullTestRuntime @NotNullTestClass Object> objects = new ArrayList<>();
    objects.add(foo);
    this.field = objects;
    return objects;
  }
}
