// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ClassHierarchyVerifierTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testOverriddenAbstractMethod() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("package.A");
    classBuilder.setIsAbstract();
    classBuilder.addAbstractMethod("method", ImmutableList.of(), "V");
    classBuilder = jasminBuilder.addClass("package.B", "package.A");
    classBuilder.addVirtualMethod("method", "V", ".limit locals 1", ".limit stack 1", "return");

    new ClassHierarchyVerifier(new CodeInspector(jasminBuilder.build())).run();
  }

  @Test
  public void testAbstractMethodOnNonAbstractClass() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("package.A");
    classBuilder.addAbstractMethod("method", ImmutableList.of(), "V");

    thrown.expect(AssertionError.class);
    thrown.expectMessage(
        "Non-abstract class package.A must implement method 'void package.A.method()'");
    new ClassHierarchyVerifier(new CodeInspector(jasminBuilder.build())).run();
  }

  @Test
  public void testUnimplementedAbstractMethod() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("package.A");
    classBuilder.setIsAbstract();
    classBuilder.addAbstractMethod("method", ImmutableList.of(), "V");
    jasminBuilder.addClass("package.B", "package.A");

    thrown.expect(AssertionError.class);
    thrown.expectMessage(
        "Non-abstract class package.B must implement method 'void package.A.method()'");
    new ClassHierarchyVerifier(new CodeInspector(jasminBuilder.build())).run();
  }

  @Test
  public void testUnimplementedInterfaceMethod() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("package.A");
    classBuilder.setIsInterface();
    classBuilder.addAbstractMethod("method", ImmutableList.of(), "V");
    jasminBuilder.addClass("package.B", "package.A");

    thrown.expect(AssertionError.class);
    thrown.expectMessage(
        "Non-abstract class package.B must implement method 'void package.A.method()'");
    new ClassHierarchyVerifier(new CodeInspector(jasminBuilder.build())).run();
  }
}
