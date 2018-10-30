// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.optimize.nonnull.IntrinsicsDeputy;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvoke;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonNullParamTest extends TestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public NonNullParamTest(Backend backend) {
    this.backend = backend;
  }

  CodeInspector buildAndRun(Class<?> mainClass, List<Class<?>> classes) throws Exception {
    String javaOutput = runOnJava(mainClass);

    return testForR8(backend)
        .addProgramClasses(classes)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(mainClass)
        .addKeepRules(
            ImmutableList.of(
                "-keepattributes InnerClasses,Signature,EnclosingMethod",
                "-dontobfuscate"))
        .run(mainClass)
        .assertSuccessWithOutput(javaOutput)
        .inspector();
  }

  @Test
  public void testIntrinsics() throws Exception {
    Class mainClass = IntrinsicsDeputy.class;
    CodeInspector inspector = buildAndRun(mainClass,
        ImmutableList.of(NeverInline.class, mainClass));

    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());

    MethodSubject selfCheck = mainSubject.method("void", "selfCheck", ImmutableList.of());
    assertThat(selfCheck, isPresent());
    assertEquals(0, countCallToParamNullCheck(selfCheck));
    assertEquals(1, countPrintCall(selfCheck));
    assertEquals(0, countThrow(selfCheck));

    MethodSubject checkNull = mainSubject.method("void", "checkNull", ImmutableList.of());
    assertThat(checkNull, isPresent());
    assertEquals(1, countCallToParamNullCheck(checkNull));
    assertEquals(1, countPrintCall(checkNull));
    assertEquals(0, countThrow(checkNull));

    String mainName = mainClass.getCanonicalName();
    MethodSubject paramCheck =
        mainSubject.method("void", "nonNullAfterParamCheck", ImmutableList.of(mainName));
    assertThat(paramCheck, isPresent());
    assertEquals(1, countPrintCall(paramCheck));
    // TODO(b/71500340): can be checked iff non-param info is propagated.
    //assertEquals(0, countThrow(paramCheck));

    paramCheck = mainSubject.method(
        "void", "nonNullAfterParamCheckDifferently", ImmutableList.of(mainName));
    assertThat(paramCheck, isPresent());
    assertEquals(1, countPrintCall(paramCheck));
    // TODO(b/71500340): can be checked iff non-param info is propagated.
    //assertEquals(0, countThrow(paramCheck));
  }

  @Test
  public void testNonNullParamAfterInvoke() throws Exception {
    Class mainClass = NonNullParamAfterInvoke.class;
    CodeInspector inspector = buildAndRun(mainClass,
        ImmutableList.of(NeverInline.class, IntrinsicsDeputy.class, mainClass));

    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());

    String mainName = mainClass.getCanonicalName();
    MethodSubject checkViaCall =
        mainSubject.method("void", "checkViaCall", ImmutableList.of(mainName, mainName));
    assertThat(checkViaCall, isPresent());
    // TODO(b/71500340): can be checked iff non-param info is propagated.
    //assertEquals(2, countPrintCall(checkViaCall));

    MethodSubject checkViaIntrinsic =
        mainSubject.method("void", "checkViaIntrinsic", ImmutableList.of(mainName));
    assertThat(checkViaIntrinsic, isPresent());
    assertEquals(0, countCallToParamNullCheck(checkViaIntrinsic));
    // TODO(b/71500340): can be checked iff non-param info is propagated.
    //assertEquals(1, countPrintCall(checkViaIntrinsic));

    MethodSubject checkAtOneLevelHigher =
        mainSubject.method("void", "checkAtOneLevelHigher", ImmutableList.of(mainName));
    assertThat(checkAtOneLevelHigher, isPresent());
    // TODO(b/71500340): can be checked iff non-param info is propagated.
    //assertEquals(1, countPrintCall(checkAtOneLevelHigher));
  }

  private long countCallToParamNullCheck(MethodSubject method) {
    return countCall(method, IntrinsicsDeputy.class.getSimpleName(), "checkParameterIsNotNull");
  }

  private long countPrintCall(MethodSubject method) {
    return countCall(method, "PrintStream", "print");
  }

  private long countCall(MethodSubject method, String className, String methodName) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        DexMethod invokedMethod = instructionSubject.getMethod();
        return invokedMethod.getHolder().toString().contains(className)
            && invokedMethod.name.toString().contains(methodName);
      }
      return false;
    })).count();
  }

  private long countThrow(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(InstructionSubject::isThrow)).count();
  }

}
