// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class PublicFieldInnerClassTestMain {
  private static class PrivateBase {
    public int value;
  }

  private static class PrivateSubclass extends PrivateBase {
  }

  static class PackageBase {
    public int value;
  }

  private static class PackageSubclass extends PackageBase {
  }

  protected static class ProtectedBase {
    public int value;
  }

  private static class ProtectedSubclass extends ProtectedBase {
  }

  public static class PublicBase {
    public int value;
  }

  private static class PublicSubclass extends PublicBase {
  }

  private static int getPrivate(PrivateSubclass instance) {
    return instance.value;
  }

  private static int getPackage(PackageSubclass instance) {
    return instance.value;
  }

  private static int getProtected(ProtectedSubclass instance) {
    return instance.value;
  }

  private static int getPublic(PublicSubclass instance) {
    return instance.value;
  }

  public static void main(String[] args) {
    System.out.println(getPrivate(new PrivateSubclass()));
    System.out.println(getPackage(new PackageSubclass()));
    System.out.println(getProtected(new ProtectedSubclass()));
    System.out.println(getPublic(new PublicSubclass()));
  }
}

@RunWith(Parameterized.class)
public class PublicFieldInnerClassTest extends TestBase {
  private static final Class CLASS = PublicFieldInnerClassTestMain.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("0", "0", "0", "0");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  public PublicFieldInnerClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .addProgramClassesAndInnerClasses(CLASS)
        .addKeepMainRule(CLASS)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
