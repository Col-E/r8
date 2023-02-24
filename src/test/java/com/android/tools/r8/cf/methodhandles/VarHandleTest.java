// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.methodhandles;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.UnsupportedInvokePolymorphicMethodHandleDiagnostic;
import com.android.tools.r8.examples.JavaExampleClassProxy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Test for VarHandle (these are a refactoring of the old example test setup.) */
@RunWith(Parameterized.class)
public class VarHandleTest extends TestBase {

  private static final String PKG = "varhandle";
  private static final String EXAMPLE = "examplesJava9/" + PKG;
  private final JavaExampleClassProxy MAIN =
      new JavaExampleClassProxy(EXAMPLE, PKG + ".VarHandleTests");

  private static final String EXPECTED = StringUtils.lines("true", "false");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  public VarHandleTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean hasInvokePolymorphicCompileSupport() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithInvokePolymorphicSupport());
  }

  private boolean hasMethodHandlesClass() {
    return parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O);
  }

  private boolean hasFindStaticVarHandleMethod() {
    // API docs list this as present from T(33), but it was included from 28
    return parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.P);
  }

  private boolean hasVarHandleInLibrary() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.T);
  }

  public List<Path> getProgramInputs() {
    return ImmutableList.of(JavaExampleClassProxy.examplesJar(EXAMPLE));
  }

  @Test
  public void testReference() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(getProgramInputs())
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    assumeFalse(
        "TODO(b/204855476): The default VM throws unsupported. Ignore it and reconsider for 8.0.0",
        parameters.isDexRuntimeVersion(Version.DEFAULT));
    testForD8()
        .addProgramFiles(getProgramInputs())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (hasInvokePolymorphicCompileSupport()) {
                diagnostics.assertNoMessages();
              } else {
                diagnostics
                    .assertAllWarningsMatch(
                        diagnosticType(UnsupportedInvokePolymorphicMethodHandleDiagnostic.class))
                    .assertOnlyWarnings();
              }
            })
        .run(parameters.getRuntime(), MAIN.typeName())
        .applyIf(
            !hasMethodHandlesClass(),
            r ->
                r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class)
                    .assertStderrMatches(containsString("java.lang.invoke.MethodHandles")),
            !hasFindStaticVarHandleMethod(),
            r ->
                r.assertFailureWithErrorThatThrows(NoSuchMethodError.class)
                    .assertStderrMatches(containsString("findStaticVarHandle")),
            !hasInvokePolymorphicCompileSupport(),
            r ->
                r.assertFailureWithErrorThatThrows(RuntimeException.class)
                    .assertStderrMatches(containsString("invoke-polymorphic")),
            r -> r.assertSuccessWithOutput(EXPECTED));
  }

  @Test
  public void testR8() throws Throwable {
    // This just tests R8 on the targets where the program is fully supported.
    assumeTrue(hasInvokePolymorphicCompileSupport() && hasFindStaticVarHandleMethod());
    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramInputs())
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(MAIN.typeName())
        .applyIf(!hasVarHandleInLibrary(), b -> b.addDontWarn("java.lang.invoke.VarHandle"))
        .run(parameters.getRuntime(), MAIN.typeName())
        .assertSuccessWithOutput(EXPECTED);
  }
}
