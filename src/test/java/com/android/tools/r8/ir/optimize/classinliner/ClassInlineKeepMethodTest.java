// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlineKeepMethodTest extends TestBase {

  private final TestParameters parameters;
  private static final String EXPECTED_OUTPUT = "Hello world";

  @NoVerticalClassMerging
  public static class ShouldBeKept {

    @NeverInline
    public void shouldBeKept() {
      System.out.print("Hello world");
    }
  }

  public static class Keeper {

    public static void main(String[] args) {
      new ShouldBeKept().shouldBeKept();
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntime(Version.last()).build();
  }

  public ClassInlineKeepMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Ignore("b/129390240")
  @Test
  public void testIsKeptWithName()
      throws ExecutionException, CompilationFailedException, IOException {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ClassInlineKeepMethodTest.class)
            .addKeepMainRule(Keeper.class)
            .addKeepClassAndMembersRules(ShouldBeKept.class)
            .enableInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .run(parameters.getRuntime(), Keeper.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();
    ClassSubject clazz = inspector.clazz(Keeper.class);
    assertThat(clazz, isPresent());
    MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
    assertTrue(main.streamInstructions().noneMatch(i -> i.isConstString(JumboStringMode.ALLOW)));
  }
}
