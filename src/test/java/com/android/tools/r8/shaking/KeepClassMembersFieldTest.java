// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepClassMembersFieldTest extends TestBase {

  private static final String KEEP_RULE =
      "-keepclassmembers,allowshrinking class"
          + " com.android.tools.r8.shaking.KeepClassMembersFieldTest$Foo {"
          + " <fields>; "
          + "}";
  private static final String EXPECTED_RESULT = StringUtils.lines("42");

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
        .addProgramClasses(Foo.class, Bar.class)
        .addKeepMainRule(Foo.class)
        .addKeepRules(KEEP_RULE)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Foo.class)
        .inspect(
            inspector -> {
              List<FoundFieldSubject> foundFieldSubjects = inspector.clazz(Foo.class).allFields();
              // TODO(b/231555675): This should present
              assertFalse(inspector.clazz(Foo.class).uniqueFieldWithName("value").isPresent());
            })
        .assertSuccess();
  }

  static class Bar {
    @Override
    public String toString() {
      return "42";
    }
  }

  static class Foo {
    public Bar value = new Bar();

    Foo() {}

    public static void main(String[] args) {
      new Foo();
    }
  }
}
