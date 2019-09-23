// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dexsplitter;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8FeatureSplitTest extends SplitterTestBase {

  private static String EXPECTED = "Hello world";

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().build();
  }

  private final TestParameters parameters;

  public R8FeatureSplitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static FeatureSplit emptySplitProvider(FeatureSplit.Builder builder) {
    builder
        .addProgramResourceProvider(() -> ImmutableList.of())
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    return builder.build();
  }

  @Test
  public void simpleApiTest() throws CompilationFailedException, IOException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(HelloWorld.class)
        .setMinApi(parameters.getRuntime())
        .addFeatureSplit(R8FeatureSplitTest::emptySplitProvider)
        .addKeepMainRule(HelloWorld.class)
        .compile()
        .run(parameters.getRuntime(), HelloWorld.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testTwoFeatures() throws CompilationFailedException, IOException, ExecutionException {
    Path basePath = temp.newFile("base.zip").toPath();
    Path feature1Path = temp.newFile("feature1.zip").toPath();
    Path feature2Path = temp.newFile("feature2.zip").toPath();

    testForR8(parameters.getBackend())
        .addProgramClasses(BaseClass.class, RunInterface.class, SplitRunner.class)
        .setMinApi(parameters.getRuntime())
        .addFeatureSplit(
            builder -> simpleSplitProvider(builder, feature1Path, temp, FeatureClass.class))
        .addFeatureSplit(
            builder -> simpleSplitProvider(builder, feature2Path, temp, FeatureClass2.class))
        .addKeepAllClassesRule()
        .compile()
        .writeToZip(basePath);

    CodeInspector baseInspector = new CodeInspector(basePath);
    assertTrue(baseInspector.clazz(BaseClass.class).isPresent());

    CodeInspector feature1Inspector = new CodeInspector(feature1Path);
    assertEquals(feature1Inspector.allClasses().size(), 1);
    assertTrue(feature1Inspector.clazz(FeatureClass.class).isPresent());

    CodeInspector feature2Inspector = new CodeInspector(feature2Path);
    assertEquals(feature2Inspector.allClasses().size(), 1);
    assertTrue(feature2Inspector.clazz(FeatureClass2.class).isPresent());

    // Sanity check, we can't call the Feature from the base directly.
    ProcessResult result =
        runFeatureOnArt(BaseClass.class, basePath, feature1Path, parameters.getRuntime());
    assertTrue(result.exitCode != 0);

    result = runFeatureOnArt(FeatureClass.class, basePath, feature1Path, parameters.getRuntime());
    assertEquals(result.exitCode, 0);
    assertEquals(result.stdout, StringUtils.lines("Testing base", "Testing feature"));

    result = runFeatureOnArt(FeatureClass2.class, basePath, feature2Path, parameters.getRuntime());
    assertEquals(result.exitCode, 0);
    assertEquals(result.stdout, StringUtils.lines("Testing second"));
  }

  public static class HelloWorld {
    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }

  public static class BaseClass implements RunInterface {
    @Override
    public void run() {
      new FeatureClass().test();
    }

    public void test() {
      System.out.println("Testing base");
    }
  }

  public static class FeatureClass implements RunInterface {
    public void test() {
      System.out.println("Testing feature");
    }

    @Override
    public void run() {
      new BaseClass().test();
      test();
    }
  }

  public static class FeatureClass2 implements RunInterface {
    public void test() {
      System.out.println("Testing second");
    }

    @Override
    public void run() {
      test();
    }
  }
}
