// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/132612059. */
@RunWith(Parameterized.class)
public class FieldMinificationObfuscationDictionaryDuplicateTest extends TestBase {

  public static class A {
    public static String field1 = "HELLO ";
    public static String field2 = "WORLD!";

    public static void main(String[] args) {
      System.out.print(field1 + field2);
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws IOException, CompilationFailedException, ExecutionException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "a");
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class)
        .noTreeShaking()
        .addKeepRules("-obfuscationdictionary " + dictionary.toString())
        .addKeepMainRule(A.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), A.class)
        .assertSuccessWithOutput("HELLO WORLD!");
  }
}
