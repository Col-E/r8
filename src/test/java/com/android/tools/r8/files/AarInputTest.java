// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.files;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AarInputTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public AarInputTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Path buildAar() throws Exception {
    Path aar = temp.newFolder().toPath().resolve("out.aar");
    Path classesJar = temp.newFolder().toPath().resolve("classes.jar");
    writeClassesToJar(classesJar, Collections.singletonList(TestClass.class));
    try (ZipOutputStream stream = new ZipOutputStream(Files.newOutputStream(aar))) {
      ZipUtils.writeToZipStream(
          stream, "classes.jar", Files.readAllBytes(classesJar), ZipEntry.DEFLATED);
    }
    return aar;
  }

  @Test
  public void allowAarProgramInputD8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello!");
    } else {
      testForD8()
          .addProgramFiles(buildAar())
          .setMinApi(parameters)
          .compile()
          .inspect(inspector -> assertThat(inspector.clazz(TestClass.class), isPresent()))
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello!");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello!");
    }
  }
}
