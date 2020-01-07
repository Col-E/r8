// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dexfilemerger;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonAsciiClassNameChecksumTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public NonAsciiClassNameChecksumTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path intermediate1 = compileIntermediate(TæstClass.class);
    Path intermediate2 = compileIntermediate(TestClåss.class);
    testForD8()
        .addProgramFiles(intermediate1, intermediate2)
        .setMinApi(parameters.getApiLevel())
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), TæstClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(inspector -> {
          checkIncludesChecksum(inspector, TæstClass.class);
          checkIncludesChecksum(inspector, TestClåss.class);
        });
  }

  private Path compileIntermediate(Class<?> clazz) throws Exception {
    return testForD8()
        .setOutputMode(OutputMode.DexFilePerClassFile)
        .addProgramClasses(clazz)
        .setMinApi(parameters.getApiLevel())
        .setIncludeClassesChecksum(true)
        .compile()
        .inspect(inspector -> checkIncludesChecksum(inspector, clazz))
        .writeToZip();
  }

  private void checkIncludesChecksum(CodeInspector inspector, Class<?> clazz) {
    ClassSubject classSubject = inspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    assertTrue(classSubject.getDexClass().asProgramClass().getChecksum() > 0);
  }

  static class TæstClass {
    public static void main(String[] args) {
      new TestClåss().foo();
    }
  }

  static class TestClåss {
    public void foo() {
      System.out.println("Hello, world");
    }
  }
}