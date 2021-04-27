// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignaturePartialTypeArgumentApplier;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignaturePartialTypeArgumentApplierTest extends TestBase {

  private final TestParameters parameters;
  private final DexItemFactory itemFactory = new DexItemFactory();
  private final DexType objectType = itemFactory.objectType;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenericSignaturePartialTypeArgumentApplierTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testVariablesInOuterPosition() {
    // TODO(b/186547736): The expected signature should be (Ljava/lang/Object;)Ljava/lang/Object;
    runTest(ImmutableMap.of("T", objectType, "R", objectType), "(TT;)TR;", "(*)*")
        .assertWarningThatMatches(
            diagnosticMessage(containsString("Invalid signature '(*)*' for method foo")));
  }

  @Test
  public void testVariablesInInnerPosition() {
    runTest(
            ImmutableMap.of("T", objectType, "R", objectType),
            "(LList<TT;>;)LList<TR;>;",
            "(LList<*>;)LList<*>;")
        .assertNoMessages();
  }

  private TestDiagnosticMessages runTest(
      Map<String, DexType> substitutions,
      String initialSignature,
      String expectedRewrittenSignature) {
    GenericSignaturePartialTypeArgumentApplier argumentApplier =
        GenericSignaturePartialTypeArgumentApplier.build(
            objectType, ClassSignature.noSignature(), substitutions);
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    MethodTypeSignature methodTypeSignature =
        argumentApplier.visitMethodSignature(
            GenericSignature.parseMethodSignature(
                "foo", initialSignature, Origin.unknown(), itemFactory, diagnosticsHandler));
    diagnosticsHandler.assertNoMessages();
    String rewrittenSignature =
        argumentApplier.visitMethodSignature(methodTypeSignature).toString();
    assertEquals(expectedRewrittenSignature, rewrittenSignature);
    GenericSignature.parseMethodSignature(
        "foo", rewrittenSignature, Origin.unknown(), itemFactory, diagnosticsHandler);
    return diagnosticsHandler;
  }
}
