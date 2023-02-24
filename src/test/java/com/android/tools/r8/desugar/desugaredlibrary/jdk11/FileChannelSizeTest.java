// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FileChannelSizeTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("class java.io.FileNotFoundException");
  private static final String EXPECTED_KEEP_RULES =
      StringUtils.lines(
          "-keep class j$.nio.channels.DesugarChannels {",
          "    java.nio.channels.FileChannel"
              + " convertMaybeLegacyFileChannelFromLibrary(java.nio.channels.FileChannel);",
          "}");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public FileChannelSizeTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspectKeepRules(
            kr -> {
              if (parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
                assertEquals(EXPECTED_KEEP_RULES, kr.get(0));
              }
            })
        .inspectL8(
            i -> {
              if (compilationSpecification.isL8Shrink()) {
                assertTrue(i.allClasses().size() <= 6);
                if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
                  assertEquals(0, i.allClasses().size());
                }
              }
            })
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public static class TestClass {

    public static void main(String[] args) {
      try {
        String toWrite = "Hello World! ";
        ByteBuffer byteBuffer = ByteBuffer.allocate(toWrite.length());
        FileInputStream fileInputStream = new FileInputStream(new File("notexisting.txt"));
        fileInputStream.getChannel().read(byteBuffer);
        fileInputStream.close();
        System.out.println(new String(byteBuffer.array()));
      } catch (IOException e) {
        System.out.println(e.getClass());
      }
    }
  }
}
