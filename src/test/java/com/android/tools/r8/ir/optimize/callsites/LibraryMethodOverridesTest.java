// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class LibraryMethodOverridesTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        // java.util.function.Predicate is not available prior to API level 24 (V7.0).
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LibClass.class)
            .addKeepClassAndMembersRules(LibClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, CustomPredicate.class)
        .addClasspathClasses(LibClass.class)
        .addKeepMainRule(MAIN)
        .addOptionsModification(
            o ->
                o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect)
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("Live", "Also live")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    assert false : "Unexpected revisit: " + method.toSourceString();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject customPredicate = inspector.clazz(CustomPredicate.class);
    assertThat(customPredicate, isPresent());

    MethodSubject m = customPredicate.uniqueMethodWithOriginalName("test");
    // Should not optimize branches since the method is overriding a library method.
    assertTrue(m.streamInstructions().anyMatch(InstructionSubject::isIf));
  }

  static class TestClass {
    public static void main(String[] args) {
      CustomPredicate predicate = new CustomPredicate();
      // calls CustomPredicate#test with non-null arg.
      predicate.test(new Object());
      // escapes to library
      LibClass.spuriousAccess(predicate);
    }
  }

  static class LibClass {
    public static void spuriousAccess(Predicate<Object> p) {
      // calls CustomPredicate#test with null arg.
      p.test(null);
    }
  }

  static class CustomPredicate implements Predicate<Object> {
    @Override
    public boolean test(Object o) {
      if (o != null) {
        live();
        return true;
      } else {
        alsoLive();
        return false;
      }
    }

    @NeverInline
    @NoMethodStaticizing
    private void live() {
      System.out.println("Live");
    }

    @NeverInline
    @NoMethodStaticizing
    private void alsoLive() {
      System.out.println("Also live");
    }
  }
}
