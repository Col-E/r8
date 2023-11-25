// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckCastDebugTestRunner extends DebugTestBase {

  private static final Class<?> MAIN = CheckCastDebugTest.class;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public R8TestRunResult runR8(ThrowableConsumer<DebugTestConfig> configConsumer) throws Exception {
    return testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(A.class, B.class, C.class, MAIN)
        .addKeepMainRule(MAIN)
        .addOptionsModification(options -> options.getVerticalClassMergerOptions().disable())
        .debug()
        .enableInliningAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(MAIN);
              assertThat(classSubject, isPresent());
            })
        .apply(
            compileResult ->
                configConsumer.accept(compileResult.debugConfig(parameters.getRuntime())))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccess();
  }

  @Test
  public void test_differentLocals() throws Throwable {
    R8TestRunResult runResult =
        runR8(
            config ->
                runDebugTest(
                    config,
                    MAIN.getCanonicalName(),
                    // Object obj = new C();
                    breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 35),
                    run(),
                    checkNoLocal("obj"),
                    checkNoLocal("a"),
                    checkNoLocal("b"),
                    checkNoLocal("c"),
                    // A a = (A) obj;
                    breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 36),
                    run(),
                    checkLocal("obj"),
                    checkNoLocal("a"),
                    checkNoLocal("b"),
                    checkNoLocal("c"),
                    // B b = (B) a;
                    breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 37),
                    run(),
                    checkLocal("obj"),
                    checkLocal("a"),
                    checkNoLocal("b"),
                    checkNoLocal("c"),
                    // C c = (C) b;
                    breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 38),
                    run(),
                    checkLocal("obj"),
                    checkLocal("a"),
                    checkLocal("b"),
                    checkNoLocal("c"),
                    // System.out.println(c.toString());
                    breakpoint(MAIN.getCanonicalName(), "differentLocals", "()V", 39),
                    run(),
                    checkLocal("obj"),
                    checkLocal("a"),
                    checkLocal("b"),
                    checkLocal("c"),
                    run()));

    ClassSubject classSubject = runResult.inspector().clazz(MAIN);
    MethodSubject method = classSubject.method("void", "differentLocals", ImmutableList.of());
    assertThat(method, isPresent());
    long count =
        Streams.stream(method.iterateInstructions(InstructionSubject::isCheckCast)).count();
    assertEquals(0, count);
  }

  @Test
  public void test_sameLocal() throws Throwable {
    R8TestRunResult runResult =
        runR8(
            config ->
                runDebugTest(
                    config,
                    MAIN.getCanonicalName(),
                    // Object obj = new C();
                    breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 44),
                    run(),
                    checkNoLocal("obj"),
                    // obj = (A) obj;
                    breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 45),
                    run(),
                    checkLocal("obj"),
                    // obj = (B) obj;
                    breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 46),
                    run(),
                    checkLocal("obj"),
                    // obj = (C) obj;
                    breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 47),
                    run(),
                    checkLocal("obj"),
                    // System.out.println(obj.toString());
                    breakpoint(MAIN.getCanonicalName(), "sameLocal", "()V", 48),
                    run(),
                    checkLocal("obj"),
                    run()));

    ClassSubject classSubject = runResult.inspector().clazz(MAIN);
    MethodSubject method = classSubject.method("void", "sameLocal", ImmutableList.of());
    assertThat(method, isPresent());
    long count =
        Streams.stream(method.iterateInstructions(InstructionSubject::isCheckCast)).count();
    assertEquals(0, count);
  }

}
