// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import org.junit.runners.Parameterized.Parameters;

public abstract class VerticalClassMergerTestBase extends TestBase {

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VerticalClassMergerTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static void assertMergedIntoSubtype(
      Class<?> clazz,
      DexItemFactory dexItemFactory,
      VerticallyMergedClasses verticallyMergedClasses) {
    assertTrue(verticallyMergedClasses.hasBeenMergedIntoSubtype(toDexType(clazz, dexItemFactory)));
  }
}
