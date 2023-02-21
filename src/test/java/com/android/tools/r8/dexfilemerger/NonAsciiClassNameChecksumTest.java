// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dexfilemerger;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonAsciiClassNameChecksumTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello æ");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public NonAsciiClassNameChecksumTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String getTransformedName(Class<?> clazz) {
    return getTransformedName(clazz.getTypeName());
  }

  private String getTransformedName(String typeName) {
    return NonAsciiClassNameChecksumTest.class.getTypeName()
        + "$"
        + (typeName.equals(TestClaass.class.getTypeName()) ? "TestClåss" : "TæstClass");
  }

  private byte[] getTransform(Class<?> clazz) throws IOException {
    return transformer(clazz)
        .setClassDescriptor(DescriptorUtils.javaTypeToDescriptor(getTransformedName(clazz)))
        .transform();
  }

  @Test
  public void test() throws Exception {
    Path intermediate1 = compileIntermediate(TaestClass.class);
    Path intermediate2 = compileIntermediate(TestClaass.class);
    testForD8()
        .addProgramFiles(intermediate1, intermediate2)
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), getTransformedName(TaestClass.class))
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              checkIncludesChecksum(inspector, TaestClass.class);
              checkIncludesChecksum(inspector, TestClaass.class);
            });
  }

  private Path compileIntermediate(Class<?> clazz) throws Exception {
    return testForD8()
        .setOutputMode(OutputMode.DexFilePerClassFile)
        .addProgramClassFileData(getTransform(clazz))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .compile()
        .inspect(inspector -> checkIncludesChecksum(inspector, clazz))
        .writeToZip();
  }

  private void checkIncludesChecksum(CodeInspector inspector, Class<?> clazz) {
    ClassSubject classSubject = inspector.clazz(getTransformedName(clazz));
    assertThat(classSubject, isPresent());
    classSubject.getDexProgramClass().getChecksum();
  }

  static class TaestClass {
    public static void main(String[] args) {
      System.out.println("Hello æ");
    }
  }

  static class TestClaass {
    public static void main(String[] args) {
      System.out.println("Hello å");
    }
  }
}