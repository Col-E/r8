// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.InternalCompilerError;
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
    try {
      testForR8(parameters.getBackend())
          .addInnerClasses(getClass())
          .addKeepMainRule(TestClass.class)
          .enableCoreLibraryDesugaring(parameters.getApiLevel())
          .enableNeverClassInliningAnnotations()
          .setMinApi(parameters.getApiLevel())
          .compile();
      assertTrue(parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
    } catch (CompilationFailedException e) {
      // TODO(b/170677722): Fix compilation failure.
      assertTrue(parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
      assertTrue(e.getCause() instanceof InternalCompilerError);
      assertThat(e.getCause().getMessage(), containsString("FORCE inlining on non-inlinable"));
    }
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
