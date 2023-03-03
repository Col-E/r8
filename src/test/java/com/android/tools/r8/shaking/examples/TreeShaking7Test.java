// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking7Test extends TreeShakingTest {

  @Parameters(name = "{0} minify:{1}")
  public static List<Object[]> data() {
    return defaultTreeShakingParameters();
  }

  public TreeShaking7Test(TestParameters parameters, MinifyMode minify) {
    super(parameters, minify);
  }

  @Override
  protected String getName() {
    return "examples/shaking7";
  }

  @Override
  protected String getMainClass() {
    return "shaking7.Shaking";
  }

  @Test
  public void testKeepdoublefields() throws Exception {
    runTest(
        TreeShaking7Test::shaking7HasOnlyDoubleFields,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking7/keep-double-fields.txt"));
  }

  @Test
  public void testKeeppublicfields() throws Exception {
    runTest(
        TreeShaking7Test::shaking7HasOnlyPublicFields,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking7/keep-public-fields.txt"));
  }

  @Test
  public void testKeeppublicthedoublefieldfields() throws Exception {
    runTest(
        TreeShaking7Test::shaking7HasOnlyPublicFieldsNamedTheDoubleField,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking7/keep-public-theDoubleField-fields.txt"));
  }

  @Test
  public void testKeeppublictheintfieldfields() throws Exception {
    runTest(
        TreeShaking7Test::shaking7HasOnlyPublicFieldsNamedTheIntField,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking7/keep-public-theIntField-fields.txt"));
  }

  private static void shaking7HasOnlyDoubleFields(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          clazz.forAllFields(
              field -> {
                Assert.assertTrue(field.type().is("double"));
              });
        });
    Assert.assertTrue(
        inspector.clazz("shaking7.Subclass").field("double", "theDoubleField").isPresent());
    Assert.assertTrue(
        inspector.clazz("shaking7.Superclass").field("double", "theDoubleField").isPresent());
    Assert.assertFalse(inspector.clazz("shaking7.Liar").field("int", "theDoubleField").isPresent());
  }

  private static void shaking7HasOnlyPublicFieldsNamedTheIntField(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          clazz.forAllFields(
              field -> {
                Assert.assertTrue(field.getField().accessFlags.isPublic());
              });
        });
    ClassSubject subclass = inspector.clazz("shaking7.Subclass");
    Assert.assertTrue(subclass.field("int", "theIntField").isPresent());
    Assert.assertFalse(subclass.field("double", "theDoubleField").isPresent());
    Assert.assertFalse(
        inspector.clazz("shaking7.Superclass").field("double", "theDoubleField").isPresent());
    ClassSubject liar = inspector.clazz("shaking7.Liar");
    Assert.assertFalse(liar.field("int", "theDoubleField").isPresent());
    Assert.assertTrue(liar.field("double", "theIntField").isPresent());
  }

  private static void shaking7HasOnlyPublicFields(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          clazz.forAllFields(
              field -> {
                Assert.assertTrue(field.getField().accessFlags.isPublic());
              });
        });
    ClassSubject subclass = inspector.clazz("shaking7.Subclass");
    Assert.assertTrue(subclass.field("int", "theIntField").isPresent());
    Assert.assertTrue(subclass.field("double", "theDoubleField").isPresent());
    Assert.assertTrue(
        inspector.clazz("shaking7.Superclass").field("double", "theDoubleField").isPresent());
    Assert.assertTrue(inspector.clazz("shaking7.Liar").field("int", "theDoubleField").isPresent());
  }

  private static void shaking7HasOnlyPublicFieldsNamedTheDoubleField(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          clazz.forAllFields(
              field -> {
                Assert.assertTrue(field.getField().accessFlags.isPublic());
              });
        });
    ClassSubject subclass = inspector.clazz("shaking7.Subclass");
    Assert.assertFalse(subclass.field("int", "theIntField").isPresent());
    Assert.assertTrue(subclass.field("double", "theDoubleField").isPresent());
    Assert.assertTrue(
        inspector.clazz("shaking7.Superclass").field("double", "theDoubleField").isPresent());
    Assert.assertTrue(inspector.clazz("shaking7.Liar").field("int", "theDoubleField").isPresent());
  }
}
