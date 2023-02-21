// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.classinliner.nonpublicsubtype;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.classinliner.nonpublicsubtype.subpkg.Utils;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlineNonPublicSubtypeTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlineNonPublicSubtypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .addProgramClassesAndInnerClasses(Utils.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addInnerClasses(getClass())
        .addProgramClassesAndInnerClasses(Utils.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public interface I {
    @NeverInline
    void method();
  }

  static class Accessor {
    @NeverInline
    static void access(I i) {
      i.method();
    }
  }

  static class TestClass {

    static void run() {
      Accessor.access(Utils.INSTANCE);
    }

    public static void main(String[] args) {
      run();
    }
  }
}
