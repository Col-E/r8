// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.desugar.LambdaRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MainDexWithSynthesizedClassesTest extends TestBase {

  static final AndroidApiLevel nativeMultiDexLevel = AndroidApiLevel.L;

  static final String EXPECTED = StringUtils.lines("AB");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsEndingAtExcluding(nativeMultiDexLevel)
        .build();
  }

  public MainDexWithSynthesizedClassesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testFinal() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      D8TestCompileResult compileResult =
          testForD8()
              .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
              .addMainDexListClasses(TestClass.class, A.class)
              .setMinApiThreshold(parameters.getApiLevel())
              .compile();
      checkCompilationResult(compileResult);
    }
  }

  @Test
  public void testIntermediate() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult intermediateResult =
        testForD8()
            .addInnerClasses(MainDexWithSynthesizedClassesTest.class)
            .setMinApiThreshold(parameters.getApiLevel())
            .setIntermediate(true)
            .compile();
    D8TestCompileResult compileResult =
        testForD8()
            .addProgramFiles(intermediateResult.writeToZip())
            .addMainDexListClasses(TestClass.class, A.class)
            .setMinApiThreshold(parameters.getApiLevel())
            .compile();
    checkCompilationResult(compileResult);
  }

  private void checkCompilationResult(D8TestCompileResult compileResult) throws Exception {
    if (parameters.getRuntime().asDex().getMinApiLevel().getLevel()
        < nativeMultiDexLevel.getLevel()) {
      compileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors();
    } else {
      compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput(EXPECTED);
    }
    Path out = temp.newFolder().toPath();
    compileResult.apply(b -> b.app.writeToDirectory(out, OutputMode.DexIndexed));
    Path classes = out.resolve("classes.dex");
    Path classes2 = out.resolve("classes2.dex");
    assertTrue(Files.exists(classes));
    assertTrue(Files.exists(classes2));
    checkContainsLambdaClasses(new CodeInspector(classes), A.class);
    checkContainsLambdaClasses(new CodeInspector(classes2), B.class);
  }

  private void checkContainsLambdaClasses(CodeInspector inspector, Class<?> lambdaHolder) {
    assertTrue(
        inspector.allClasses().stream()
            .anyMatch(
                clazz ->
                    clazz.getOriginalName().contains(LambdaRewriter.LAMBDA_CLASS_NAME_PREFIX)
                        && clazz
                            .getOriginalName()
                            .contains("$" + lambdaHolder.getSimpleName() + "$")));
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(new A().foo().get());
    }
  }

  interface Getter {
    String get();
  }

  static class A {
    Getter foo() {
      return () -> "A" + new B().foo().get();
    }
  }

  static class B {
    Getter foo() {
      return () -> "B";
    }
  }
}
