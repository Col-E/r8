// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class GetNameClass {
  interface Itf {
    String foo();
  }

  static Itf createItf() {
    return new Itf() {
      @Override
      public String foo() {
        return "anonymous";
      }
    };
  }
}

class GetNameMain {
  public static void main(String[] args) {
    Class<?> test = GetNameClass.createItf().getClass();
    String name = test.getCanonicalName();
    System.out.println(name == null ? "-Returned-null-" : name);
  }
}

@RunWith(Parameterized.class)
public class EnclosingMethodTest extends TestBase {
  private final Backend backend;
  private final boolean enableMinification;
  private Collection<Path> classPaths;
  private static final String JAVA_OUTPUT = "-Returned-null-" + System.lineSeparator();
  private static final Class<?> MAIN = GetNameMain.class;

  @Parameterized.Parameters(name = "Backend: {0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(Backend.values(), BooleanUtils.values());
  }

  public EnclosingMethodTest(Backend backend, boolean enableMinification) throws Exception {
    this.backend = backend;
    this.enableMinification = enableMinification;

    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.addAll(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(MAIN.getPackage()),
        path -> path.getFileName().toString().startsWith("GetName")));
    classPaths = builder.build();
  }

  private void configure(InternalOptions options) {
    options.enableNameReflectionOptimization = false;
    options.testing.forceNameReflectionOptimization = false;
  }

  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)", backend == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(classPaths)
        .enableInliningAnnotations()
        .addOptionsModification(this::configure)
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class **.GetName*")
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod");
    if (!enableMinification) {
      builder.addKeepRules("-dontobfuscate");
    }

    TestRunResult result = builder.run(MAIN);
    if (backend == Backend.DEX) {
      if (ToolHelper.getDexVm().getVersion().isNewerThan(Version.V4_4_4)
          && ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(Version.V6_0_1)) {
        result.assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
        return;
      }
    }

    result.assertSuccessWithOutput(JAVA_OUTPUT);
  }
}
