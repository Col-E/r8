// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticClassMergerTest extends TestBase {

  static class StaticMergeCandidateA {

    @NeverInline
    public static String m() {
      return "StaticMergeCandidateA.m()";
    }
  }

  static class StaticMergeCandidateB {

    @NeverInline
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

  public StaticClassMergerTest(Backend backend) {
    this.backend = backend;
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  @Test
  public void testStaticClassIsRemoved() throws Exception {
    String expected =
        StringUtils.joinLines("StaticMergeCandidateA.m()", "StaticMergeCandidateB.m()");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(StaticClassMergerTest.class)
            .addKeepMainRule(TestClass.class)
            .addOptionsModification(options -> options.enableMinification = false)
            .enableInliningAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    // Check that one of the two static merge candidates has been removed
    List<FoundClassSubject> classes =
        inspector.allClasses().stream()
            .filter(clazz -> clazz.getOriginalName().contains("StaticMergeCandidate"))
            .collect(Collectors.toList());
    // TODO(b/117916645): Revert back to 1 here once the issue is fixed.
    assertEquals(2, classes.size());

    // Check that the remaining static merge candidate has two methods.
    FoundClassSubject remaining = classes.get(0);
    assertEquals(2, remaining.allMethods().size());
  }
}
