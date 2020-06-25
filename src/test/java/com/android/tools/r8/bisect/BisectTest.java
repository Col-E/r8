// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bisect;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.bisect.BisectOptions.Result;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BisectTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return TestParametersBuilder.builder().withNoneRuntime().build();
  }

  public BisectTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final String[] CLASSES = {"A", "B", "C", "D", "E", "F", "G", "H"};
  private final String ERRONEOUS_CLASS = "F";
  private final String ERRONEOUS_METHOD = "foo";
  private final String VALID_METHOD = "bar";

  // Set during build to more easily inspect later.
  private MethodSignature erroneousMethodSignature = null;

  // Build "good" application with no method in "F".
  AndroidApp buildGood() throws Exception {
    SmaliBuilder builderGood = new SmaliBuilder();
    for (String clazz : CLASSES) {
      builderGood.addClass(clazz);
      builderGood.addStaticMethod(
          "void", VALID_METHOD, ImmutableList.of(), 0, "return-void");
    }
    return AndroidApp.builder().addDexProgramData(builderGood.compile(), Origin.unknown()).build();
  }

  AndroidApp buildBad() throws Exception {
    // Build "bad" application with a method "foo" in "F".
    SmaliBuilder builderBad = new SmaliBuilder();
    for (String clazz : CLASSES) {
      builderBad.addClass(clazz);
      if (clazz.equals(ERRONEOUS_CLASS)) {
        erroneousMethodSignature = builderBad.addStaticMethod(
            "void", ERRONEOUS_METHOD, ImmutableList.of(), 0, "return-void");
      } else {
        builderBad.addStaticMethod(
            "void", VALID_METHOD, ImmutableList.of(), 0, "return-void");
      }
    }
    return AndroidApp.builder().addDexProgramData(builderBad.compile(), Origin.unknown()).build();
  }

  @Test
  public void bisectWithExternalCommand() throws Exception {
    AndroidApp goodInput = buildGood();
    AndroidApp badInput = buildBad();
    ExecutorService executor = Executors.newWorkStealingPool();
    DexProgramClass clazz = null;
    Path stateFile = temp.newFolder().toPath().resolve("state.txt");
    try {
      Result lastResult = Result.UNKNOWN;
      while (clazz == null) {
        InternalOptions options = new InternalOptions();
        Timing timing = Timing.empty();
        DexApplication appGood = new ApplicationReader(goodInput, options, timing).read();
        DexApplication appBad = new ApplicationReader(badInput, options, timing).read();
        BisectState state = new BisectState(appGood, appBad, stateFile);
        state.read();
        if (lastResult != Result.UNKNOWN) {
          state.setPreviousResult(lastResult);
        }
        Path output = temp.newFolder().toPath();
        clazz = Bisect.run(state, null, output, executor);
        state.write();
        if (clazz == null) {
          lastResult = command(new CodeInspector(output.resolve("classes.dex")));
        }
      }
    } finally {
      executor.shutdown();
    }
    assertEquals(clazz.type.toString(), ERRONEOUS_CLASS);
  }

  @Test
  public void bisectWithInternalCommand() throws Exception {
    InternalOptions options = new InternalOptions();
    Timing timing = Timing.empty();
    DexApplication appGood = new ApplicationReader(buildGood(), options, timing).read();
    DexApplication appBad = new ApplicationReader(buildBad(), options, timing).read();
    ExecutorService executor = Executors.newWorkStealingPool();
    try {
      BisectState state = new BisectState(appGood, appBad, null);
      DexProgramClass clazz =
          Bisect.run(
              state, app -> command(new CodeInspector(app)), temp.newFolder().toPath(), executor);
      assertEquals(clazz.type.toString(), ERRONEOUS_CLASS);
    } finally {
      executor.shutdown();
    }
  }

  private Result command(CodeInspector inspector) {
    if (inspector
        .clazz(ERRONEOUS_CLASS)
        .method(erroneousMethodSignature.returnType,
            erroneousMethodSignature.name,
            erroneousMethodSignature.parameterTypes)
        .isPresent()) {
      return Result.BAD;
    }
    return Result.GOOD;
  }
}
