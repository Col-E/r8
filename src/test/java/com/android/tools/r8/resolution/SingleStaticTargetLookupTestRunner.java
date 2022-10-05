// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SingleStaticTargetLookupTestRunner extends TestBase {

  static final String EXPECTED = StringUtils.lines("foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public SingleStaticTargetLookupTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  public <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      void test(boolean keepFoo, TestShrinkerBuilder<C, B, CR, RR, T> builder) throws Exception {
    builder
        .addProgramClassesAndInnerClasses(SingleStaticTargetLookupTest.class)
        .addKeepMainRule(SingleStaticTargetLookupTest.class)
        .apply(
            b -> {
              if (keepFoo) {
                b.addKeepMethodRules(
                    methodFromMethod(SingleStaticTargetLookupTest.class.getMethod("staticFoo")));
              }
            })
        .run(parameters.getRuntime(), SingleStaticTargetLookupTest.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(SingleStaticTargetLookupTest.class);
              MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
              MethodSubject staticFoo = clazz.uniqueMethodWithOriginalName("staticFoo");
              assertThat(clazz, isPresent());
              assertThat(main, isPresent());
              assertEquals(keepFoo, staticFoo.isPresent());
              assertEquals(
                  keepFoo, main.streamInstructions().anyMatch(InstructionSubject::isInvokeStatic));
            });
  }

  @Test
  public void testInlinedPG() throws Exception {
    test(false, testForProguard());
  }

  @Test
  public void testKeptPG() throws Exception {
    test(true, testForProguard());
  }

  @Test
  public void testInlinedR8() throws Exception {
    test(false, testForR8(parameters.getBackend()));
  }

  @Test
  public void testKeptR8() throws Exception {
    test(true, testForR8(parameters.getBackend()));
  }
}
