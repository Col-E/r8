// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress214340258 extends TestBase {
  // Generate this many classes to not overflow instruction limit.
  static final int NUMBER_OF_FILES = 50;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public Regress214340258(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Path compiledJumbo = getZipWithJumboString();
    R8TestRunResult r8TestRunResult =
        testForR8(parameters.getBackend())
            .addDontOptimize()
            .addKeepAllClassesRule()
            .addProgramFiles(compiledJumbo)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), "TestClass0");
    r8TestRunResult.assertSuccessWithOutputLines("foobar");
    assertTrue(hasJumboString(r8TestRunResult));
  }

  private boolean hasJumboString(R8TestRunResult r8TestRunResult)
      throws IOException, ExecutionException {
    for (FoundClassSubject classSubject : r8TestRunResult.inspector().allClasses()) {
      for (FoundMethodSubject foundMethodSubject : classSubject.allMethods()) {
        for (InstructionSubject instruction : foundMethodSubject.instructions()) {
          if (instruction.isJumboString()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public Path getZipWithJumboString() throws IOException {
    List<Path> javaFiles = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_FILES; i++) {
      String name = "TestClass" + i;
      Path file = temp.newFile(name + ".java").toPath();
      Files.write(file, getClassWithManyStrings(name, i).getBytes(StandardCharsets.UTF_8));
      javaFiles.add(file);
    }
    Path compiledJumbo = javac(CfRuntime.getCheckedInJdk9()).addSourceFiles(javaFiles).compile();
    return compiledJumbo;
  }

  private String getClassWithManyStrings(String className, int index) {
    String file =
        ""
            + "public class "
            + className
            + " {\n"
            + "  public static void use(String s) { }\n"
            + "\n"
            + "  public static void main(String[] args) {\n"
            + "    String s = \"foobar\";\n";

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < Constants.MAX_NON_JUMBO_INDEX / NUMBER_OF_FILES; i++) {
      builder.append("    s = \"foobar" + i + "_" + index + "\";\n");
      builder.append("    System.getenv(s);\n");
    }
    file += builder.toString();

    file += "" + "    s = \"foobar\";\n" + "    System.out.println(s);\n" + "  }\n" + "}";
    return file;
  }
}
