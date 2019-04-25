// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.NestAccessControl;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAccessControlTest extends TestBase {

  private static final Path EXAMPLE_DIR = Paths.get(ToolHelper.EXAMPLES_JAVA11_BUILD_DIR);
  private static final Path JAR = EXAMPLE_DIR.resolve("nestHostExample" + JAR_EXTENSION);

  private static final String EXPECTED = StringUtils.lines(
      "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
      "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
      "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
      "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
      "staticInterfaceMethodstaticStaticInterfaceMethod",
      "staticInterfaceMethodstaticStaticInterfaceMethod",
      "staticInterfaceMethodstaticStaticInterfaceMethod",
      "staticInterfaceMethodstaticStaticInterfaceMethod");
  private static final ImmutableMap<String, String> MAIN_CLASSES_AND_EXPECTED_RESULTS = ImmutableMap
      .of(
          "BasicNestHostWithInnerClass", StringUtils.lines(
              "nest1SFieldstaticNestFieldstaticNestFieldnestMethodstaticNestMethodstaticNestMethod",
              "fieldstaticFieldstaticNestFieldhostMethodstaticHostMethodstaticNestMethod"),
          "BasicNestHostWithAnonymousInnerClass", StringUtils
              .lines("fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethod"),
          "NestHostExample", EXPECTED);

  private static Function<AndroidApiLevel, D8TestCompileResult> d8CompilationResult =
      memoizeFunction(NestAccessControlTest::compileD8);

  private static BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> r8CompilationResult =
      memoizeBiFunction(NestAccessControlTest::compileR8);

  private static D8TestCompileResult compileD8(AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForD8(getStaticTemp()).addProgramFiles(JAR).setMinApi(minApi).compile();
  }

  private static R8TestCompileResult compileR8(Backend backend, AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForR8(getStaticTemp(), backend)
        .noTreeShaking()
        .noMinification()
        .addKeepAllAttributes()
        .addProgramFiles(JAR)
        .setMinApi(minApi)
        .compile();
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  public NestAccessControlTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJavaAndD8() throws Exception {
    for (ImmutableMap.Entry<String,String> entry : MAIN_CLASSES_AND_EXPECTED_RESULTS.entrySet()) {
      if (parameters.isCfRuntime()) {
        testForJvm()
            .addProgramFiles(JAR)
            .run(parameters.getRuntime(), "nestHostExample."+ entry.getKey())
            .assertSuccessWithOutput(entry.getValue());
      } else {
        assert parameters.isDexRuntime();
        d8CompilationResult
            .apply(parameters.getApiLevel())
            .run(parameters.getRuntime(), "nestHostExample."+ entry.getKey())
            // TODO(b/130529390): Assert expected once fixed.
            .assertFailureWithErrorThatMatches(containsString("IllegalAccessError"));
      }
    }
  }

  @Test
  public void testR8() throws Exception {
    for (ImmutableMap.Entry<String,String> entry : MAIN_CLASSES_AND_EXPECTED_RESULTS.entrySet()) {
      R8TestRunResult result =
          r8CompilationResult
              .apply(parameters.getBackend(), parameters.getApiLevel())
              .run(parameters.getRuntime(), "nestHostExample."+ entry.getKey());
      if (parameters.isCfRuntime()) {
        result.assertSuccessWithOutput(entry.getValue());
        result.inspect(NestAccessControlTest::checkNestMateAttributes);
      } else {
        // TODO(b/130529390): Assert expected once fixed.
        result.assertFailureWithErrorThatMatches(containsString("IllegalAccessError"));
      }
    }
  }

  private static void checkNestMateAttributes(CodeInspector inspector) {
    assertEquals(11, inspector.allClasses().size());
    ImmutableSet<String> outerClassNames =
        ImmutableSet.of(
            "NestHostExample",
            "BasicNestHostWithInnerClass",
            "BasicNestHostWithAnonymousInnerClass");
    inspector.forAllClasses(
        classSubject -> {
          DexClass dexClass = classSubject.getDexClass();
          assertTrue(dexClass.isInANest());
          if (outerClassNames.contains(dexClass.type.getName())) {
            assertNull(dexClass.getNestHostClassAttribute());
            assertFalse(dexClass.getNestMembersClassAttributes().isEmpty());
          } else {
            assertTrue(dexClass.getNestMembersClassAttributes().isEmpty());
            assertTrue(
                outerClassNames.contains(
                    dexClass.getNestHostClassAttribute().getNestHost().getName()));
          }
        });
  }
}
