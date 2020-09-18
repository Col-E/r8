// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b159688129;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaGroupGCLimitTest extends TestBase {

  private final TestParameters parameters;
  private final int LAMBDA_HOLDER_LIMIT = 50;
  private final int LAMBDAS_PER_CLASS_LIMIT = 100;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public LambdaGroupGCLimitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    String PKG_NAME = LambdaGroupGCLimitTest.class.getPackage().getName();
    R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getKotlinStdlibJar())
            .setMinApi(parameters.getApiLevel())
            .noMinification();
    Path classFiles = temp.newFile("classes.jar").toPath();
    List<byte[]> classFileData = new ArrayList<>();
    for (int mainId = 0; mainId < LAMBDA_HOLDER_LIMIT; mainId++) {
      classFileData.add(MainKtDump.dump(mainId, LAMBDAS_PER_CLASS_LIMIT));
      for (int lambdaId = 0; lambdaId < LAMBDAS_PER_CLASS_LIMIT; lambdaId++) {
        classFileData.add(MainKt$main$1Dump.dump(mainId, lambdaId));
      }
      testBuilder.addKeepClassAndMembersRules(PKG_NAME + ".MainKt" + mainId);
    }
    writeClassFileDataToJar(classFiles, classFileData);
    R8TestCompileResult compileResult = testBuilder.addProgramFiles(classFiles).compile();
    Path path = compileResult.writeToZip();
    compileResult
        .run(parameters.getRuntime(), PKG_NAME + ".MainKt0")
        .assertSuccessWithOutputLines("3")
        .inspect(
            codeInspector -> {
              List<FoundClassSubject> lambdaGroups =
                  codeInspector.allClasses().stream()
                      .filter(c -> c.getFinalName().contains("LambdaGroup"))
                      .collect(Collectors.toList());
              assertEquals(1, lambdaGroups.size());
            });
    Path oatFile = temp.newFile("out.oat").toPath();
    ProcessResult processResult =
        ToolHelper.runDex2OatRaw(path, oatFile, parameters.getRuntime().asDex().getVm());
    assertEquals(0, processResult.exitCode);
    assertThat(
        processResult.stderr, not(containsString("Method exceeds compiler instruction limit")));
  }
}
