// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentsCollisionMappingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedArgumentsCollisionMappingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class)
            .setMinApi(parameters)
            .addKeepMainRule(Main.class)
            .enableConstantArgumentAnnotations()
            .enableInliningAnnotations()
            .addKeepAttributeLineNumberTable()
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("test with unused", "test with used: foo")
            .inspect(this::verifyArgumentsRemoved);
    assertEquals(
        1,
        StringUtils.splitLines(runResult.proguardMap()).stream()
            .filter(line -> line.contains("void test(java.lang.String,java.lang.String)"))
            .count());
    assertEquals(
        1,
        StringUtils.splitLines(runResult.proguardMap()).stream()
            .filter(line -> line.contains("void test(java.lang.String)"))
            .count());
  }

  private void verifyArgumentsRemoved(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(Main.class);
    assertThat(main, isPresent());
    List<FoundMethodSubject> foundMethodSubjects = main.allMethods();
    assertEquals(3, foundMethodSubjects.size());
    boolean foundZeroArgumentMethod = false;
    boolean foundOneArgumentMethod = false;
    for (FoundMethodSubject method : foundMethodSubjects) {
      if (method.getFinalName().equals("main")) {
        continue;
      }
      foundZeroArgumentMethod |= method.getMethod().getParameters().size() == 0;
      foundOneArgumentMethod |= method.getMethod().getParameters().size() == 1;
    }
    assertTrue(foundZeroArgumentMethod);
    assertTrue(foundOneArgumentMethod);
  }

  public static class Main {

    @NeverInline
    public static void test(String unused) {
      System.out.println("test with unused");
    }

    @KeepConstantArguments
    @NeverInline
    public static void test(String used, String unused) {
      System.out.println("test with used: " + used);
    }

    public static void main(String[] args) {
      test("foo");
      test("foo", "notused");
    }
  }
}
