// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;

class GetNameClinitClass {
  static String name;
  static {
    name = GetNameClinitClass.class.getName();
  }

  @NeverInline
  static String getName() {
    return name;
  }
}

class GetNameClinitRunner {
  public static void main(String[] args) {
    System.out.print(GetNameClinitClass.getName());
  }
}

public class GetNameInClassInitializerTest extends GetNameTestBase {
  private Collection<Path> classPaths;
  private static final String JAVA_OUTPUT = GetNameClinitClass.class.getName();
  private static final Class<?> MAIN = GetNameClinitRunner.class;

  public GetNameInClassInitializerTest(Backend backend, boolean enableMinification)
      throws Exception {
    super(backend, enableMinification);

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.addAll(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(MAIN.getPackage()),
        path -> path.getFileName().toString().startsWith("GetNameClinit")));
    builder.add(ToolHelper.getClassFileForTestClass(NeverInline.class));
    classPaths = builder.build();
  }

  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)",
        backend == Backend.CF && !enableMinification);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8_pinning() throws Exception {
    // Pinning the test class.
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class **.GetNameClinit*")
        .addKeepRules("-printmapping");
    if (!enableMinification) {
      builder.addKeepRules("-dontobfuscate");
    }
    builder
        .addOptionsModification(this::configure)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8_shallow_pinning() throws Exception {
    // Pinning the test class.
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep,allowobfuscation class **.GetNameClinit*")
        .addKeepRules("-printmapping");
    if (!enableMinification) {
      builder.addKeepRules("-dontobfuscate");
    }

    TestRunResult result =
        builder
            .addOptionsModification(this::configure)
            .run(MAIN);
    result.assertSuccessWithOutput(
        result.inspector().clazz(GetNameClinitClass.class).getFinalName());
  }
}
