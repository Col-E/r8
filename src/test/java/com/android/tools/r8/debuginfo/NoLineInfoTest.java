// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
        .setPredictiveLineNumbering()
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
        .setMinApi(parameters)
        .addOptionsModification(o -> o.testing.forcePcBasedEncoding = true)
        .applyIf(
            customSourceFile,
            b -> b.getBuilder().setSourceFileProvider(environment -> CUSTOM_SOURCE_FILE))
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class)
        .inspectFailure(
            i -> {
              if (parameters.isDexRuntime()) {
                Set<DexDebugInfo> debugInfos =
                    i.allClasses().stream()
                        .flatMap(c -> c.allMethods().stream())
                        .map(m -> m.getMethod().getCode().asDexCode().getDebugInfo())
                        .collect(Collectors.toSet());
                if (isCompileWithPcAsLineNumberSupport() && !customSourceFile) {
                  // If debug info is stripped all items are null pointers.
                  assertEquals(Collections.singleton(null), debugInfos);
                } else {
                  // If debug info remains it is two canonical items and one null pointer.
                  // The presence of 'null' debug info items is for methods with no actual lines at
                  // all.
                  assertEquals(3, debugInfos.size());
                }
              }
            })
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
                    StackTrace.isSame(
                        parameters.isCfRuntime()
                            ? getExpectedInputStacktrace()
                            : getUnexpectedRetracedStacktrace())));
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
    if (customSourceFile) {
      return line(CUSTOM_SOURCE_FILE, method, line);
    }
    if (isCompileWithPcAsLineNumberSupport()) {
      return line(UNKNOWN_SOURCE_FILE, method, line);
    }
    return line(DEFAULT_SOURCE_FILE, method, line);
  }

  private int getPcEncoding(int pc) {
    return isCompileWithPcAsLineNumberSupport() && !customSourceFile ? pc : (pc + 1);
  }

  // A residual line that is either null debug info or pc2pc mapping.
  private StackTraceLine residualPcOrNoLine(String method, int pc) {
    // If compiling with custom source file the debug info must be non-null and have a pc mapping.
    // Add one as pc2pc encoding shifts lines by one.
    if (customSourceFile) {
      return line(CUSTOM_SOURCE_FILE, method, getPcEncoding(pc));
    }
    // If debug info is null, then on native pc support VMs it will print "unknown" and pc.
    if (isRuntimeWithPcAsLineNumberSupport()) {
      return line(UNKNOWN_SOURCE_FILE, method, pc);
    }
    // On old runtimes it will print "default" and no line info.
    return line(DEFAULT_SOURCE_FILE, method, -1);
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
    assertFalse(parameters.isCfRuntime());
    StackTraceLine fooLine;
    if (customSourceFile) {
      // TODO(b/232212653): Should retrace convert out of "0" and represent it as <noline>/-1?
      fooLine = inputLine("foo", 0);
    } else if (isRuntimeWithPcAsLineNumberSupport()) {
      // TODO(b/232212653): Retrace should convert PC 1 to line <noline>/-1/0.
      fooLine = inputLine("foo", 1);
    } else {
      fooLine = inputLine("foo", -1);
    }
    StackTraceLine barLine = inputLine("bar", getPcEncoding(0));
    StackTraceLine bazLine = inputLine("baz", getPcEncoding(0));
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
    return StackTrace.builder()
        // Foo has only <noline> on input and so it is allowed to compile it to a null debug-info.
        .add(residualPcOrNoLine("foo", 1))
        .add(residualLine("bar", getPcEncoding(0)))
        .add(residualLine("baz", getPcEncoding(0)))
        .add(residualLine("main", getPcEncoding(6)))
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
