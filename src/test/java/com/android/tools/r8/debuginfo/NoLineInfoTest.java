// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Label;

@RunWith(Parameterized.class)
public class NoLineInfoTest extends TestBase {

  private static final String INPUT_SOURCE_FILE = "InputSourceFile.java";
  private static final String CUSTOM_SOURCE_FILE = "TaggedSourceFile";
  private static final String DEFAULT_SOURCE_FILE = "SourceFile";
  private static final String UNKNOWN_SOURCE_FILE = "Unknown Source";

  private final TestParameters parameters;
  private final boolean customSourceFile;

  @Parameterized.Parameters(name = "{0}, custom-sf:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public NoLineInfoTest(TestParameters parameters, boolean customSourceFile) {
    this.parameters = parameters;
    this.customSourceFile = customSourceFile;
  }

  private byte[] getTestClassTransformed() throws IOException {
    return transformer(TestClass.class)
        .setSourceFile(INPUT_SOURCE_FILE)
        .addMethodTransformer(
            new MethodTransformer() {
              private final Map<MethodReference, Integer> lines = new HashMap<>();

              @Override
              public void visitLineNumber(int line, Label start) {
                Integer nextLine = lines.getOrDefault(getContext().getReference(), 0);
                if (nextLine > 0) {
                  super.visitLineNumber(nextLine, start);
                }
                // Increment the actual line content by 100 so that each one is clearly distinct
                // from a PC value for any of the methods.
                lines.put(getContext().getReference(), nextLine + 100);
              }
            })
        .transform();
  }

  public boolean isRuntimeWithPcAsLineNumberSupport() {
    return parameters.isDexRuntime()
        && parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport());
  }

  public boolean isCompileWithPcAsLineNumberSupport() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport());
  }

  @Test
  public void testReference() throws Exception {
    assumeFalse(customSourceFile);
    testForRuntime(parameters)
        .addProgramClassFileData(getTestClassTransformed())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectStackTrace(
            stacktrace -> {
              if (isRuntimeWithPcAsLineNumberSupport()) {
                // On VMs with PC support the lack of a line will emit the PC instead.
                assertThat(stacktrace, StackTrace.isSame(getExpectedInputStacktraceOnPcVms()));
              } else {
                assertThat(stacktrace, StackTrace.isSame(getExpectedInputStacktrace()));
              }
            });
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTestClassTransformed())
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(o -> o.testing.forcePcBasedEncoding = true)
        .applyIf(
            customSourceFile,
            b -> b.getBuilder().setSourceFileProvider(environment -> CUSTOM_SOURCE_FILE))
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectOriginalStackTrace(
            stackTrace ->
                assertThat(
                    "Unexpected residual stacktrace",
                    stackTrace,
                    StackTrace.isSame(getResidualStacktrace())))
        .inspectStackTrace(
            stacktrace ->
                assertThat(
                    "Unexpected input-source stacktrace",
                    stacktrace,
                    StackTrace.isSame(getUnexpectedRetracedStacktrace())));
  }

  private StackTraceLine line(String file, String method, int line) {
    return StackTraceLine.builder()
        .setClassName(typeName(TestClass.class))
        .setFileName(file)
        .setMethodName(method)
        .setLineNumber(line)
        .build();
  }

  // Normal reference line from the input.
  private StackTraceLine inputLine(String method, int line) {
    return line(INPUT_SOURCE_FILE, method, line);
  }

  // Line as printed for PC supporting VMs when the line is absent (D8 compilation).
  private StackTraceLine inputPcLine(String method, int pc) {
    return line(UNKNOWN_SOURCE_FILE, method, pc);
  }

  // Residual line depends on the source file parameter.
  private StackTraceLine residualLine(String method, int line) {
    String defaultFile =
        isCompileWithPcAsLineNumberSupport() ? UNKNOWN_SOURCE_FILE : DEFAULT_SOURCE_FILE;
    String file = customSourceFile ? CUSTOM_SOURCE_FILE : defaultFile;
    return line(file, method, line);
  }

  // This is the real "reference" stack trace as given by JVM on inputs and should be retraced to.
  private StackTrace getExpectedInputStacktrace() {
    return StackTrace.builder()
        .add(inputLine("foo", -1))
        .add(inputLine("bar", -1))
        .add(inputLine("baz", -1))
        .add(inputLine("main", 200))
        .build();
  }

  // When D8 compiling reference inputs directly there is (currently) no way to recover from the PC
  // printing. Thus, this is the expected stack trace on those VMs.
  private StackTrace getExpectedInputStacktraceOnPcVms() {
    return StackTrace.builder()
        .add(inputPcLine("foo", 1))
        .add(inputPcLine("bar", 0))
        .add(inputPcLine("baz", 0))
        .add(inputLine("main", 200))
        .build();
  }

  // TODO(b/232212653): The retraced stack trace should be the same as `getExpectedInputStacktrace`.
  private StackTrace getUnexpectedRetracedStacktrace() {

    // TODO(b/232212653): Retracing the PC 1 preserves it but it should map to <noline>
    StackTraceLine fooLine =
        isRuntimeWithPcAsLineNumberSupport() ? inputLine("foo", 1) : inputLine("foo", -1);

    // TODO(b/232212653): Normal line-opt will cause a single-line mapping. Retrace should not
    //  optimize that to mean it represents a single possible line. (<noline> should not match 1:x).
    StackTraceLine barLine = parameters.isCfRuntime() ? inputLine("bar", 100) : inputLine("bar", 0);

    // TODO(b/232212653): The retracing in CF where the line table is preserved is incorrect.
    //  same issue as for bar.
    StackTraceLine bazLine = parameters.isCfRuntime() ? inputLine("baz", 100) : inputLine("baz", 0);

    return StackTrace.builder()
        .add(fooLine)
        .add(barLine)
        .add(bazLine)
        .add(inputLine("main", 200))
        .build();
  }

  private StackTrace getResidualStacktrace() {
    if (parameters.isCfRuntime()) {
      // For CF compilation the line number increments are used and each preamble is retained as
      // such. This is the expected output.
      return StackTrace.builder()
          .add(residualLine("foo", -1))
          .add(residualLine("bar", -1))
          .add(residualLine("baz", -1))
          .add(residualLine("main", 101)) // TODO(b/232212653) Why is this 101?
          .build();
    }

    // TODO(b/232212653): The correct line should be with CUSTOM_SOURCE_FILE and PC 1.
    //   When compiling with debug info encoding PCs this is almost the expected output. The issue
    //   being that even "foo" should have PC based encoding too to ensure the SF remains on
    //   newer VMs too.
    StackTraceLine fooLine =
        isRuntimeWithPcAsLineNumberSupport()
            ? line(UNKNOWN_SOURCE_FILE, "foo", 1)
            : residualLine("foo", -1);

    return StackTrace.builder()
        .add(fooLine)
        .add(residualLine("bar", 0))
        .add(residualLine("baz", 0))
        .add(residualLine("main", 6))
        .build();
  }

  // Test with a call stack where each initial line is stripped (see getTestClassTransformed)
  // Line numbers are indicated in comments. The stacktrace is marked by ***.
  static class TestClass {

    public static void nop() {}

    public static void foo() {
      throw null; // noline ***
    }

    public static void bar() {
      foo(); // noline ***
      nop(); // 100
    }

    public static void baz() {
      bar(); // noline ***
      nop(); // 100
      nop(); // 200
    }

    public static void main(String[] args) {
      nop(); // noline
      nop(); // 100
      baz(); // 200 ***
    }
  }
}
