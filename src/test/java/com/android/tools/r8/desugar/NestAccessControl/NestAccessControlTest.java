// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.NestAccessControl;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

  private static final Path JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_BUILD_DIR).resolve("nestHostExample" + JAR_EXTENSION);
  private static final String PACKAGE_NAME = "nestHostExample.";
  private static final int NUMBER_OF_TEST_CLASSES = 15;

  private static final ImmutableMap<String, String> MAIN_CLASSES =
      ImmutableMap.of(
          "fields", "BasicNestHostWithInnerClassFields",
          "methods", "BasicNestHostWithInnerClassMethods",
          "constructors", "BasicNestHostWithInnerClassConstructors",
          "anonymous", "BasicNestHostWithAnonymousInnerClass",
          "all", "NestHostExample");
  private static final String ALL_EXPECTED_RESULT =
      StringUtils.lines(
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "staticInterfaceMethodstaticStaticInterfaceMethod",
          "staticInterfaceMethodstaticStaticInterfaceMethod",
          "staticInterfaceMethodstaticStaticInterfaceMethod",
          "staticInterfaceMethodstaticStaticInterfaceMethod");
  private static final ImmutableMap<String, String> EXPECTED_RESULTS =
      ImmutableMap.of(
          "fields",
              StringUtils.lines(
                  "RWnestFieldRWRWnestFieldRWRWnestField", "RWfieldRWRWfieldRWRWnestField"),
          "methods",
              StringUtils.lines(
                  "nestMethodstaticNestMethodstaticNestMethod",
                  "hostMethodstaticHostMethodstaticNestMethod"),
          "constructors", StringUtils.lines("field", "nest1SField"),
          "anonymous",
              StringUtils.lines(
                  "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethod"),
          "all", ALL_EXPECTED_RESULT);

  private static Function<AndroidApiLevel, D8TestCompileResult> d8CompilationResult =
      memoizeFunction(NestAccessControlTest::compileD8);

  private static BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> r8CompilationResult =
      memoizeBiFunction(NestAccessControlTest::compileR8);

  private static D8TestCompileResult compileD8(AndroidApiLevel minApi)
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

  private static R8TestCompileResult compileR8(Backend backend, AndroidApiLevel minApi)
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

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  private static String getMainClass(String id) {
    return PACKAGE_NAME + MAIN_CLASSES.get(id);
  }

  private static String getExpectedResult(String id) {
    return EXPECTED_RESULTS.get(id);
  }


  public NestAccessControlTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public void testJavaAndD8(String id, boolean d8Success) throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramFiles(JAR)
          .run(parameters.getRuntime(), getMainClass(id))
          .assertSuccessWithOutput(getExpectedResult(id));
    } else {
      assert parameters.isDexRuntime();
      D8TestRunResult run =
          d8CompilationResult
              .apply(parameters.getApiLevel())
              .run(parameters.getRuntime(), getMainClass(id));
      if (d8Success) {
        run.assertSuccessWithOutput(getExpectedResult(id));
      } else {
        if (parameters.isDexRuntime()
            && (parameters.getRuntime().asDex().getVm().getVersion() == Version.V6_0_1
                || parameters.getRuntime().asDex().getVm().getVersion() == Version.V5_1_1)) {
          run.assertFailure(); // different message, same error
        } else {
          run.assertFailureWithErrorThatMatches(containsString("IllegalAccessError"));
        }
      }
    }
  }

  @Test
  public void testJavaAndD8() throws Exception {
    // TODO(b/130529390): As features are implemented, set success to true in each line.
    testJavaAndD8("methods", true);
    testJavaAndD8("fields", true);
    testJavaAndD8("constructors", false);
    testJavaAndD8("anonymous", true);
    testJavaAndD8("all", false);
  }

  public void testR8(String id, boolean r8Success) throws Exception {
    R8TestRunResult result =
        r8CompilationResult
            .apply(parameters.getBackend(), parameters.getApiLevel())
            .run(parameters.getRuntime(), getMainClass(id));
    if (r8Success) {
      result.assertSuccessWithOutput(getExpectedResult(id));
      if (parameters.isCfRuntime()) {
        result.inspect(NestAccessControlTest::checkNestMateAttributes);
      }
    } else {
      if (parameters.isDexRuntime()
          && (parameters.getRuntime().asDex().getVm().getVersion() == Version.V6_0_1
              || parameters.getRuntime().asDex().getVm().getVersion() == Version.V5_1_1)) {
        result.assertFailure(); // different message, same error
      } else {
        result.assertFailureWithErrorThatMatches(containsString("IllegalAccessError"));
      }
    }
  }

  @Test
  public void testMethodsAccessR8() throws Exception {
    // TODO(b/130529390): As features are implemented, set success to true in each line.
    testR8("methods", true);
    testR8("fields", parameters.isCfRuntime());
    testR8("constructors", parameters.isCfRuntime());
    testR8("anonymous", parameters.isCfRuntime());
    testR8("all", parameters.isCfRuntime());
  }

  private static void checkNestMateAttributes(CodeInspector inspector) {
    // Interface method desugaring may add extra classes
    assertTrue(NUMBER_OF_TEST_CLASSES <= inspector.allClasses().size());
    ImmutableList<String> outerClassNames = MAIN_CLASSES.values().asList();
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
