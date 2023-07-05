// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
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

  static Itf createRunnable() {
    return new Itf() {
      @Override
      public void foo() {
        System.out.println("anonymous");
      }
    };
  }

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
      "com.android.tools.r8.ir.optimize.reflection.c",
      // getTypeName
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.c",
      // getCanonicalName
      "com.android.tools.r8.ir.optimize.reflection.c",
      // getSimpleName
      "c",
      // getName, inner
      "com.android.tools.r8.ir.optimize.reflection.c$a",
      // getTypeName, inner
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.c$a",
      // getCanonicalName, inner
      "com.android.tools.r8.ir.optimize.reflection.c.a",
      // getSimpleName, inner
      "a",
      // getName, array
      "[Lcom.android.tools.r8.ir.optimize.reflection.c;",
      // getTypeName, array
      // TODO(b/119426668): desugar Type#getTypeName
      // "com.android.tools.r8.ir.optimize.reflection.c[]",
      // getCanonicalName, array
      "com.android.tools.r8.ir.optimize.reflection.c[]",
      // getSimpleName, array
      "c[]",
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

  public GetNameTest(TestParameters parameters, boolean enableMinification) throws Exception {
    super(parameters, enableMinification);

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.addAll(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(MAIN.getPackage()),
        path -> path.getFileName().toString().startsWith("GetName0")));
    classPaths = builder.build();
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(
        "Only run JVM reference on CF runtimes",
        parameters.isCfRuntime() && !enableMinification);
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(SingleTestRunResult<?> result, int expectedCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countGetName(mainMethod);
    assertEquals(expectedCount, count);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend)", parameters.isDexRuntime() && !enableMinification);

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramFiles(classPaths)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 15);

    result =
        testForD8()
            .release()
            .addProgramFiles(classPaths)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    // getClass() -> const-class is not available in D8.
    test(result, 11);
  }

  @Test
  public void testR8_pinning() throws Exception {
    // Pinning the test class.
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(classPaths)
            .addKeepMainRule(MAIN)
            .addKeepRules("-keep class **.GetName0*")
            .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
            .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString())
            .minification(enableMinification)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject getName0ClassSubject = inspector.clazz(GetName0Class.class);
                  assertThat(getName0ClassSubject, isPresent());
                  assertTrue(
                      getName0ClassSubject.allMethods(FoundMethodSubject::isStatic).isEmpty());
                })
            .run(parameters.getRuntime(), MAIN)
            // TODO(b/154813140): Invalidly assumes that getClass on kept classes can be optimized.
            .assertSuccessWithOutputThatMatches(not(equalTo(JAVA_OUTPUT)));
    test(result, 6);
  }

  @Test
  public void testR8_shallow_pinning() throws Exception {
    // Shallow pinning the test class.
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(classPaths)
            .addKeepMainRule(MAIN)
            .addKeepRules("-keep,allowobfuscation class **.GetName0*")
            .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
            .addKeepRules("-printmapping " + createNewMappingPath().toAbsolutePath().toString())
            .minification(enableMinification)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject getName0ClassSubject = inspector.clazz(GetName0Class.class);
                  assertThat(getName0ClassSubject, isPresent());
                  assertTrue(
                      getName0ClassSubject.allMethods(FoundMethodSubject::isStatic).isEmpty());
                })
            .run(parameters.getRuntime(), MAIN);
    // TODO(b/154813140): Invalidly assumes that getClass on kept classes can be optimized.
    if (enableMinification) {
      result.assertSuccessWithOutputThatMatches(not(equalTo(RENAMED_OUTPUT)));
    } else {
      result.assertSuccessWithOutputThatMatches(not(equalTo(JAVA_OUTPUT)));
    }
    test(result, 6);
  }
}
