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
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignaturePrinter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassSignatureTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ClassSignatureTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSuperClass() {
    testParsingAndPrintingEqual("Lfoo/bar/baz;");
  }

  @Test
  public void testSuperClassWithInnerClasses() {
    testParsingAndPrintingEqual("Lfoo/bar/baz.Foo.Bar;");
  }

  @Test
  public void testSuperClassWithInterfaces1() {
    testParsingAndPrintingEqual("Lfoo/bar/baz;Lfoo/bar/qux;");
  }

  @Test
  public void testSuperClassWithInterfaces2() {
    testParsingAndPrintingEqual("Lfoo/bar/baz;Lfoo/bar/qux;Lfoo/bar/quux;");
  }

  @Test
  public void testSuperClassWithInterfacesInnerClassesSeparatedByDollar() {
    testParsingAndPrintingEqual("Lfoo/bar/baz$Foo$Bar;Lfoo/bar/qux;Lfoo/bar/quux$Foo$Bar;");
  }

  @Test
  public void testSuperClassWithInterfacesInnerClassesSeparatedByPeriod() {
    testParsingAndPrintingEqual("Lfoo/bar/baz.Foo.Bar;Lfoo/bar/qux;Lfoo/bar/quux.Foo.Bar;");
  }

  @Test
  public void testInnerClassesWithSeparatorInName1() {
    testParsingAndPrintingEqual("Lfoo/bar/baz<*>.Foo$$$<*>.Bar;");
  }

  @Test
  public void testInnerClassesWithSeparatorInName2() {
    testParsingAndPrintingEqual("Lfoo/bar/baz<*>.Foo$Bar$$<*>.Qux;");
  }

  @Test
  public void testSuperClassWithInnerClassesGenericArguments2() {
    testParsingAndPrintingEqual("Lfoo/bar/baz<LFoo;>.Foo<LFoo;>.Bar<LFoo;>;");
  }

  @Test
  public void testSuperClassWithInterfacesInnerClassesGenericArguments2() {
    testParsingAndPrintingEqual(
        "Lfoo/bar/baz<LFoo;>.Foo<LFoo;>.Bar<LFoo;>;Lfoo/bar/qux<LFoo;>;Lfoo/bar/quux<LFoo;>.Foo<LFoo;>.Bar<LFoo;>;");
  }

  @Test
  public void testSuperClassWithInterfacesInnerClassesGenericArguments3() {
    testParsingAndPrintingEqual(
        "Lfoo/bar/baz<LFoo;[[I[[[LBar<LFoo;>;>.Foo<LFoo;>.Bar<LFoo;>;Lfoo/bar/qux<LFoo;>;Lfoo/bar/quux<LFoo;>.Foo<LFoo;>.Bar<LFoo;>;");
  }

  @Test
  public void testWildCards() {
    testParsingAndPrintingEqual("Lfoo/Bar<*>;");
  }

  @Test
  public void testWildCardsPositiveNegative() {
    testParsingAndPrintingEqual("Lfoo/Bar<*+[I-LFoo<+LBar;>;>;");
  }

  @Test
  public void testSuperClassError() {
    testParsingAndPrintingError("Lfoo/bar/baz")
        .assertAllWarningsMatch(
            diagnosticMessage(containsString("Invalid signature 'Lfoo/bar/baz'")));
  }

  @Test
  public void testReferenceToTypeVariable() {
    testParsingAndPrintingEqual("Lfoo/bar/baz<TT;>;");
  }

  @Test
  public void testFormalTypeParameters() {
    testParsingAndPrintingEqual("<T:Ljava/lang/Object;>Lfoo/bar/baz<TT;>;");
  }

  @Test
  public void testFormalTypeWithEmpty() {
    testParsingAndPrintingEqual("<T:>Lfoo/bar/baz<TT;>;");
  }

  @Test
  public void testFormalTypeParametersWithInterfaces() {
    testParsingAndPrintingEqual("<T:Ljava/lang/Object;:LI;>Lfoo/bar/baz<TT;>;");
  }

  @Test
  public void testFormalTypeParametersArguments() {
    testParsingAndPrintingEqual(
        "<T:Ljava/lang/Object;:LI;R:LFoo<TT;[Lfoo/bar<TT;>.Baz<TT;[I>;>;>Lfoo/bar/baz<TT;>;");
  }

  @Test
  public void testFormalTypeParameterEmptyInterfaceError() {
    testParsingAndPrintingError("<T:Ljava/lang/Object;:>Lfoo/bar/baz<TT;>;")
        .assertAllWarningsMatch(
            diagnosticMessage(
                containsString("Invalid signature '<T:Ljava/lang/Object;:>Lfoo/bar/baz<TT;>;'")));
  }

  @Test
  public void testFormalTypeParametersEmptyError() {
    // TODO(b/169716723): This should throw an error
    assertThrows(AssertionError.class, () -> testParsingAndPrintingError("<>Lfoo/bar/baz<TT;>;"));
  }

  private void testParsingAndPrintingEqual(String signature) {
    ClassSignature parsed =
        GenericSignature.parseClassSignature(
            "A", signature, Origin.unknown(), new DexItemFactory(), new Reporter());
    GenericSignaturePrinter genericSignaturePrinter =
        new GenericSignaturePrinter(NamingLens.getIdentityLens(), alwaysFalse());
    genericSignaturePrinter.visitClassSignature(parsed);
    String outSignature = genericSignaturePrinter.toString();
    assertEquals(signature, outSignature);
  }

  private TestDiagnosticMessages testParsingAndPrintingError(String signature) {
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    ClassSignature parsed =
        GenericSignature.parseClassSignature(
            "A",
            signature,
            Origin.unknown(),
            new DexItemFactory(),
            new Reporter(testDiagnosticMessages));
    assertEquals(ClassSignature.noSignature(), parsed);
    return testDiagnosticMessages;
  }
}
