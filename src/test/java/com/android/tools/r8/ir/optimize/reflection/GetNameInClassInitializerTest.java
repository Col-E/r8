// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
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

  public GetNameInClassInitializerTest(TestParameters parameters, boolean enableMinification)
      throws Exception {
    super(parameters, enableMinification);

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.addAll(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(MAIN.getPackage()),
        path -> path.getFileName().toString().startsWith("GetNameClinit")));
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

  @Test
  public void testR8_pinning() throws Exception {
    // Pinning the test class.
    testForR8(parameters.getBackend())
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class **.GetNameClinit*")
        .minification(enableMinification)
        .setMinApi(parameters)
        .addOptionsModification(this::configure)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8_shallow_pinning() throws Exception {
    // Pinning the test class.
    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(classPaths)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .addKeepRules("-keep,allowobfuscation class **.GetNameClinit*")
            .minification(enableMinification)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .compile();
    result
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(result.inspector().clazz(GetNameClinitClass.class).getFinalName());
  }
}
