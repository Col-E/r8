// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepClassMembersFieldTest extends TestBase {

  private static final String KEEP_RULE =
      StringUtils.lines(
          "-keepclassmembers,allowshrinking class " + Foo.class.getTypeName() + " {",
          "  <fields>;",
          "}");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepClassMembersFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Foo.class)
        .addKeepRules(KEEP_RULE)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(Foo.class).uniqueFieldWithOriginalName("value"), isPresent()))
        .run(parameters.getRuntime(), Foo.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Bar {}

  static class Foo {

    Bar value = new Bar();

    public static void main(String[] args) {
      new Foo();
    }
  }
}
