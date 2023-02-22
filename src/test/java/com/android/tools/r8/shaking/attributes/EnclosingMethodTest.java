// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.attributes;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

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

  private static final String JAVA_OUTPUT = "-Returned-null-" + System.lineSeparator();
  private static final String OUTPUT_WITH_SHRUNK_ATTRIBUTES =
      GetNameClass.class.getName() + "$1" + System.lineSeparator();
  private static final Class<?> MAIN = GetNameMain.class;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableMinification;

  private Collection<Path> classPaths;

  @Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Before
  public void setup() throws Exception {
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
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(classPaths)
        .addOptionsModification(this::configure)
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep class **.GetName*")
        .addKeepRules("-keepattributes InnerClasses,EnclosingMethod")
        .minification(enableMinification)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(OUTPUT_WITH_SHRUNK_ATTRIBUTES);
  }
}
