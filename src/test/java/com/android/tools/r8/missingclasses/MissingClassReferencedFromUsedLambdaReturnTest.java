// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionMethodContext;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Test;

public class MissingClassReferencedFromUsedLambdaReturnTest extends MissingClassesTestBase {

  private final MissingDefinitionContext[] referencedFrom =
      new MissingDefinitionContext[] {
        MissingDefinitionMethodContext.builder()
            .setMethodContext(
                Reference.method(
                    Reference.classFromClass(I.class),
                    "m",
                    Collections.emptyList(),
                    Reference.classFromClass(MissingClass.class)))
            .setOrigin(getOrigin(I.class))
            .build(),
        MissingDefinitionMethodContext.builder()
            .setMethodContext(
                Reference.method(
                    Reference.classFromClass(Main.class),
                    "lambda$main$0",
                    Collections.emptyList(),
                    Reference.classFromClass(MissingClass.class)))
            .setOrigin(getOrigin(Main.class))
            .build(),
        MissingDefinitionMethodContext.builder()
            .setMethodContext(
                Reference.method(
                    Reference.classFromClass(Main.class),
                    "main",
                    ImmutableList.of(Reference.array(Reference.classFromClass(String.class), 1)),
                    null))
            .setOrigin(getOrigin(Main.class))
            .build()
      };

  public MissingClassReferencedFromUsedLambdaReturnTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        addInterface());
  }

  @Test
  public void testDontWarnMainClassAndInterface() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addInterface().andThen(addDontWarn(Main.class, I.class)));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addInterface().andThen(addDontWarn(MissingClass.class)));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addInterface().andThen(addIgnoreWarnings()));
  }

  ThrowableConsumer<R8FullTestBuilder> addInterface() {
    return builder -> builder.addProgramClasses(I.class);
  }

  static class Main {

    public static void main(String[] args) {
      I i = () -> null;
      i.m();
    }

    /* private static synthetic MissingClass lambda$main$0() { return null; } */
  }

  interface I {

    MissingClass m();
  }
}
