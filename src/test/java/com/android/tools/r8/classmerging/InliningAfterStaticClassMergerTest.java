// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test checks that the static class inliner does not merge classes in situations where a merge
 * operation could lead to a regression in code size because of reduced inlining opportunities.
 */
@RunWith(Parameterized.class)
public class InliningAfterStaticClassMergerTest extends TestBase {

  // A class that implements a library class.
  static class StaticMergeCandidateA implements Cloneable {

    // Cannot be inlined into TestClass.main() because the static initialization of this class could
    // have side-effects; in order for R8 to be conservative, library classes are treated as if
    // their static initialization could have side-effects.
    public static String m() {
      return "StaticMergeCandidateA.m()";
    }
  }

  static class StaticMergeCandidateB {

    // Can be inlined into TestClass.main() because the static initialization of this class has no
    // side-effects.
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

  private Backend backend;

  public InliningAfterStaticClassMergerTest(Backend backend) {
    this.backend = backend;
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  @Test
  public void testMethodsAreInlined() throws Exception {
    String expected =
        StringUtils.joinLines("StaticMergeCandidateA.m()", "StaticMergeCandidateB.m()");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addProgramClasses(
                InliningAfterStaticClassMergerTest.TestClass.class,
                InliningAfterStaticClassMergerTest.StaticMergeCandidateA.class,
                InliningAfterStaticClassMergerTest.StaticMergeCandidateB.class)
            .addKeepMainRule(TestClass.class)
            .addOptionsModification(options -> options.enableMinification = false)
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    // Check that StaticMergeCandidateB has been removed.
    List<FoundClassSubject> classes =
        inspector.allClasses().stream()
            .filter(clazz -> clazz.getOriginalName().contains("StaticMergeCandidate"))
            .collect(Collectors.toList());
    assertEquals(1, classes.size());
    assertEquals(StaticMergeCandidateA.class.getTypeName(), classes.get(0).getOriginalName());

    // Check that StaticMergeCandidateB.m() has not been moved into StaticMergeCandidateA, because
    // that would disable inlining of it.
    assertEquals(1, classes.get(0).allMethods().size());

    // Check that the test class only has a main method.
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertEquals(1, classSubject.allMethods().size());
  }
}
