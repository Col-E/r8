// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.optimize.nonnull.IntrinsicsDeputy;
import com.android.tools.r8.ir.optimize.nonnull.NonNullParamAfterInvoke;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Collection;
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

  CodeInspector buildAndRun(Class<?> mainClass, Collection<Class<?>> classes) throws Exception {
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
        .addOptionsModification(options -> {
          // Need to increase a little bit to inline System.out.println
          options.inliningInstructionLimit = 4;
        })
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

    MethodSubject paramCheck =
        mainSubject.method("void", "nonNullAfterParamCheck", ImmutableList.of());
    assertThat(paramCheck, isPresent());
    assertEquals(1, countPrintCall(paramCheck));
    assertEquals(0, countThrow(paramCheck));

    paramCheck = mainSubject.method(
        "void", "nonNullAfterParamCheckDifferently", ImmutableList.of());
    assertThat(paramCheck, isPresent());
    assertEquals(1, countPrintCall(paramCheck));
    assertEquals(0, countThrow(paramCheck));
  }

  @Test
  public void testNonNullParamAfterInvoke() throws Exception {
    Class mainClass = NonNullParamAfterInvoke.class;
    CodeInspector inspector = buildAndRun(mainClass, ImmutableList.of(
        NeverInline.class,
        IntrinsicsDeputy.class,
        NonNullParamAfterInvoke.NotPinnedClass.class,
        mainClass));

    ClassSubject mainSubject = inspector.clazz(mainClass);
    assertThat(mainSubject, isPresent());

    String argTypeName = NonNullParamAfterInvoke.NotPinnedClass.class.getName();
    MethodSubject checkViaCall =
        mainSubject.method("void", "checkViaCall", ImmutableList.of(argTypeName, argTypeName));
    assertThat(checkViaCall, isPresent());
    assertEquals(2, countPrintCall(checkViaCall));

    MethodSubject checkViaIntrinsic =
        mainSubject.method("void", "checkViaIntrinsic", ImmutableList.of(argTypeName));
    assertThat(checkViaIntrinsic, isPresent());
    assertEquals(0, countCallToParamNullCheck(checkViaIntrinsic));
    assertEquals(1, countPrintCall(checkViaIntrinsic));

    MethodSubject checkAtOneLevelHigher =
        mainSubject.method("void", "checkAtOneLevelHigher", ImmutableList.of(argTypeName));
    assertThat(checkAtOneLevelHigher, isPresent());
    assertEquals(1, countPrintCall(checkAtOneLevelHigher));
    assertEquals(0, countThrow(checkAtOneLevelHigher));
  }

  private long countCallToParamNullCheck(MethodSubject method) {
    return countCall(method, IntrinsicsDeputy.class.getSimpleName(), "checkParameterIsNotNull");
  }

  private long countPrintCall(MethodSubject method) {
    return countCall(method, "PrintStream", "print");
  }

  private long countThrow(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(InstructionSubject::isThrow)).count();
  }

}
