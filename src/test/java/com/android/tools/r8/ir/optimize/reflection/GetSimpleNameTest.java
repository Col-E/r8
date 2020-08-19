// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
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

    @ForceInline
    String getClassName() {
      return inner.getClass().getSimpleName();
    }

    static TestHelper getHelper() {
      return new TestHelper(new Inner());
    }
  }
}

public class GetSimpleNameTest extends GetNameTestBase {
  private Collection<Path> classPaths;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Local_t03",
      "InnerLocal",
      "$",
      "$$",
      "Local[][][]",
      "[][][]",
      "Inner",
      "Inner"
  );
  private static final String OUTPUT_WITH_SHRUNK_ATTRIBUTES = StringUtils.lines(
      "Local_t03",
      "InnerLocal",
      "$",
      "$$",
      "Local[][][]",
      "[][][]",
      "Outer$Inner",
      "Outer$Inner"
  );
  // JDK8 computes the simple name differently: some assumptions about non-member classes,
  // e.g., 1 or more digits (followed by the simple name if it's local).
  // Since JDK9, the simple name is computed by stripping off the package name.
  // See b/132808897 for more details.
  private static final String RENAMED_OUTPUT_JDK8 = StringUtils.lines(
      "d",
      "a",
      "a",
      "a",
      "c[][][]",
      "b[][][]",
      "a",
      "a"
  );
  private static final String RENAMED_OUTPUT = StringUtils.lines(
      "d",
      "a",
      "a",
      "a",
      "c[][][]",
      "[][][]",
      "a",
      "a"
  );
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
    builder.add(ToolHelper.getClassFileForTestClass(ForceInline.class));
    builder.add(ToolHelper.getClassFileForTestClass(NeverInline.class));
    classPaths = builder.build();
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(
        "Only run JVM reference on CF runtimes",
        parameters.isCfRuntime() && !enableMinification);
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(SingleTestRunResult<?> result) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(0, countGetName(mainMethod));
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
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result);

    result =
        testForD8()
            .release()
            .addProgramFiles(classPaths)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result);
  }

  @Test
  public void testR8_pinning() throws Exception {
    // Pinning the test class.
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(classPaths)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .addKeepRules("-keep class **.ClassGetSimpleName*")
            .addKeepRules("-keep class **.Outer*")
            .addKeepAttributes("InnerClasses", "EnclosingMethod")
            .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString())
            .minification(enableMinification)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result);
  }

  @Test
  public void testR8_shallow_pinning() throws Exception {
    // Shallow pinning the test class.
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(classPaths)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .addKeepRules("-keep,allowobfuscation class **.ClassGetSimpleName*")
            // See b/119471127: some old VMs are not resilient to broken attributes.
            // Comment out the following line to reproduce b/120130435
            // then use OUTPUT_WITH_SHRUNK_ATTRIBUTES
            .addKeepRules("-keep,allowobfuscation class **.Outer*")
            .addKeepAttributes("InnerClasses", "EnclosingMethod")
            .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString())
            .minification(enableMinification)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN);
    if (enableMinification) {
      if (parameters.isCfRuntime()
          && parameters.getRuntime().asCf().getVm().lessThanOrEqual(CfVm.JDK8)) {
        result.assertSuccessWithOutput(RENAMED_OUTPUT_JDK8);
      } else {
        result.assertSuccessWithOutput(RENAMED_OUTPUT);
      }
    } else {
      result.assertSuccessWithOutput(JAVA_OUTPUT);
    }
    test(result);
  }
}
