// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.invalid;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.junit.Test;

public class DuplicateDefinitionsTest extends JasminTestBase {

  @Test
  public void testDuplicateMethods() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("C");
    classBuilder.addMainMethod(".limit locals 1", ".limit stack 0", "return");
    classBuilder.addMainMethod(".limit locals 1", ".limit stack 0", "return");
    classBuilder.addVirtualMethod("method", "V", ".limit locals 1", ".limit stack 0", "return");
    classBuilder.addVirtualMethod("method", "V", ".limit locals 1", ".limit stack 0", "return");

    // Run D8 and intercept warnings.
    PrintStream stderr = System.err;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setErr(new PrintStream(baos));

    AndroidApp app = compileWithD8(jasminBuilder.build());

    String output = new String(baos.toByteArray(), Charset.defaultCharset());
    System.setOut(stderr);

    // Check that warnings were emitted.
    assertThat(
        output,
        containsString(
            "Ignoring an implementation of the method `void C.main(java.lang.String[])` because "
                + "it has multiple definitions"));
    assertThat(
        output,
        containsString(
            "Ignoring an implementation of the method `void C.method()` because "
                + "it has multiple definitions"));

    DexInspector inspector = new DexInspector(app);
    ClassSubject clazz = inspector.clazz("C");
    assertThat(clazz, isPresent());

    // There are two direct methods, but only because one is <init>.
    assertEquals(2, clazz.getDexClass().directMethods().length);
    assertThat(clazz.method("void", "<init>", ImmutableList.of()), isPresent());

    // There is only one virtual method.
    assertEquals(1, clazz.getDexClass().virtualMethods().length);
  }

  @Test
  public void testDuplicateFields() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("C");
    classBuilder.addField("public", "fld", "LC;", null);
    classBuilder.addField("public", "fld", "LC;", null);
    classBuilder.addStaticField("staticFld", "LC;", null);
    classBuilder.addStaticField("staticFld", "LC;", null);

    // Run D8 and intercept warnings.
    PrintStream stderr = System.err;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setErr(new PrintStream(baos));

    AndroidApp app = compileWithD8(jasminBuilder.build());

    String output = new String(baos.toByteArray(), Charset.defaultCharset());
    System.setOut(stderr);

    // Check that warnings were emitted.
    assertThat(output, containsString("Field `C C.fld` has multiple definitions"));
    assertThat(output, containsString("Field `C C.staticFld` has multiple definitions"));

    DexInspector inspector = new DexInspector(app);
    ClassSubject clazz = inspector.clazz("C");
    assertThat(clazz, isPresent());

    // Redundant fields have been removed.
    assertEquals(1, clazz.getDexClass().instanceFields().length);
    assertEquals(1, clazz.getDexClass().staticFields().length);
  }
}
