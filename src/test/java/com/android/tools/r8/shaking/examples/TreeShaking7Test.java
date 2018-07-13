// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking7Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShaking7Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking7", "shaking7.Shaking", frontend, backend, minify);
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

  private static void shaking7HasOnlyDoubleFields(DexInspector inspector) {
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

  private static void shaking7HasOnlyPublicFieldsNamedTheIntField(DexInspector inspector) {
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

  private static void shaking7HasOnlyPublicFields(DexInspector inspector) {
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

  private static void shaking7HasOnlyPublicFieldsNamedTheDoubleField(DexInspector inspector) {
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
