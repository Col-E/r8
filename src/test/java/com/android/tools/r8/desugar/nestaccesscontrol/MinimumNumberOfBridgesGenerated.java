// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.JAR;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getMainClass;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinimumNumberOfBridgesGenerated extends TestBase {

  @Test
  public void testOnlyRequiredBridges() throws Exception {
    if (parameters.isDexRuntime()) {
      d8CompilationResult.apply(parameters.getApiLevel()).inspect(this::assertOnlyRequiredBridges);
    }
    r8CompilationResult
        .apply(parameters.getBackend(), parameters.getApiLevel())
        .inspect(this::assertOnlyRequiredBridges);
  }

  public MinimumNumberOfBridgesGenerated(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntime(DexVm.Version.first())
        .withDexRuntime(DexVm.Version.last())
        .withAllApiLevels()
        .build();
  }

  private void assertOnlyRequiredBridges(CodeInspector inspector) {
    // The following 2 classes have an extra private member which does not require a bridge.

    // Two bridges for method and staticMethod.
    int methodNumBridges = parameters.isCfRuntime() ? 0 : 2;
    ClassSubject methodMainClass = inspector.clazz(getMainClass("methods"));
    assertEquals(
        methodNumBridges, methodMainClass.allMethods(FoundMethodSubject::isSynthetic).size());

    // Two bridges for method and staticMethod.
    int constructorNumBridges = parameters.isCfRuntime() ? 0 : 1;
    ClassSubject constructorMainClass = inspector.clazz(getMainClass("constructors"));
    assertEquals(
        constructorNumBridges,
        constructorMainClass.allMethods(FoundMethodSubject::isSynthetic).size());

    // Four bridges for field and staticField, both get & set.
    int fieldNumBridges = parameters.isCfRuntime() ? 0 : 4;
    ClassSubject fieldMainClass = inspector.clazz(getMainClass("fields"));
    assertEquals(
        fieldNumBridges, fieldMainClass.allMethods(FoundMethodSubject::isSynthetic).size());
  }

  private static Function<AndroidApiLevel, D8TestCompileResult> d8CompilationResult =
      memoizeFunction(MinimumNumberOfBridgesGenerated::compileAllNestsD8);

  private static BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> r8CompilationResult =
      memoizeBiFunction(MinimumNumberOfBridgesGenerated::compileAllNestsR8);

  private static D8TestCompileResult compileAllNestsD8(AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForD8(getStaticTemp())
        .addProgramFiles(JAR)
        .addOptionsModification(
            options -> {
              options.enableNestBasedAccessDesugaring = true;
            })
        .setMinApi(minApi)
        .compile();
  }

  private static R8TestCompileResult compileAllNestsR8(Backend backend, AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForR8(getStaticTemp(), backend)
        .noTreeShaking()
        .noMinification()
        .addKeepAllAttributes()
        .addOptionsModification(
            options -> {
              options.enableNestBasedAccessDesugaring = true;
            })
        .addProgramFiles(JAR)
        .setMinApi(minApi)
        .compile();
  }
}
