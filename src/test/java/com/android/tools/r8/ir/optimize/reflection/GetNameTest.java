// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;

class GetName0Class {
  static class InnerClass {
  }

  interface Itf {
    void foo();
  }

  @ForceInline
  static Itf createRunnable() {
    return new Itf() {
      @Override
      public void foo() {
        System.out.println("anonymous");
      }
    };
  }

  @ForceInline
  static GetName0Class factory() {
    return new GetName0Class() {
      @Override
      public String toString() {
        return "local";
      }
    };
  }
}

class GetName0Main {
  public static void main(String[] args) {
    {
      Class<?> test = GetName0Class.class;
      System.out.println(test.getName());
      // TODO(b/119426668): desugar Type#getTypeName
      // System.out.println(test.getTypeName());
      System.out.println(test.getCanonicalName());
      System.out.println(test.getSimpleName());
    }

    {
      Class<?> test = GetName0Class.InnerClass.class;
      System.out.println(test.getName());
      // TODO(b/119426668): desugar Type#getTypeName
      // System.out.println(test.getTypeName());
      System.out.println(test.getCanonicalName());
      System.out.println(test.getSimpleName());
    }

    {
      GetName0Class[] arr = new GetName0Class[1];
      Class<?> test = arr.getClass();
      System.out.println(test.getName());
      // TODO(b/119426668): desugar Type#getTypeName
      // System.out.println(test.getTypeName());
      System.out.println(test.getCanonicalName());
      System.out.println(test.getSimpleName());
    }

    {
      Class<?> test = GetName0Class.createRunnable().getClass();
      System.out.println(test.getName());
      // TODO(b/119426668): desugar Type#getTypeName
      // System.out.println(test.getTypeName());
      String name = test.getCanonicalName();
      System.out.println(name == null ? "-Returned-null-" : name);
      name = test.getSimpleName();
      System.out.println(name.isEmpty() ? "-Returned-empty-" : name);
    }

    {
      Class<?> test = GetName0Class.factory().getClass();
      System.out.println(test.getName());
      // TODO(b/119426668): desugar Type#getTypeName
      // System.out.println(test.getTypeName());
      String name = test.getCanonicalName();
      System.out.println(name == null ? "-Returned-null-" : name);
      name = test.getSimpleName();
      System.out.println(name.isEmpty() ? "-Returned-empty-" : name);
    }
  }
}

public class GetNameTest extends GetNameTestBase {
  private Collection<Path> classPaths;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      // getName
      "com.android.tools.r8.ir.optimize.reflection.GetName0Class",
      // getTypeName
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.GetName0Class",
      // getCanonicalName
      "com.android.tools.r8.ir.optimize.reflection.GetName0Class",
      // getSimpleName
      "GetName0Class",
      // getName, inner
      "com.android.tools.r8.ir.optimize.reflection.GetName0Class$InnerClass",
      // getTypeName, inner
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.GetName0Class$InnerClass",
      // getCanonicalName, inner
      "com.android.tools.r8.ir.optimize.reflection.GetName0Class.InnerClass",
      // getSimpleName, inner
      "InnerClass",
      // getName, array
      "[Lcom.android.tools.r8.ir.optimize.reflection.GetName0Class;",
      // getTypeName, array
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.GetName0Class[]",
      // getCanonicalName, array
      "com.android.tools.r8.ir.optimize.reflection.GetName0Class[]",
      // getSimpleName, array
      "GetName0Class[]",
      // getName, anonymous
      "com.android.tools.r8.ir.optimize.reflection.GetName0Class$1",
      // getTypeName, anonymous
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.GetName0Class$1",
      // getCanonicalName, anonymous
      "-Returned-null-",
      // getSimpleName, anonymous
      "-Returned-empty-",
      // getName, local
      "com.android.tools.r8.ir.optimize.reflection.GetName0Class$2",
      // getTypeName, local
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.GetName0Class$2",
      // getCanonicalName, local
      "-Returned-null-",
      // getSimpleName, local
      "-Returned-empty-"
  );
  private static final String RENAMED_OUTPUT = StringUtils.lines(
      // getName
      "com.android.tools.r8.ir.optimize.reflection.e",
      // getTypeName
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.e",
      // getCanonicalName
      "com.android.tools.r8.ir.optimize.reflection.e",
      // getSimpleName
      "e",
      // getName, inner
      "com.android.tools.r8.ir.optimize.reflection.c",
      // getTypeName, inner
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.c",
      // getCanonicalName, inner
      "com.android.tools.r8.ir.optimize.reflection.c",
      // getSimpleName, inner
      "c",
      // getName, array
      "[Lcom.android.tools.r8.ir.optimize.reflection.e;",
      // getTypeName, array
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.e[]",
      // getCanonicalName, array
      "com.android.tools.r8.ir.optimize.reflection.e[]",
      // getSimpleName, array
      "e[]",
      // getName, anonymous
      "com.android.tools.r8.ir.optimize.reflection.a",
      // getTypeName, anonymous
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.a",
      // getCanonicalName, anonymous
      "-Returned-null-",
      // getSimpleName, anonymous
      "-Returned-empty-",
      // getName, local
      "com.android.tools.r8.ir.optimize.reflection.b",
      // getTypeName, local
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.b,
      // getCanonicalName, local
      "-Returned-null-",
      // getSimpleName, local
      "-Returned-empty-"
  );
  private static final Class<?> MAIN = GetName0Main.class;

  public GetNameTest(Backend backend, boolean enableMinification) throws Exception {
    super(backend, enableMinification);

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.addAll(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(MAIN.getPackage()),
        path -> path.getFileName().toString().startsWith("GetName0")));
    builder.add(ToolHelper.getClassFileForTestClass(ForceInline.class));
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
    test(result, 15);

    result = testForD8()
        .release()
        .addProgramFiles(classPaths)
        .addOptionsModification(this::configure)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    // getClass() -> const-class is not available in D8.
    test(result, 11);
  }

  @Test
  public void testR8_pinning() throws Exception {
    // Pinning the test class.
    TestRunResult result = testForR8(backend)
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class **.GetName0*")
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
        .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString())
        .minification(enableMinification)
        .addOptionsModification(this::configure)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 2);
  }

  @Test
  public void testR8_shallow_pinning() throws Exception {
    // Shallow pinning the test class.
    TestRunResult result = testForR8(backend)
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep,allowobfuscation class **.GetName0*")
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
        .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString())
        .minification(enableMinification)
        .addOptionsModification(this::configure)
        .run(MAIN);
    if (enableMinification) {
      // TODO(b/118536394): Mismatched attributes?
      if (backend == Backend.CF) {
        return;
      }
      // TODO(b/120185045): Short name of innerName is not renamed.
      // result.assertSuccessWithOutput(RENAMED_OUTPUT);
    } else {
      result.assertSuccessWithOutput(JAVA_OUTPUT);
    }
    test(result, 2);
  }

}
