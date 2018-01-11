// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;
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
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeLatticeTest {

  private static DexItemFactory factory;
  private static AppInfoWithSubtyping appInfo;

  @BeforeClass
  public static void makeAppInfo() throws Exception {
    InternalOptions options = new InternalOptions();
    DexApplication application =
        new ApplicationReader(
                AndroidApp.builder()
                    .addLibraryFiles(Paths.get(ToolHelper.getDefaultAndroidJar()))
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
    return Arrays.stream(elements).reduce(TypeLatticeElement.joiner(appInfo)).get();
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
        element(factory.createType("Ljava/io/IOException;")),
        join(
            element(factory.createType("Ljava/io/FileNotFoundException;")),
            element(factory.createType("Ljava/io/InterruptedIOException;"))));
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
}
