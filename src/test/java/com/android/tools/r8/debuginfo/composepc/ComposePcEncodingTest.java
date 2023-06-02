// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo.composepc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ComposePcEncodingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ComposePcEncodingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private byte[] getTransformedClass() throws Exception {
    return transformer(TestClass.class)
        .removeLineNumberTable(MethodPredicate.onName("unusedKeptAndNoLineInfo"))
        .transform();
  }

  private boolean isNativePcSupported() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport());
  }

  @Test
  public void test() throws Exception {
    MethodReference unusedKeptAndNoLineInfo =
        Reference.methodFromMethod(TestClass.class.getDeclaredMethod("unusedKeptAndNoLineInfo"));

    // R8 compiles to DEX with pc2pc encoding or native-pc encoding.
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(getTransformedClass())
            .addKeepMainRule(TestClass.class)
            .addKeepMethodRules(unusedKeptAndNoLineInfo)
            .setMinApi(parameters)
            .addKeepAttributeLineNumberTable()
            .compile()
            .inspect(
                inspector -> {
                  // Expected residual line info of 1 for pc2pc encoding and some value for native.
                  int residualLine = isNativePcSupported() ? 123 : 1;
                  // Check the expected status of the DEX debug info object for the "no lines".
                  MethodSubject methodNoLines = inspector.method(unusedKeptAndNoLineInfo);
                  assertThat(methodNoLines, isPresent());
                  // TODO(b/232212653): This should be true in pc2pc compilation with a single line.
                  assertFalse(methodNoLines.hasLineNumberTable());
                  // Check that "retracing" the pinned method with no lines maps to "noline/zero".
                  RetraceFrameResult retraceResult =
                      inspector
                          .retrace()
                          .retraceFrame(
                              RetraceStackTraceContext.empty(),
                              OptionalInt.of(residualLine),
                              unusedKeptAndNoLineInfo);
                  assertFalse(retraceResult.isAmbiguous());
                  RetraceFrameElement frameElement = retraceResult.stream().findFirst().get();
                  assertEquals(0, frameElement.getOuterFrames().size());
                  RetracedMethodReference topFrame = frameElement.getTopFrame();
                  assertTrue(topFrame.isKnown());
                  // TODO(b/232212653): Retrace should map back to the "no line" value of zero.
                  assertFalse(topFrame.hasPosition());
                });

    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(ComposePcEncodingTest::checkStackTrace);

    Path r8OutputDex = compileResult.writeToZip();
    Path r8OutputMap =
        FileUtils.writeTextFile(
            temp.newFolder().toPath().resolve("out.map"), compileResult.getProguardMap());

    // D8 (re)merges DEX with an artificial jumbo-string to force a remapping of PC values.
    testForD8(parameters.getBackend())
        .addProgramFiles(r8OutputDex)
        .setMinApi(parameters)
        // We only optimize line info in release mode and with a mapping file output enabled.
        .release()
        .internalEnableMappingOutput()
        .apply(b -> b.getBuilder().setProguardInputMapFile(r8OutputMap))
        // Forcing jumbo processing will shift the PC values on the methods.
        .addOptionsModification(o -> o.testing.forceJumboStringProcessing = true)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectFailure(
            inspector -> {
              MethodSubject methodNoLines = inspector.method(unusedKeptAndNoLineInfo);
              assertThat(methodNoLines, isPresent());
              // TODO(b/213411850): This should depend on native pc support.
              assertTrue(methodNoLines.hasLineNumberTable());
            })
        .inspectStackTrace(ComposePcEncodingTest::checkStackTrace);
  }

  private static void checkStackTrace(StackTrace stackTrace) {
    StackTraceLine.Builder builder =
        StackTraceLine.builder()
            .setClassName(typeName(TestClass.class))
            .setFileName(TestClass.class.getSimpleName() + ".java");
    assertThat(
        stackTrace,
        StackTrace.isSame(
            StackTrace.builder()
                .add(builder.setMethodName("bar").setLineNumber(15).build())
                .add(builder.setMethodName("main").setLineNumber(25).build())
                .build()));
  }
}
