// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
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
  private static final String OUTPUT_WITH_SHRUNK_ATTRIBUTE = StringUtils.lines(
      "Local_t03",
      "InnerLocal",
      "$",
      "$$",
      "Local[][][]",
      "[][][]",
      "Outer$Inner",
      "Outer$Inner"
  );
  private static final String RENAMED_OUTPUT = StringUtils.lines(
      "f",
      "InnerLocal",
      "b",
      "$$",
      "d[][][]",
      "[][][]",
      "Inner",
      "Inner"
  );
  private static final String RENAMED_OUTPUT_FOR_OLDER_VMS = StringUtils.lines(
      "Local_t03",
      "InnerLocal",
      "$",
      "$$",
      "Local[][][]",
      "[][][]",
      "Inner",
      "Inner"
  );
  private static final Class<?> MAIN = ClassGetSimpleName.class;

  public GetSimpleNameTest(Backend backend, boolean enableMinification) throws Exception {
    super(backend, enableMinification);

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
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)",
        backend == Backend.CF && !enableMinification);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(TestRunResult result, int expectedCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countGetName(mainMethod);
    assertEquals(expectedCount, count);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend)",
        backend == Backend.DEX && !enableMinification);

    TestRunResult result = testForD8()
        .debug()
        .addProgramFiles(classPaths)
        .addOptionsModification(this::configure)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);

    result = testForD8()
        .release()
        .addProgramFiles(classPaths)
        .addOptionsModification(this::configure)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }

  @Test
  public void testR8_pinning() throws Exception {
    // Pinning the test class.
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(classPaths)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class **.ClassGetSimpleName*")
        .addKeepRules("-keep class **.Outer*")
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
        .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString());
    if (!enableMinification) {
      builder.addKeepRules("-dontobfuscate");
    }
    TestRunResult result =
        builder
            .addOptionsModification(this::configure)
            .run(MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }

  @Test
  public void testR8_shallow_pinning() throws Exception {
    // Shallow pinning the test class.
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(classPaths)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep,allowobfuscation class **.ClassGetSimpleName*")
        // See b/119471127: some old VMs are not resilient to broken attributes.
        // Comment out the following line to reproduce b/120130435
        // then use OUTPUT_WITH_SHRUNK_ATTRIBUTE
        .addKeepRules("-keep,allowobfuscation class **.Outer*")
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
        .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString());
    if (!enableMinification) {
      builder.addKeepRules("-dontobfuscate");
    }
    TestRunResult result =
        builder
            .addOptionsModification(this::configure)
            .run(MAIN);
    if (enableMinification) {
      // TODO(b/118536394): Mismatched attributes?
      if (backend == Backend.CF) {
        return;
      }
      // TODO(b/120185045): Short name of innerName is not renamed.
      if (ToolHelper.getDexVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
        result.assertSuccessWithOutput(RENAMED_OUTPUT_FOR_OLDER_VMS);
      } else {
        result.assertSuccessWithOutput(RENAMED_OUTPUT);
      }
    } else {
      result.assertSuccessWithOutput(JAVA_OUTPUT);
    }
    test(result, 0);
  }
}
