// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b134858535;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EventPublisherTest extends TestBase {

  private final KotlinTestParameters kotlinTestParameters;
  private final TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        getKotlinTestParameters()
            .withAllCompilers()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .build(),
        TestBase.getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public EventPublisherTest(KotlinTestParameters kotlinTestParameters, TestParameters parameters) {
    this.kotlinTestParameters = kotlinTestParameters;
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
        .addProgramFiles(kotlinTestParameters.getCompiler().getKotlinStdlibJar())
        .addKeepClassRules(Interface.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertClassesNotMerged(EventPublisher$b.class))
        .compile();
  }

  public static class Main {

    public static void main(String[] args) {
      new EventPublisher$b().apply("foo");
    }
  }
}
