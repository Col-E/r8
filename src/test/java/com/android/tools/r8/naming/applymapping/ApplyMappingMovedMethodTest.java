// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MemberSubject;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/269178203. */
@RunWith(Parameterized.class)
public class ApplyMappingMovedMethodTest extends TestBase {

  private static final String mapping =
      StringUtils.lines(
          "com.android.tools.r8.naming.applymapping.ApplyMappingMovedMethodTest$One -> b.b:",
          "    void foo() -> m1",
          "    void"
              + " com.android.tools.r8.naming.applymapping.ApplyMappingMovedMethodTest$Other.foo()"
              + " -> m2");

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8ApplyMappingSameCompilation() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(One.class, Other.class, Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addApplyMapping(mapping)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertClassesMerged(One.class, Other.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("One::foo")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz("b.b");
              assertThat(clazz, isPresent());
              Set<String> expected = new HashSet<>();
              expected.add("m1");
              // TODO(b/269178203): We should be able to rewrite this to m2.
              expected.add("a");
              assertEquals(
                  expected,
                  clazz.allMethods().stream()
                      .map(MemberSubject::getFinalName)
                      .collect(Collectors.toSet()));
            });
  }

  @Test
  public void testR8TestReference() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(One.class, Other.class, Main.class)
            .setMinApi(parameters)
            .addKeepMainRule(Main.class)
            .addHorizontallyMergedClassesInspector(
                inspector -> inspector.assertClassesMerged(One.class, Other.class))
            .enableInliningAnnotations()
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addClasspathClasses(One.class, Other.class)
        .addKeepAllClassesRule()
        .addApplyMapping(libraryResult.getProguardMap())
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/269178203): We could rewrite calls to m2 but it would be better to just keep the
        //  endpoints in the program when tests are referencing it - in this example it is this
        //  compilation that is the test and it referencing the libraryResult, which is the program.
        .assertFailureWithErrorThatThrows(NoClassDefFoundError.class)
        .assertFailureWithErrorThatMatches(containsString(typeName(Other.class)));
  }

  public static class One {

    @NeverInline
    public static void foo() {
      System.out.println("One::foo");
    }
  }

  public static class Other {

    @NeverInline
    public static void foo() {
      System.out.println("Other::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (System.currentTimeMillis() > 0) {
        One.foo();
      } else {
        Other.foo();
      }
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      One.foo();
      Other.foo();
    }
  }
}
