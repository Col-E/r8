// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.abstractmethodremoval;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.abstractmethodremoval.a.PackageBase;
import com.android.tools.r8.examples.abstractmethodremoval.a.Public;
import com.android.tools.r8.examples.abstractmethodremoval.b.Impl1;
import com.android.tools.r8.examples.abstractmethodremoval.b.Impl2;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AbstractMethodRemovalTestRunner extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractMethodRemovalTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Class<?> getMainClass() {
    return AbstractMethodRemoval.class;
  }

  private List<Class<?>> getProgramClasses() {
    return ImmutableList.of(
        getMainClass(), Public.class, PackageBase.class, Impl1.class, Impl2.class);
  }

  private String getExpected() {
    return StringUtils.lines(
        "Impl1.foo(0)",
        "Impl2.foo(0)",
        "Impl2.foo(0)",
        "Impl1.foo(0)",
        "Impl2.foo(0)",
        "Impl2.foo(0)");
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getProgramClasses())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addKeepMainRule(getMainClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpected());
  }
}
