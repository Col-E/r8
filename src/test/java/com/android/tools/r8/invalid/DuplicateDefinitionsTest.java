// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.invalid;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateDefinitionsTest extends JasminTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  private final TestParameters parameters;

  public DuplicateDefinitionsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDuplicateMethods() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("C");
    classBuilder.addMainMethod(".limit locals 1", ".limit stack 0", "return");
    classBuilder.addMainMethod(".limit locals 1", ".limit stack 0", "return");
    classBuilder.addVirtualMethod("method", "V", ".limit locals 1", ".limit stack 0", "return");
    classBuilder.addVirtualMethod("method", "V", ".limit locals 1", ".limit stack 0", "return");

    testForD8(parameters.getBackend())
        .addProgramClassFileData(jasminBuilder.buildClasses())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .assertWarningsMatch(
                        diagnosticMessage(
                            containsString(
                                "Ignoring an implementation of the method `void"
                                    + " C.main(java.lang.String[])` because it has multiple"
                                    + " definitions")),
                        diagnosticMessage(
                            containsString(
                                "Ignoring an implementation of the method `void C.method()` because"
                                    + " it has multiple definitions"))))
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz("C");
              assertThat(clazz, isPresent());

              // There are two direct methods, but only because one is <init>.
              assertEquals(
                  2, clazz.getDexProgramClass().getMethodCollection().numberOfDirectMethods());
              assertThat(clazz.method("void", "<init>", ImmutableList.of()), isPresent());

              // There is only one virtual method.
              assertEquals(
                  1, clazz.getDexProgramClass().getMethodCollection().numberOfVirtualMethods());
            });
  }

  @Test
  public void testDuplicateFields() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("C");
    classBuilder.addField("public", "fld", "LC;", null);
    classBuilder.addField("public", "fld", "LC;", null);
    classBuilder.addStaticField("staticFld", "LC;", null);
    classBuilder.addStaticField("staticFld", "LC;", null);

    testForD8(parameters.getBackend())
        .addProgramClassFileData(jasminBuilder.buildClasses())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .assertWarningsMatch(
                        diagnosticMessage(
                            containsString("Field `C C.fld` has multiple definitions")),
                        diagnosticMessage(
                            containsString("Field `C C.staticFld` has multiple definitions"))))
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz("C");
              assertThat(clazz, isPresent());

              // Redundant fields have been removed.
              assertEquals(1, clazz.getDexProgramClass().instanceFields().size());
              assertEquals(1, clazz.getDexProgramClass().staticFields().size());
            });
  }
}
