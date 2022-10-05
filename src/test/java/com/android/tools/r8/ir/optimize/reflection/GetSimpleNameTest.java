// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.optimize.reflection.Outer.TestHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;

class ClassGetSimpleName {
  @NeverInline
  static void A01_t03() {
    class Local_t03 {
      Local_t03() {
      }

      class InnerLocal {
        InnerLocal() {
        }
      }
    }
    // Local_t03
    System.out.println(Local_t03.class.getSimpleName());
    // InnerLocal
    System.out.println(Local_t03.InnerLocal.class.getSimpleName());

    class $ {
      $() {
      }

      class $$ {
        $$() {
        }
      }
    }
    // $
    System.out.println($.class.getSimpleName());
    // $$
    System.out.println($.$$.class.getSimpleName());
  }

  @NeverInline
  static void A03_t02() {
    class Local {
      Local() {
      }
    }
    // Local[][][]
    System.out.println(Local[][][].class.getSimpleName());
  }

  @NeverInline
  static void A03_t03() {
    Class a2 = Array.newInstance((new Object() {}).getClass(), new int[] {1, 2, 3}).getClass();
    // [][][]
    System.out.println(a2.getSimpleName());
  }

  @NeverInline
  static void b120130435() {
    System.out.println(Outer.Inner.class.getSimpleName());
    System.out.println(Outer.TestHelper.getHelper().getClassName());
  }

  public static void main(String[] args) {
    A01_t03();
    A03_t02();
    A03_t03();
    b120130435();
  }
}

class Outer {
  static class Inner {
    public Inner() {
    }
  }

  static class TestHelper {
    Inner inner;

    private TestHelper(Inner inner) {
      this.inner = inner;
    }

    String getClassName() {
      return inner.getClass().getSimpleName();
    }

    static TestHelper getHelper() {
      return new TestHelper(new Inner());
    }
  }
}

public class GetSimpleNameTest extends GetNameTestBase {
  private final Collection<Path> classPaths;
  private static final String JVM_OUTPUT =
      StringUtils.lines(
          "Local_t03", "InnerLocal", "$", "$$", "Local[][][]", "[][][]", "Inner", "Inner");

  // When removing the class attributes the simple name of a class becomes prepended with the
  // outer class.
  private static final String OUTPUT_NO_ATTRIBUTES =
      StringUtils.lines(
          "ClassGetSimpleName$1Local_t03",
          "InnerLocal",
          "ClassGetSimpleName$1$",
          "$$",
          "ClassGetSimpleName$1Local[][][]",
          "ClassGetSimpleName$1[][][]",
          "Inner",
          "Inner");

  private static final String RENAMED_OUTPUT =
      StringUtils.lines("d", "a", "a", "a", "c[][][]", "b[][][]", "a", "a");
  private static final Class<?> MAIN = ClassGetSimpleName.class;

  public GetSimpleNameTest(TestParameters parameters, boolean enableMinification) throws Exception {
    super(parameters, enableMinification);

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.addAll(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(MAIN.getPackage()),
        path -> path.getFileName().toString().startsWith("ClassGetSimpleName")));
    builder.add(ToolHelper.getClassFileForTestClass(Outer.class));
    builder.add(ToolHelper.getClassFileForTestClass(Outer.Inner.class));
    builder.add(ToolHelper.getClassFileForTestClass(Outer.TestHelper.class));
    classPaths = builder.build();
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(
        "Only run JVM reference on CF runtimes",
        parameters.isCfRuntime() && !enableMinification);
    CodeInspector inspector = new CodeInspector(classPaths);
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JVM_OUTPUT);
  }

  private void test(SingleTestRunResult<?> result, boolean isOptimizing) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(0, countGetName(mainMethod));

    if (isOptimizing) {
      ClassSubject testHelperClassSubject = codeInspector.clazz(TestHelper.class);
      assertThat(testHelperClassSubject, isPresent());
      assertThat(testHelperClassSubject.uniqueMethodWithOriginalName("getClassName"), isAbsent());
    }
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime() && !enableMinification);

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramFiles(classPaths)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JVM_OUTPUT);
    test(result, false);

    result =
        testForD8()
            .release()
            .addProgramFiles(classPaths)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JVM_OUTPUT);
    test(result, false);
  }

  @Test
  public void testR8_pin_all() throws Exception {
    // Pinning the test class.
    testForR8(parameters.getBackend())
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepAllClassesRule()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .minification(enableMinification)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(this::configure)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JVM_OUTPUT)
        .apply(result -> test(result, false));
  }

  @Test
  public void testR8_pinning() throws Exception {
    // Pinning the test class.
    testForR8(parameters.getBackend())
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class **.ClassGetSimpleName*")
        .addKeepRules("-keep class **.Outer*")
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .minification(enableMinification)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(this::configure)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(OUTPUT_NO_ATTRIBUTES)
        .apply(result -> test(result, true));
  }

  @Test
  public void testR8_shallow_pinning() throws Exception {
    // Shallow pinning the test class.
    testForR8(parameters.getBackend())
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep,allowobfuscation class **.ClassGetSimpleName*")
        // See b/119471127: some old VMs are not resilient to broken attributes.
        // Comment out the following line to reproduce b/120130435
        // then use OUTPUT_WITH_SHRUNK_ATTRIBUTES
        .addKeepRules("-keep,allowobfuscation class **.Outer*")
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .minification(enableMinification)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(this::configure)
        .run(parameters.getRuntime(), MAIN)
        .applyIf(enableMinification, result -> result.assertSuccessWithOutput(RENAMED_OUTPUT))
        .applyIf(
            !enableMinification, result -> result.assertSuccessWithOutput(OUTPUT_NO_ATTRIBUTES))
        .apply(result -> test(result, true));
  }
}
