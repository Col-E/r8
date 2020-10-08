// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.google.common.base.Predicates.alwaysFalse;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignaturePrinter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodSignatureTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MethodSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testEmptyVoid() {
    testParsingAndPrintingEqual("()V");
  }

  @Test
  public void testThrowsOnly() {
    testParsingAndPrintingError("^TT;")
        .assertAllErrorsMatch(
            diagnosticMessage(containsString("Invalid signature '^TT;' for method A.")));
  }

  @Test
  public void testArguments() {
    testParsingAndPrintingEqual("(TT;Lfoo/bar/Baz;[I)V");
  }

  @Test
  public void testReturnType() {
    testParsingAndPrintingEqual("()Lfoo/Baz;");
  }

  @Test
  public void testTypeVariableReturn() {
    testParsingAndPrintingEqual("()TR;");
  }

  @Test
  public void testThrowsSingle() {
    testParsingAndPrintingEqual("(Lfoo/Bar;)V^Ljava/lang/Exception;");
  }

  @Test
  public void testThrowsMultiple() {
    testParsingAndPrintingEqual("(Lfoo/Bar;)V^Ljava/lang/Exception;^TT;^Lfoo/bar/Baz;");
  }

  @Test
  public void testThrowsMultipleError() {
    // TODO(b/170287583): This should throw an error.
    assertThrows(
        AssertionError.class,
        () -> testParsingAndPrintingEqual("(Lfoo/Bar;)V^Ljava/lang/Exception;^TT;Lfoo/bar/Baz;"));
  }

  @Test
  public void testTypeArgument() {
    testParsingAndPrintingEqual("<T:Ljava/lang/Object;>([I)V");
  }

  @Test
  public void testTypeArgumentMultiple() {
    testParsingAndPrintingEqual("<T:Ljava/lang/Object;R::LConsumer<TT;>;>([I)V");
  }

  @Test
  public void testTypeArgumentMultipleThrows() {
    testParsingAndPrintingEqual(
        "<T:Ljava/lang/Object;R::LConsumer<TT;>;>([Lfoo<TR;>;)Lbaz<TR;>;^TR;^Lfoo<TT;>;");
  }

  @Test
  public void testFormalTypeParameterEmptyInterfaceError() {
    testParsingAndPrintingError("<T:Ljava/lang/Object;:>Lfoo/bar/baz<TT;>;")
        .assertAllWarningsMatch(
            diagnosticMessage(
                containsString("Invalid signature '<T:Ljava/lang/Object;:>Lfoo/bar/baz<TT;>;'")));
  }

  private void testParsingAndPrintingEqual(String signature) {
    MethodTypeSignature parsed =
        GenericSignature.parseMethodSignature(
            "A", signature, Origin.unknown(), new DexItemFactory(), new Reporter());
    GenericSignaturePrinter genericSignaturePrinter =
        new GenericSignaturePrinter(NamingLens.getIdentityLens(), alwaysFalse());
    genericSignaturePrinter.visitMethodSignature(parsed);
    String outSignature = genericSignaturePrinter.toString();
    assertEquals(signature, outSignature);
  }

  private TestDiagnosticMessages testParsingAndPrintingError(String signature) {
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    MethodTypeSignature parsed =
        GenericSignature.parseMethodSignature(
            "A",
            signature,
            Origin.unknown(),
            new DexItemFactory(),
            new Reporter(testDiagnosticMessages));
    assertEquals(MethodTypeSignature.noSignature(), parsed);
    return testDiagnosticMessages;
  }
}
