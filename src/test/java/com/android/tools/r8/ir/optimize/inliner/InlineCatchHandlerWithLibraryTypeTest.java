// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This test extends that of Regress131349148 for other API-introduced exceptions. */
@RunWith(Parameterized.class)
public class InlineCatchHandlerWithLibraryTypeTest extends TestBase {

  private static final String TEMPLATE_CODE_EXCEPTION_BINARY_NAME = "java/lang/Exception";

  // A subset of exception types introduced in API levels between 16 to 24.
  private static final Map<String, Integer> EXCEPTIONS =
      ImmutableMap.<String, Integer>builder()
          // VM 4.0.4 (api 15) is the first VM we have so no need to go prior to that.
          .put("android.media.MediaCryptoException", 16)
          .put("android.view.WindowManager$InvalidDisplayException", 17)
          .put("android.media.DeniedByServerException", 18)
          .put("android.media.ResourceBusyException", 19)
          .put("java.lang.ReflectiveOperationException", 19)
          .put("javax.crypto.AEADBadTagException", 19)
          .put("android.system.ErrnoException", 21)
          .put("android.media.MediaDrmResetException", 23)
          .put("java.io.UncheckedIOException", 24)
          .put("java.util.concurrent.CompletionException", 24)
          // Verify error was fixed in 21 so up to 24 should catch post-fix issues.
          .build();

  private static final String EXPECTED = StringUtils.lines("Done...");

  private final TestParameters parameters;
  private final String exception;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> params() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        new TreeSet<>(EXCEPTIONS.keySet()));
  }

  public InlineCatchHandlerWithLibraryTypeTest(TestParameters parameters, String exception) {
    this.parameters = parameters;
    this.exception = exception;
  }

  private String getExceptionBinaryName() {
    return DescriptorUtils.getBinaryNameFromJavaType(exception);
  }

  private byte[] getClassWithCatchHandler() throws IOException {
    return transformer(ClassWithCatchHandler.class)
        .transformTryCatchBlock(
            "methodWithCatch",
            (start, end, handler, type, continuation) -> {
              String newType =
                  type.equals(TEMPLATE_CODE_EXCEPTION_BINARY_NAME)
                      ? getExceptionBinaryName()
                      : type;
              continuation.visitTryCatchBlock(start, end, handler, newType);
            })
        .transform();
  }

  private boolean compilationTargetIsMissingExceptionType() {
    // A CF target could target any API in the end.
    return parameters.isCfRuntime()
        || parameters.getApiLevel().getLevel() < EXCEPTIONS.get(exception);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getClassWithCatchHandler())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        // Use the latest library so that all of the exceptions are defined.
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .compile()
        .inspect(this::checkInlined)
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResult);
  }

  private void checkResult(R8TestRunResult runResult) {
    // The bootclasspath for our build of 4.4.4 does not contain various bits. Allow verify error.
    if (!compilationTargetIsMissingExceptionType()
        && parameters.getRuntime().asDex().getVm().getVersion().equals(Version.V4_4_4)
        && (exception.startsWith("android.media") || exception.startsWith("android.view"))) {
      runResult.assertFailureWithErrorThatThrows(VerifyError.class);
      return;
    }
    // Correct compilation should ensure that all programs run without error.
    runResult.assertSuccessWithOutput(EXPECTED);
  }

  private void checkInlined(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    boolean mainHasInlinedCatchHandler =
        Streams.stream(classSubject.mainMethod().iterateTryCatches())
            .anyMatch(tryCatch -> tryCatch.isCatching(exception));
    if (compilationTargetIsMissingExceptionType()) {
      assertFalse(mainHasInlinedCatchHandler);
    } else {
      assertTrue(mainHasInlinedCatchHandler);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      if (args.length == 200) {
        // Never called
        ClassWithCatchHandler.methodWithCatch();
      }
      System.out.println("Done...");
    }
  }

  static class ClassWithCatchHandler {

    @NeverInline
    public static void maybeThrow() {
      if (System.nanoTime() > 0) {
        throw new RuntimeException();
      }
    }

    public static void methodWithCatch() {
      try {
        maybeThrow();
      } catch (Exception e) {
        // We must use the exception, otherwise there is no move-exception that triggers the
        // verification error.
        System.out.println(e.getClass().getName());
      }
    }
  }
}
