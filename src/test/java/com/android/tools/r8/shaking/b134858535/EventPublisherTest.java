// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b134858535;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EventPublisherTest extends TestBase {

  private final KotlinCompiler kotlinc;
  private final TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        getKotlinCompilers(),
        TestBase.getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public EventPublisherTest(KotlinCompiler kotlinc, TestParameters parameters) {
    this.kotlinc = kotlinc;
    this.parameters = parameters;
  }

  @Test
  public void testPrivateMethodsInLambdaClass() throws CompilationFailedException {
    // This test only tests if the dump can be compiled without errors.
    testForR8(parameters.getBackend())
        .addProgramClasses(
            Main.class,
            Interface.class,
            EventEntity.class,
            Flowable.class,
            SdkConfiguration.class,
            TrackBatchEventResponse.class)
        .addProgramClassFileData(EventPublisher$bDump.dump())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .addKeepClassRules(Interface.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedLambdaClassesInspector(
            inspector -> inspector.assertClassNotMerged(EventPublisher$b.class))
        .compile();
  }

  public static class Main {

    public static void main(String[] args) {
      new EventPublisher$b().apply("foo");
    }
  }
}
