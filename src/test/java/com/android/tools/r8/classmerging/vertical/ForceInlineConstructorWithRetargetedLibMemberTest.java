// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ForceInlineConstructorWithRetargetedLibMemberTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ForceInlineConstructorWithRetargetedLibMemberTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // Regression test for b/170677722.
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class))
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), not(isPresent()));
              assertThat(inspector.clazz(B.class), isPresent());
            });
  }

  static class TestClass {

    public static void main(String[] args) {
      new B(args);
    }
  }

  static class A {

    A(String[] args) {
      Arrays.stream(args);
    }
  }

  @NeverClassInline
  static class B extends A {

    B(String[] args) {
      super(args);
    }
  }
}
