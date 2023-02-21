// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.CollectorsUtils.toSingle;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/152715309.
@RunWith(Parameterized.class)
public class ApplyMappingDesugarLambdaTest extends TestBase {

  private static final String EXPECTED = "FOO";
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ApplyMappingDesugarLambdaTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws CompilationFailedException, IOException, ExecutionException {
    // Create a dictionary to control the naming.
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "e");

    final String finalName = "com.android.tools.r8.naming.applymapping.e";

    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(A.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(A.class)
            .setMinApi(parameters)
            .addKeepRules(
                "-keeppackagenames", "-classobfuscationdictionary " + dictionary.toString())
            .compile()
            .inspect(
                inspector -> {
                  assertThat(inspector.clazz(A.class), isPresentAndRenamed());
                  assertEquals(finalName, inspector.clazz(A.class).getFinalName());
                });

    Path libraryPath = libraryResult.writeToZip();

    // Ensure that the library works as supposed.
    testForD8()
        .addProgramClasses(I.class, Main.class)
        .addClasspathFiles(libraryPath)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class, EXPECTED)
        .assertSuccessWithOutputLines(EXPECTED);

    testForR8(parameters.getBackend())
        .addClasspathClasses(A.class)
        .addProgramClasses(I.class, Main.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(I.class)
        .setMinApi(parameters)
        .addApplyMapping(libraryResult.getProguardMap())
        .addOptionsModification(internalOptions -> internalOptions.enableClassInlining = false)
        .addKeepRules("-classobfuscationdictionary " + dictionary.toString())
        .compile()
        .inspect(
            inspector -> {
              // Assert that there is a lambda class created.
              assertEquals(3, inspector.allClasses().size());
              FoundClassSubject lambdaClass =
                  inspector.allClasses().stream()
                      .filter(FoundClassSubject::isSynthetic)
                      .collect(toSingle());
              assertNotSame(finalName, lambdaClass.getFinalName());
            })
        .addRunClasspathFiles(libraryPath)
        .run(parameters.getRuntime(), Main.class, EXPECTED)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class A {

    A(int bar) {
      System.out.println(bar);
    }
  }

  public interface I {

    void doStuff();
  }

  public static class Main {

    public static void main(String[] args) {
      processI(() -> System.out.println(args[0]));
    }

    public static void processI(I i) {
      i.doStuff();
    }
  }
}
