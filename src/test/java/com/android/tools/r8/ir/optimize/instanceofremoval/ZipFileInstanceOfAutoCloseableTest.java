// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.instanceofremoval;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ZipFileInstanceOfAutoCloseableTest extends TestBase {

  static final String EXPECTED_PRE_API_19 = StringUtils.lines("Not an AutoCloseable");
  static final String EXPECTED_POST_API_19 = StringUtils.lines("Is an AutoCloseable");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ZipFileInstanceOfAutoCloseableTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean runtimeZipFileIsCloseable() {
    return parameters.isCfRuntime()
        || parameters
            .getRuntime()
            .asDex()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  private String expectedOutput() {
    return runtimeZipFileIsCloseable() ? EXPECTED_POST_API_19 : EXPECTED_PRE_API_19;
  }

  private Path getAndroidJar() {
    // Always use an android jar later than API 19. Thus at compile-time ZipFile < Closeable.
    return ToolHelper.getAndroidJar(AndroidApiLevel.LATEST);
  }

  private String getZipFile() throws IOException {
    return ZipBuilder.builder(temp.newFile("file.zip").toPath())
        .addBytes("entry", new byte[1])
        .build()
        .toString();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addInnerClasses(ZipFileInstanceOfAutoCloseableTest.class)
        .setMinApi(parameters.getApiLevel())
        .addLibraryFiles(getAndroidJar())
        .run(parameters.getRuntime(), TestClass.class, getZipFile())
        .assertSuccessWithOutput(expectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ZipFileInstanceOfAutoCloseableTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .addLibraryFiles(getAndroidJar())
        .run(parameters.getRuntime(), TestClass.class, getZipFile())
        .apply(
            result -> {
              if (!runtimeZipFileIsCloseable()) {
                // TODO(b/177532008): This should succeed with the usual expected output.
                result.assertFailure();
              } else {
                result.assertSuccessWithOutput(expectedOutput());
              }
            });
  }

  static class TestClass {

    public static void foo(Object o) throws Exception {
      if (o instanceof AutoCloseable) {
        System.out.println("Is an AutoCloseable");
        ((AutoCloseable) o).close();
      } else {
        System.out.println("Not an AutoCloseable");
      }
    }

    public static void main(String[] args) throws Exception {
      foo(new JarFile(args[0]));
    }
  }
}
