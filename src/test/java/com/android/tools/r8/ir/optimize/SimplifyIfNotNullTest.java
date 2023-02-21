// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ir.optimize.nonnull.FieldAccessTest;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterArrayAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterFieldAccess;
import com.android.tools.r8.ir.optimize.nonnull.NonNullAfterInvoke;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimplifyIfNotNullTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter public TestParameters parameters;

  private void verifyAbsenceOfIf(
      CodeInspector codeInspector, Class<?> testClass, List<MethodSignature> signatures) {
    for (MethodSignature signature : signatures) {
      MethodSubject method =
          codeInspector.clazz(testClass).method(signature);
      long count = Streams.stream(method.iterateInstructions(InstructionSubject::isIf)).count();
      assertEquals(0, count);
    }
  }

  private void testR8(Class<?> testClass, List<MethodSignature> signatures) throws Exception {
    testR8(testClass, signatures, null);
  }

  private void testR8(
      Class<?> testClass,
      List<MethodSignature> signatures,
      ThrowableConsumer<R8FullTestBuilder> configuration)
      throws Exception {
    CodeInspector codeInspector =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClasses(testClass)
            .addKeepRules("-keep class " + testClass.getCanonicalName() + " { *; }")
            .apply(configuration)
            .compile()
            .run(parameters.getRuntime(), testClass)
            .assertSuccessWithOutput("")
            .inspector();
    verifyAbsenceOfIf(codeInspector, testClass, signatures);
  }

  @Test
  public void nonNullAfterSafeInvokes() throws Exception {
    MethodSignature foo =
        new MethodSignature("foo", "int", new String[]{"java.lang.String"});
    MethodSignature bar =
        new MethodSignature("bar", "int", new String[]{"java.lang.String"});
    testR8(NonNullAfterInvoke.class, ImmutableList.of(foo, bar));
  }

  @Test
  public void nonNullAfterSafeArrayAccess() throws Exception {
    MethodSignature foo =
        new MethodSignature("foo", "int", new String[]{"java.lang.String[]"});
    MethodSignature bar =
        new MethodSignature("bar", "int", new String[]{"java.lang.String[]"});
    testR8(NonNullAfterArrayAccess.class, ImmutableList.of(foo, bar));
  }

  @Test
  public void nonNullAfterSafeFieldAccess() throws Exception {
    MethodSignature foo = new MethodSignature("foo", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    MethodSignature bar = new MethodSignature("bar", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    MethodSignature foo2 = new MethodSignature("foo2", "int",
        new String[]{FieldAccessTest.class.getCanonicalName()});
    testR8(
        NonNullAfterFieldAccess.class,
        ImmutableList.of(foo, bar, foo2),
        testBuilder -> testBuilder.addProgramClasses(FieldAccessTest.class));
  }
}
