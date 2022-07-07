// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.C;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.E;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.F;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.I;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.Impl;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodHandleTestRunner extends TestBase {
  static final Class<?> CLASS = MethodHandleTest.class;

  enum LookupType {
    DYNAMIC,
    CONSTANT,
  }

  enum MinifyMode {
    NONE,
    MINIFY,
  }

  private String getExpected() {
    return StringUtils.lines(
        "C 42", "svi 1", "sji 2", "svic 3", "sjic 4", "vvi 5", "vji 6", "vvic 7", "vjic 8", "svi 9",
        "sji 10", "svic 11", "sjic 12", "dvi 13", "dji 14", "dvic 15", "djic 16", "C 21", "37",
        "37");
  }

  private final TestParameters parameters;
  private final LookupType lookupType;
  private final MinifyMode minifyMode;

  @Parameters(name = "{0}, lookup:{1}, minify:{2}")
  public static List<Object[]> data() {
    List<Object[]> res = new ArrayList<>();
    for (TestParameters params :
        TestParameters.builder()
            .withCfRuntimes()
            .withDexRuntimesStartingFromExcluding(Version.V7_0_0)
            // .withApiLevelsStartingAtIncluding(AndroidApiLevel.P)
            .withAllApiLevels()
            .build()) {
      for (LookupType lookupType : LookupType.values()) {
        for (MinifyMode minifyMode : MinifyMode.values()) {
          if (lookupType == LookupType.DYNAMIC && minifyMode == MinifyMode.MINIFY) {
            // Skip because we don't keep the members looked up dynamically.
            continue;
          }
          res.add(new Object[] {params, lookupType.name(), minifyMode.name()});
        }
      }
    }
    return res;
  }

  public MethodHandleTestRunner(TestParameters parameters, String lookupType, String minifyMode) {
    this.parameters = parameters;
    this.lookupType = LookupType.valueOf(lookupType);
    this.minifyMode = MinifyMode.valueOf(minifyMode);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), CLASS.getName())
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime() && minifyMode == MinifyMode.NONE);
    testForD8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(getTransformedClasses())
        .mapUnsupportedFeaturesToWarnings()
        // TODO(b/238175192): remove again when resolved
        .addOptionsModification(
            options -> options.enableUnrepresentableInDexInstructionRemoval = true)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), CLASS.getName())
        .apply(this::checkResult);
  }

  @Test
  public void testR8() throws Exception {
    R8TestBuilder<?> builder =
        testForR8(parameters.getBackend())
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(getInputClasses())
            .addProgramClassFileData(getTransformedClasses())
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addNoVerticalClassMergingAnnotations()
            // TODO(b/238175192): remove again when resolved
            .addOptionsModification(
                options -> options.enableUnrepresentableInDexInstructionRemoval = true);
    if (minifyMode == MinifyMode.MINIFY) {
      builder
          .enableProguardTestOptions()
          .addKeepMainRule(MethodHandleTest.class)
          .addKeepRules(
              // Prevent the second argument of C.svic(), C.sjic(), I.sjic() and I.svic() from
              // being removed although they are never used unused. This is needed since these
              // methods are accessed reflectively.
              "-keep,allowobfuscation public class " + typeName(C.class) + " {",
              "  static void svic(int, char);",
              "  static long sjic(int, char);",
              "}",
              "-keep,allowobfuscation public interface " + typeName(I.class) + " {",
              "  static long sjic(int, char);",
              "  static void svic(int, char);",
              "}");
      // TODO(b/235810300): The compiler fails with assertion in AppInfoWithLiveness.
      if (lookupType == LookupType.CONSTANT && hasConstMethodCompileSupport()) {
        builder.allowDiagnosticMessages();
        assertThrows(CompilationFailedException.class, builder::compile);
        return;
      }
    } else {
      builder.noTreeShaking();
      builder.noMinification();
    }
    builder
        .allowDiagnosticMessages()
        .mapUnsupportedFeaturesToWarnings()
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), CLASS.getCanonicalName())
        .apply(this::checkResult);
  }

  private boolean hasConstMethodCompileSupport() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithConstMethodHandleSupport());
  }

  private boolean hasInvokePolymorphicCompileSupport() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithInvokePolymorphicSupport());
  }

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    if ((lookupType == LookupType.DYNAMIC && !hasInvokePolymorphicCompileSupport())
        || (lookupType == LookupType.CONSTANT && !hasConstMethodCompileSupport())) {
      diagnostics
          .assertAllWarningsMatch(diagnosticType(UnsupportedFeatureDiagnostic.class))
          .assertOnlyWarnings();
    } else {
      diagnostics.assertNoMessages();
    }
  }

  private void checkResult(TestRunResult<?> result) {
    if (lookupType == LookupType.DYNAMIC && !hasInvokePolymorphicCompileSupport()) {
      result
          .assertFailureWithErrorThatThrows(RuntimeException.class)
          .assertStderrMatches(containsString("invoke-polymorphic"));
      return;
    }
    if (lookupType == LookupType.CONSTANT && !hasConstMethodCompileSupport()) {
      result
          .assertFailureWithErrorThatThrows(RuntimeException.class)
          .assertStderrMatches(containsString("const-method-handle"));
      return;
    }
    result.assertSuccessWithOutput(getExpected());
  }

  private List<Class<?>> getInputClasses() {
    Builder<Class<?>> builder =
        ImmutableList.<Class<?>>builder().add(C.class, I.class, Impl.class, E.class, F.class);
    if (lookupType == LookupType.DYNAMIC) {
      builder.add(MethodHandleTest.class);
    }
    return builder.build();
  }

  private List<byte[]> getTransformedClasses() throws Exception {
    if (lookupType == LookupType.DYNAMIC) {
      return ImmutableList.of();
    }
    return ImmutableList.of(MethodHandleDump.getTransformedClass());
  }
}
