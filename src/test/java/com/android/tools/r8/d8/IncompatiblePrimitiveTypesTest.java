// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.d8;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.hamcrest.Matcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IncompatiblePrimitiveTypesTest extends TestBase {

  @ClassRule
  public static TemporaryFolder tempFolder = ToolHelper.getTemporaryFolderForTest();

  private static Path inputJar;
  private static String expectedOutput = "true";

  @BeforeClass
  public static void setup() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("TestClass");
    classBuilder
        .staticMethodBuilder("main", ImmutableList.of("[Ljava/lang/String;"), "V")
        .setCode(
            "invokestatic TestClass/getByte()B", "invokestatic TestClass/takeBoolean(Z)V", "return")
        .build();
    classBuilder
        .staticMethodBuilder("getByte", ImmutableList.of(), "B")
        .setCode("iconst_1", "ireturn")
        .build();
    classBuilder
        .staticMethodBuilder("takeBoolean", ImmutableList.of("Z"), "V")
        .setCode(
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "iload_0",
            "invokevirtual java/io/PrintStream/print(Z)V",
            "return")
        .build();
    inputJar = tempFolder.getRoot().toPath().resolve("input.jar");
    jasminBuilder.writeJar(inputJar);
  }

  @Test
  public void jvmTest() throws Exception {
    assumeTrue(
        "JVM test independent of Art version - only run when testing on latest",
        ToolHelper.getDexVm().getVersion().isLatest());
    testForJvm().addClasspath(inputJar).run("TestClass").assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void dexTest() throws Exception {
    TestRunResult<?> d8Result = testForD8().addProgramFiles(inputJar).run("TestClass");
    TestRunResult<?> dxResult = testForDX().addProgramFiles(inputJar).run("TestClass");
    if (ToolHelper.getDexVm().getVersion().isNewerThan(Version.V4_4_4)) {
      d8Result.assertSuccessWithOutput(expectedOutput);
      dxResult.assertSuccessWithOutput(expectedOutput);
    } else {
      // TODO(b/119812046): On Art 4.0.4 and 4.4.4 it is a verification error to use one short type
      // as another short type.
      Matcher<String> expectedError = containsString("java.lang.VerifyError");
      d8Result.assertFailureWithErrorThatMatches(expectedError);
      dxResult.assertFailureWithErrorThatMatches(expectedError);
    }
  }
}
