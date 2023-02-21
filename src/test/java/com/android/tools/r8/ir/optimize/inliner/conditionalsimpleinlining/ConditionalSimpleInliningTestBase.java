// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ConditionalSimpleInliningTestBase extends TestBase {

  protected final boolean enableSimpleInliningConstraints;
  protected final TestParameters parameters;

  @Parameters(name = "{1}, simple inlining constraints: {0}")
  public static Iterable<?> data() {
    return buildParameters(
        BooleanUtils.values(), TestBase.getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public ConditionalSimpleInliningTestBase(
      boolean enableSimpleInliningConstraints, TestParameters parameters) {
    this.enableSimpleInliningConstraints = enableSimpleInliningConstraints;
    this.parameters = parameters;
  }

  public void configure(R8FullTestBuilder testBuilder) {
    testBuilder.addOptionsModification(this::enableSimpleInliningConstraints).setMinApi(parameters);
  }

  private void enableSimpleInliningConstraints(InternalOptions options) {
    options.enableSimpleInliningConstraints = enableSimpleInliningConstraints;
  }
}
