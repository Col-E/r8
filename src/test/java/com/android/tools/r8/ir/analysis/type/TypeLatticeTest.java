// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeLatticeTest {
  private static final String IO_EXCEPTION = "Ljava/io/IOException;";
  private static final String NOT_FOUND = "Ljava/io/FileNotFoundException;";
  private static final String INTERRUPT = "Ljava/io/InterruptedIOException;";

  private static DexItemFactory factory;
  private static AppInfoWithSubtyping appInfo;

  @BeforeClass
  public static void makeAppInfo() throws Exception {
    InternalOptions options = new InternalOptions();
    DexApplication application =
        new ApplicationReader(
                AndroidApp.builder()
                    .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
                    .build(),
                options,
                new Timing(TypeLatticeTest.class.getName()))
            .read()
            .toDirect();
    factory = options.itemFactory;
    appInfo = new AppInfoWithSubtyping(application);
  }

  private Top top() {
    return Top.getInstance();
  }

  private Bottom bottom() {
    return Bottom.getInstance();
  }

  private TypeLatticeElement element(DexType type) {
    return TypeLatticeElement.fromDexType(type, true);
  }

  private ArrayTypeLatticeElement array(int nesting, DexType base) {
    return (ArrayTypeLatticeElement) element(factory.createArrayType(nesting, base));
  }

  private TypeLatticeElement join(TypeLatticeElement... elements) {
    assertTrue(elements.length > 1);
    return TypeLatticeElement.join(appInfo, Arrays.stream(elements));
  }

  private boolean strictlyLessThan(TypeLatticeElement l1, TypeLatticeElement l2) {
    return TypeLatticeElement.strictlyLessThan(appInfo, l1, l2);
  }

  private boolean lessThanOrEqual(TypeLatticeElement l1, TypeLatticeElement l2) {
    return TypeLatticeElement.lessThanOrEqual(appInfo, l1, l2);
  }

  @Test
  public void joinTopIsTop() {
    assertEquals(
        top(),
        join(element(factory.stringType), element(factory.stringBuilderType), top()));
    assertEquals(
        top(),
        join(top(), element(factory.stringType), element(factory.stringBuilderType)));
    assertEquals(
        top(),
        join(element(factory.stringType), top(), element(factory.stringBuilderType)));
  }

  @Test
  public void joinBottomIsUnit() {
    assertEquals(
        element(factory.objectType),
        join(element(factory.stringType), element(factory.stringBuilderType), bottom()));
    assertEquals(
        element(factory.objectType),
        join(bottom(), element(factory.stringType), element(factory.stringBuilderType)));
    assertEquals(
        element(factory.objectType),
        join(element(factory.stringType), bottom(), element(factory.stringBuilderType)));
  }

  @Test
  public void joinClassTypes() {
    assertEquals(
        element(factory.objectType),
        join(element(factory.stringType), element(factory.stringBuilderType)));
  }

  @Test
  public void joinToNonJavaLangObject() {
    assertEquals(
        element(factory.createType(IO_EXCEPTION)),
        join(
            element(factory.createType(NOT_FOUND)),
            element(factory.createType(INTERRUPT))));
  }

  @Test
  public void joinSamePrimitiveArrays() {
    assertEquals(
        array(3, factory.intType),
        join(
            array(3, factory.intType),
            array(3, factory.intType)));
  }

  @Test
  public void joinDistinctTypesPrimitiveArrays() {
    assertEquals(
        array(2, factory.objectType),
        join(
            array(3, factory.intType),
            array(3, factory.floatType)));
  }

  @Test
  public void joinDistinctTypesNestingOnePrimitiveArrays() {
    assertEquals(
        element(factory.objectType),
        join(
            array(1, factory.intType),
            array(1, factory.floatType)));
  }

  @Test
  public void joinDistinctTypesNestingOneRightPrimitiveArrays() {
    assertEquals(
        element(factory.objectType),
        join(
            array(5, factory.intType),
            array(1, factory.floatType)));
  }

  @Test
  public void joinDistinctTypesNestingOneLeftPrimitiveArrays() {
    assertEquals(
        element(factory.objectType),
        join(
            array(1, factory.intType),
            array(5, factory.floatType)));
  }

  @Test
  public void joinDistinctNestingPrimitiveArrays() {
    assertEquals(
        array(2, factory.objectType),
        join(
            array(3, factory.intType),
            array(4, factory.intType)));
  }

  @Test
  public void joinPrimitiveAndClassArrays() {
    assertEquals(
        array(3, factory.objectType),
        join(
            array(4, factory.intType),
            array(3, factory.stringType)));
  }

  @Test
  public void joinSameClassArrays() {
    assertEquals(
        array(3, factory.stringType),
        join(
            array(3, factory.stringType),
            array(3, factory.stringType)));
  }

  @Test
  public void joinDistinctTypesClassArrays() {
    assertEquals(
        array(3, factory.objectType),
        join(
            array(3, factory.stringType),
            array(3, factory.stringBuilderType)));
  }

  @Test
  public void joinDistinctNestingClassArrays() {
    assertEquals(
        array(3, factory.objectType),
        join(
            array(3, factory.stringType),
            array(4, factory.stringType)));
  }

  @Test
  public void testPartialOrders() {
    assertTrue(lessThanOrEqual(
        element(factory.objectType),
        element(factory.objectType)));
    assertFalse(strictlyLessThan(
        element(factory.objectType),
        element(factory.objectType)));

    assertTrue(strictlyLessThan(
        element(factory.createType(NOT_FOUND)),
        element(factory.createType(IO_EXCEPTION))));
    assertTrue(strictlyLessThan(
        element(factory.createType(INTERRUPT)),
        element(factory.createType(IO_EXCEPTION))));
    assertFalse(lessThanOrEqual(
        element(factory.createType(NOT_FOUND)),
        element(factory.createType(INTERRUPT))));
    assertFalse(lessThanOrEqual(
        element(factory.createType(INTERRUPT)),
        element(factory.createType(NOT_FOUND))));

    assertTrue(strictlyLessThan(
        array(1, factory.stringType),
        array(1, factory.objectType)));
    assertFalse(lessThanOrEqual(
        array(1, factory.stringType),
        array(2, factory.objectType)));
    assertTrue(strictlyLessThan(
        array(2, factory.stringType),
        array(1, factory.objectType)));

    assertFalse(lessThanOrEqual(
        array(3, factory.stringType),
        array(4, factory.stringType)));
    assertFalse(lessThanOrEqual(
        array(4, factory.stringType),
        array(3, factory.stringType)));

    assertTrue(strictlyLessThan(
        array(2, factory.objectType),
        array(1, factory.objectType)));
    assertTrue(strictlyLessThan(
        NullLatticeElement.getInstance(),
        array(1, factory.classType)));
  }
}
