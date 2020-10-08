// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.google.common.base.Predicates.alwaysFalse;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignaturePrinter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldSignatureTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public FieldSignatureTest(TestParameters parameters) {}

  @Test
  public void testClass() {
    testParsingAndPrintingEqual("Lfoo/bar/baz;");
  }

  @Test
  public void testMissingSemicolon() {
    testParsingAndPrintingError("Lfoo/bar/baz")
        .assertAllErrorsMatch(
            diagnosticMessage(containsString("Invalid signature 'Lfoo/bar/baz;' for field A.")));
  }

  @Test
  public void testClassWithEmptyTypeArguments() {
    testParsingAndPrintingError("Lfoo/bar/baz<>;")
        .assertAllErrorsMatch(
            diagnosticMessage(containsString("Invalid signature 'Lfoo/bar/baz;' for field A.")));
  }

  @Test
  public void testClassWithTypeVariableArguments() {
    testParsingAndPrintingEqual("Lfoo/bar/baz<TT;>;");
  }

  @Test
  public void testTypeVariable() {
    testParsingAndPrintingEqual("TT;");
  }

  @Test
  public void testPrimitive() {
    testParsingAndPrintingError("I")
        .assertAllErrorsMatch(
            diagnosticMessage(containsString("Invalid signature 'I' for field A.")));
  }

  @Test
  public void testArray() {
    testParsingAndPrintingEqual("[I");
    testParsingAndPrintingEqual("[Lfoo/bar/baz;");
  }

  @Test
  public void testArrayWithGeneric() {
    testParsingAndPrintingEqual("[Lfoo/bar/baz<[I+Lfoo/Qux<*>.Inner<-Lfoo/Quux<TT;>;>;>;");
  }

  private void testParsingAndPrintingEqual(String signature) {
    FieldTypeSignature parsed =
        GenericSignature.parseFieldTypeSignature(
            "A", signature, Origin.unknown(), new DexItemFactory(), new Reporter());
    GenericSignaturePrinter genericSignaturePrinter =
        new GenericSignaturePrinter(NamingLens.getIdentityLens(), alwaysFalse());
    genericSignaturePrinter.visitFieldTypeSignature(parsed);
    String outSignature = genericSignaturePrinter.toString();
    assertEquals(signature, outSignature);
  }

  private TestDiagnosticMessages testParsingAndPrintingError(String signature) {
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    FieldTypeSignature parsed =
        GenericSignature.parseFieldTypeSignature(
            "A",
            signature,
            Origin.unknown(),
            new DexItemFactory(),
            new Reporter(testDiagnosticMessages));
    assertEquals(FieldTypeSignature.noSignature(), parsed);
    return testDiagnosticMessages;
  }
}
