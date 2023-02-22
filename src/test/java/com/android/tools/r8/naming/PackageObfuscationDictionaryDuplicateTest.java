// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.keeppackagenames.Top;
import com.android.tools.r8.naming.packageobfuscationdict.A;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackageObfuscationDictionaryDuplicateTest extends TestBase {

  public static class C {
    public static void main(String[] args) {
      System.out.print("HELLO WORLD!");
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
        .addProgramClassesAndInnerClasses(Top.class, A.class, C.class)
        .noTreeShaking()
        .addKeepRules("-packageobfuscationdictionary " + dictionary.toString())
        .addKeepMainRule(C.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), C.class)
        .assertSuccessWithOutput("HELLO WORLD!")
        .inspect(
            inspector -> {
              ClassSubject clazzTop = inspector.clazz(Top.class);
              assertTrue(clazzTop.getFinalName().endsWith("a.a"));
              ClassSubject clazzA = inspector.clazz(A.class);
              assertTrue(clazzA.getFinalName().endsWith("b.a"));
            });
  }
}
