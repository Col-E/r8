// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class BackportedMethodMergeTest extends TestBase {
  @Test
  public void testD8Merge() throws Exception {
    String jvmOutput = testForJvm()
        .addTestClasspath()
        .run(MergeRun.class).getStdOut();
    // See b/123242448
    Path zip1 = temp.newFile("first.zip").toPath();
    Path zip2 = temp.newFile("second.zip").toPath();

    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(MergeRun.class, MergeInputB.class)
        .compile()
        .assertNoMessages()
        .writeToZip(zip1);
    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(MergeInputA.class)
        .compile()
        .assertNoMessages()
        .writeToZip(zip2);
    testForD8()
        .addProgramFiles(zip1, zip2)
        .setMinApi(AndroidApiLevel.L)
        .compile()
        .assertNoMessages()
        .run(MergeRun.class)
        .assertSuccessWithOutput(jvmOutput);
  }

  @Test
  public void testMergeOldPrefix()
      throws IOException, CompilationFailedException, ExecutionException {
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
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(MergeRunWithOldBackportedPrefix.class)
        .addProgramClassFileData(transform)
        .compile()
        .assertNoMessages()
        .writeToZip(zip1);
    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClassFileData(transform)
        .compile()
        .assertNoMessages()
        .writeToZip(zip2);
    testForD8()
        .addProgramFiles(zip1, zip2)
        .setMinApi(AndroidApiLevel.L)
        .compile()
        .assertNoMessages()
        .run(MergeRunWithOldBackportedPrefix.class)
        .assertSuccessWithOutputLines("foobar");
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
