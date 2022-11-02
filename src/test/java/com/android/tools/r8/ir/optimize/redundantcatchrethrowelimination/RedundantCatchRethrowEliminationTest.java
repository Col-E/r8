// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantcatchrethrowelimination;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.containsThrow;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RedundantCatchRethrowEliminationTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines(
          "removableRethrow",
          "trivialContext",
          "complexRemovableRethrow",
          "keptRethrow",
          "nonTrivialContext",
          "CloseableContext::close",
          "complexKeptRethrow");

  @Parameterized.Parameter() public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(Main.class, TrivialClosableContext.class, ClosableContext.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class, TrivialClosableContext.class, ClosableContext.class)
        .release()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::inspectD8);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, TrivialClosableContext.class, ClosableContext.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::inspectR8);
  }

  private void inspectD8(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Main.class);
    MethodSubject removableRethrowSubject =
        classSubject.uniqueMethodWithOriginalName("removableRethrow");
    assertThat(removableRethrowSubject, not(containsThrow()));
    // Without whole-program optimizations, we can't get rid of the trivial closable context.close()
    // call, which means that we can't remove the trivialContext throw.
    MethodSubject complexRemovableRethrowSubject =
        classSubject.uniqueMethodWithOriginalName("complexRemovableRethrow");
    assertThat(complexRemovableRethrowSubject, not(containsThrow()));

    MethodSubject keptRethrowSubject = classSubject.uniqueMethodWithOriginalName("keptRethrow");
    assertThat(keptRethrowSubject, containsThrow());
    MethodSubject nonTrivialContextSubject =
        classSubject.uniqueMethodWithOriginalName("nonTrivialContext");
    assertThat(nonTrivialContextSubject, containsThrow());
    MethodSubject complexKeptRethrowSubject =
        classSubject.uniqueMethodWithOriginalName("complexKeptRethrow");
    assertThat(complexKeptRethrowSubject, containsThrow());
  }

  private void inspectR8(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Main.class);
    MethodSubject removableRethrowSubject =
        classSubject.uniqueMethodWithOriginalName("removableRethrow");
    assertThat(removableRethrowSubject, not(containsThrow()));
    MethodSubject trivialContextSubject =
        classSubject.uniqueMethodWithOriginalName("trivialContext");
    assertThat(trivialContextSubject, not(containsThrow()));
    MethodSubject complexRemovableRethrowSubject =
        classSubject.uniqueMethodWithOriginalName("complexRemovableRethrow");
    assertThat(complexRemovableRethrowSubject, not(containsThrow()));

    MethodSubject keptRethrowSubject = classSubject.uniqueMethodWithOriginalName("keptRethrow");
    assertThat(keptRethrowSubject, containsThrow());
    MethodSubject nonTrivialContextSubject =
        classSubject.uniqueMethodWithOriginalName("nonTrivialContext");
    assertThat(nonTrivialContextSubject, containsThrow());
    MethodSubject complexKeptRethrowSubject =
        classSubject.uniqueMethodWithOriginalName("complexKeptRethrow");
    assertThat(complexKeptRethrowSubject, containsThrow());
  }

  public static class TrivialClosableContext implements java.io.Closeable {
    @Override
    public void close() {}
  }

  public static class ClosableContext implements java.io.Closeable {
    @Override
    public void close() {
      System.out.println("CloseableContext::close");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      removableRethrow();
      trivialContext();
      complexRemovableRethrow();
      keptRethrow();
      nonTrivialContext();
      complexKeptRethrow();
    }

    @NeverInline
    static void removableRethrow() {
      try {
        System.out.println("removableRethrow");
      } catch (Throwable t) {
        throw t;
      }
    }

    @NeverInline
    static void trivialContext() {
      try (TrivialClosableContext unused = new TrivialClosableContext()) {
        System.out.println("trivialContext");
      }
    }

    @NeverInline
    static void complexRemovableRethrow() {
      try {
        System.out.println("complexRemovableRethrow");
      } catch (RuntimeException e) {
        try {
          throw e;
        } catch (RuntimeException e2) {
          throw e2;
        } catch (Throwable t) {
          throw t;
        }
      } catch (Throwable t) {
        throw t;
      }
    }

    @NeverInline
    static void keptRethrow() {
      try {
        System.out.println("keptRethrow");
      } catch (Throwable t) {
        throw new RuntimeException("cause", t);
      }
    }

    @NeverInline
    static void nonTrivialContext() {
      try (ClosableContext unused = new ClosableContext()) {
        System.out.println("nonTrivialContext");
      }
    }

    @NeverInline
    static void complexKeptRethrow() {
      try {
        System.out.println("complexKeptRethrow");
      } catch (RuntimeException e) {
        try {
          throw e;
        } catch (RuntimeException e2) {
          throw e2;
        } catch (Throwable t) {
          System.out.println("So that this can't be elided");
          throw t;
        }
      } catch (Throwable t) {
        throw t;
      }
    }
  }
}
