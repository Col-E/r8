// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BackportedMethodMergeTest extends TestBase {

  private static final String MERGE_RUN_EXPECTED_OUTPUT =
      StringUtils.lines("42", "1078263808", "43", "1078296576");
  private static final String MERGE_RUN_WITH_OLD_BACKPORTED_PREFIX_EXPECTED_OUTPUT =
      StringUtils.lines("foobar");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsEndingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MergeRun.class)
        .assertSuccessWithOutput(MERGE_RUN_EXPECTED_OUTPUT);
  }

  @Test
  public void testD8Merge() throws Exception {
    parameters.assumeDexRuntime();

    // See b/123242448
    Path zip1 = temp.newFile("first.zip").toPath();
    Path zip2 = temp.newFile("second.zip").toPath();
    testForD8()
        .setMinApi(parameters)
        .addProgramClasses(MergeRun.class, MergeInputB.class)
        .compile()
        .assertNoMessages()
        .writeToZip(zip1);
    testForD8()
        .setMinApi(parameters)
        .addProgramClasses(MergeInputA.class)
        .compile()
        .assertNoMessages()
        .writeToZip(zip2);
    testForD8()
        .addProgramFiles(zip1, zip2)
        .setMinApi(parameters)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), MergeRun.class)
        .assertSuccessWithOutput(MERGE_RUN_EXPECTED_OUTPUT);
  }

  @Test
  public void testMergeOldPrefix() throws Exception {
    parameters.assumeDexRuntime();

    byte[] transform = transformer($r8$java8methods$utility_MergeInputWithOldBackportedPrefix.class)
        .addClassTransformer(new ClassTransformer() {
          @Override
          public void visit(int version, int access, String name, String signature,
              String superName, String[] interfaces) {
            ClassAccessFlags accessFlags = ClassAccessFlags.fromCfAccessFlags(access);
            accessFlags.setSynthetic();
            super.visit(version, accessFlags.getAsCfAccessFlags(),
                name, signature, superName, interfaces);
          }
        }).transform();

    Path zip1 = temp.newFile("first.zip").toPath();
    Path zip2 = temp.newFile("second.zip").toPath();
    testForD8()
        .setMinApi(parameters)
        .addProgramClasses(MergeRunWithOldBackportedPrefix.class)
        .addProgramClassFileData(transform)
        .compile()
        .assertNoMessages()
        .writeToZip(zip1);
    testForD8()
        .setMinApi(parameters)
        .addProgramClassFileData(transform)
        .compile()
        .assertNoMessages()
        .writeToZip(zip2);
    testForD8()
        .addProgramFiles(zip1, zip2)
        .setMinApi(parameters)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), MergeRunWithOldBackportedPrefix.class)
        .assertSuccessWithOutput(MERGE_RUN_WITH_OLD_BACKPORTED_PREFIX_EXPECTED_OUTPUT);
  }


  static class $r8$java8methods$utility_MergeInputWithOldBackportedPrefix {
    public void foo() {
      System.out.println("foobar");
    }

  }

  static class MergeRunWithOldBackportedPrefix {
    public static void main(String[] args) {
      $r8$java8methods$utility_MergeInputWithOldBackportedPrefix a =
          new $r8$java8methods$utility_MergeInputWithOldBackportedPrefix();
      a.foo();
    }
  }

  static class MergeInputA {
    public void foo() {
      System.out.println(Integer.hashCode(42));
      System.out.println(Double.hashCode(42.0));
    }
  }

  static class MergeInputB {
    public void foo() {
      System.out.println(Integer.hashCode(43));
      System.out.println(Double.hashCode(43.0));
    }
  }

  static class MergeRun {
    public static void main(String[] args) {
      MergeInputA a = new MergeInputA();
      MergeInputB b = new MergeInputB();
      a.foo();
      b.foo();
    }
  }
}
