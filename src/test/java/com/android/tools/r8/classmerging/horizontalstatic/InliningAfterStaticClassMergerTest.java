// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontalstatic;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test checks that the static class inliner does not merge classes in situations where a merge
 * operation could lead to a regression in code size because of reduced inlining opportunities.
 */
@RunWith(Parameterized.class)
public class InliningAfterStaticClassMergerTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.joinLines("StaticMergeCandidateA.m()", "StaticMergeCandidateB.m()");

  // A class that implements a library class.
  static class StaticMergeCandidateA implements Cloneable {

    // Cannot be inlined into TestClass.main() because the static initialization of this class could
    // have side-effects; in order for R8 to be conservative, library classes are treated as if
    // their static initialization could have side-effects.
    @NeverPropagateValue
    public static String m() {
      return "StaticMergeCandidateA.m()";
    }
  }

  static class StaticMergeCandidateB {

    // Can be inlined into TestClass.main() because the static initialization of this class has no
    // side-effects.
    @NeverPropagateValue
    public static String m() {
      return "StaticMergeCandidateB.m()";
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(StaticMergeCandidateA.m());
      System.out.print(StaticMergeCandidateB.m());
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testMethodsAreInlined() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                TestClass.class, StaticMergeCandidateA.class, StaticMergeCandidateB.class)
            .addKeepMainRule(TestClass.class)
            .addOptionsModification(
                options -> options.libraryInterfacesMayHaveStaticInitialization = true)
            .enableMemberValuePropagationAnnotations()
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    // Check that StaticMergeCandidateB has been removed.
    List<FoundClassSubject> classes =
        inspector.allClasses().stream()
            .filter(clazz -> clazz.getOriginalName().contains("StaticMergeCandidate"))
            .collect(Collectors.toList());
    assertEquals(1, classes.size());

    FoundClassSubject clazz = classes.get(0);
    assertEquals(StaticMergeCandidateA.class.getTypeName(), clazz.getOriginalName());

    // Check that StaticMergeCandidateB.m() has been inlined.
    assertEquals(0, clazz.allMethods().size());

    // Check that a static field has been synthesized in order to trigger class initialization.
    assertEquals(1, clazz.allStaticFields().size());
    assertEquals("$r8$clinit", clazz.allStaticFields().get(0).getOriginalName());

    // Check that the test class only has a main method.
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertEquals(1, classSubject.allMethods().size());
  }
}
