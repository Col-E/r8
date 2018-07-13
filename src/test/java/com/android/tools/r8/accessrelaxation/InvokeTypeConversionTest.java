// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.dexinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class InvokeTypeConversionTest extends SmaliTestBase {
  private final String CLASS_NAME = "Example";
  private MethodSignature main;

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
      Consumer<DexInspector> inspectorConsumer) throws Exception {
    AndroidApp app = buildApplication(builder);
    List<String> pgConfigs = ImmutableList.of(
        keepMainProguardConfiguration(CLASS_NAME),
        "-printmapping",
        "-dontobfuscate",
        "-allowaccessmodification");
    R8Command.Builder command = ToolHelper.prepareR8CommandBuilder(app);
    command.addProguardConfiguration(pgConfigs, Origin.unknown());
    AndroidApp processedApp = ToolHelper.runR8(command.build(), o -> {
      o.enableInlining = false;
    });
    ProcessResult artResult = runOnArtRaw(processedApp, CLASS_NAME);
    if (expectedException == null) {
      assertEquals(0, artResult.exitCode);
      assertEquals("0", artResult.stdout);
    } else {
      assertEquals(1, artResult.exitCode);
      assertThat(artResult.stderr, containsString(expectedException));
    }
    DexInspector inspector = new DexInspector(processedApp);
    inspectorConsumer.accept(inspector);
  }

  // The following test checks invoke-direct, which refers to the private static method, is *not*
  // rewritten by publicizer lense, resulting in IncompatibleClassChangeError, which is expected.
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
        ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(Version.V4_4_4)
            ? "VerifyError" : "IncompatibleClassChangeError";
    run(builder, expectedError, dexInspector -> {
      ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
      assertThat(clazz, isPresent());
      DexEncodedMethod method = getMethod(dexInspector, main);
      assertNotNull(method);
      DexCode code = method.getCode().asDexCode();
      // The given invoke line is remained as-is.
      assertTrue(code.instructions[2] instanceof InvokeDirect);
    });
  }

  // The following test checks invoke-direct, which refers to the private instance method, *is*
  // rewritten by publicizer lense, as the target method will be publicized.
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
    run(builder, null, dexInspector -> {
      ClassSubject clazz = dexInspector.clazz(CLASS_NAME);
      assertThat(clazz, isPresent());
      DexEncodedMethod method = getMethod(dexInspector, main);
      assertNotNull(method);
      DexCode code = method.getCode().asDexCode();
      // The given invoke line is changed to invoke-virtual
      assertTrue(code.instructions[2] instanceof InvokeVirtual);
    });
  }

}