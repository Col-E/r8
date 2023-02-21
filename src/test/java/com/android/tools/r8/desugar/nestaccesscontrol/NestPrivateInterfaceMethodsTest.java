// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NestPrivateInterfaceMethodsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello world!");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public NestPrivateInterfaceMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private byte[] getClassWithNest(Class<?> clazz) throws Exception {
    return transformer(clazz)
        .setNest(I.class, J.class)
        .setAccessFlags(
            I.class.getMethod("foo"),
            flags -> {
              flags.unsetPublic();
              flags.setPrivate();
            })
        .transform();
  }

  @Test
  public void test() throws Exception {
    testForDesugaring(parameters)
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getClassWithNest(I.class), getClassWithNest(J.class))
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/191115349): Nest desugar does not downgrade the classfile version.
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotJavac(c)
                    || parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  @Test
  public void testOnClasspath() throws Exception {
    byte[] bytesI = getClassWithNest(I.class);
    byte[] bytesJ = getClassWithNest(J.class);
    Path outI =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(bytesI)
            .addClasspathClassFileData(bytesJ)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    Path outJ =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(bytesJ)
            .addClasspathClassFileData(bytesI)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    Path outTestClass =
        testForD8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .addClasspathClassFileData(bytesI, bytesJ)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    testForD8(parameters.getBackend())
        .addProgramFiles(outI, outJ, outTestClass)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface I {
    default /* will be private */ void foo() {
      System.out.println("Hello world!");
    }
  }

  interface J {
    default void bar(I o) {
      o.foo();
    }
  }

  static class TestClass implements I, J {

    public static void main(String[] args) {
      TestClass o = new TestClass();
      o.bar(o);
    }
  }
}
