// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeTypeConversionTest extends SmaliTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final String CLASS_NAME = "Example";
  private MethodSignature main;

  private final TestParameters parameters;

  public InvokeTypeConversionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private SmaliBuilder buildTestClass(String invokeLine) {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addDefaultConstructor();
    builder.addPrivateInstanceMethod("int", "foo", ImmutableList.of(), 1,
        "  const/4 v0, 0",
        "  return v0");
    builder.addPrivateStaticMethod("int", "bar", ImmutableList.of(), 1,
        "  const/4 v0, 0",
        "  return v0");
    main = builder.addMainMethod(2,
        "new-instance v1, L" + CLASS_NAME + ";",
        "invoke-direct { v1 }, L" + CLASS_NAME + ";-><init>()V",
        invokeLine,
        "move-result v1",
        "invoke-static { v1 }, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;",
        "move-result-object v1",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "invoke-virtual { v0, v1 }, Ljava/io/PrintStream;->print(Ljava/lang/Object;)V",
        "return-void");
    return builder;
  }

  private void run(
      SmaliBuilder builder,
      String expectedException,
      Consumer<CodeInspector> inspectorConsumer) throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramDexFileData(builder.compile())
            .addKeepMainRule(CLASS_NAME)
            .addKeepRules(
                // We're testing lens-based invocation type conversions.
                "-dontoptimize", "-dontobfuscate", "-allowaccessmodification")
            .addOptionsModification(o -> o.inlinerOptions().enableInlining = false)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), CLASS_NAME);
    if (expectedException == null) {
      result.assertSuccessWithOutput("0");
      result.inspect(inspectorConsumer::accept);
    } else {
      result.assertFailureWithErrorThatMatches(containsString(expectedException));
      result.inspectFailure(inspectorConsumer::accept);
    }
  }

  // The following test checks invoke-direct, which refers to the private static method, is *not*
  // rewritten by publicizer lens, resulting in IncompatibleClassChangeError, which is expected.
  //
  // class Example {
  //   private int foo() { return 0; }
  //   private static int bar() { return 0; }
  //   public static void main(String[] args) {
  //     Example instance = new Example();
  //     "invoke-direct instance, Example->bar()"  <- should yield IncompatibleClassChangeError
  //     ...
  //   }
  // }
  @Test
  public void invokeDirectToAlreadyStaticMethod() throws Exception {
    SmaliBuilder builder = buildTestClass(
        "invoke-direct { v1 }, L" + CLASS_NAME + ";->bar()I");
    String expectedError =
        parameters.getRuntime().asDex().getVm().getVersion().isOlderThanOrEqual(Version.V4_4_4)
            ? "VerifyError"
            : "IncompatibleClassChangeError";
    run(
        builder,
        expectedError,
        dexInspector -> {
          ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
          assertThat(clazz, isPresent());
          DexEncodedMethod method = getMethod(dexInspector, main);
          assertNotNull(method);
          DexCode code = method.getCode().asDexCode();
          // The given invoke line is remained as-is.
          assertTrue(code.instructions[2] instanceof DexInvokeDirect);
        });
  }

  // The following test checks invoke-direct, which refers to the private instance method, *is*
  // rewritten by publicizer lens, as the target method will be publicized.
  //
  // class Example {
  //   private int foo() { return 0; }
  //   private static int bar() { return 0; }
  //   public static void main(String[] args) {
  //     Example instance = new Example();
  //     instance.foo();  // which was "invoke-direct instance, Example->foo()"
  //                      // will be "invoke-virtual instance, Example->foo()"
  //     ...
  //   }
  // }
  @Test
  public void invokeDirectToPublicizedMethod() throws Exception {
    SmaliBuilder builder = buildTestClass(
        "invoke-direct { v1 }, L" + CLASS_NAME + ";->foo()I");
    run(
        builder,
        null,
        dexInspector -> {
          ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
          assertThat(clazz, isPresent());
          DexEncodedMethod method = getMethod(dexInspector, main);
          assertNotNull(method);
          DexCode code = method.getCode().asDexCode();
          // The given invoke line is changed to invoke-virtual
          assertTrue(code.instructions[2] instanceof DexInvokeVirtual);
        });
  }

}