// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.computeLeastUpperBoundOfInterfaces;
import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.fromDexType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeLatticeTest extends TestBase {
  private static final String IO_EXCEPTION = "Ljava/io/IOException;";
  private static final String NOT_FOUND = "Ljava/io/FileNotFoundException;";
  private static final String INTERRUPT = "Ljava/io/InterruptedIOException;";

  private static DexItemFactory factory;
  private static AppInfoWithSubtyping appInfo;

  @BeforeClass
  public static void makeAppInfo() throws Exception {
    InternalOptions options = new InternalOptions();
    List<Path> testClassPaths = ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(TypeLatticeTest.class.getPackage()),
        path -> path.getFileName().toString().startsWith("I"));
    D8Command.Builder d8CommandBuilder = D8Command.builder();
    d8CommandBuilder.addProgramFiles(testClassPaths);
    AndroidApp testClassApp = ToolHelper.runD8(d8CommandBuilder);
    DexApplication application =
        new ApplicationReader(
                AndroidApp.builder(testClassApp)
                    .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
                    .build(),
                options,
                new Timing(TypeLatticeTest.class.getName()))
            .read()
            .toDirect();
    factory = options.itemFactory;
    appInfo = new AppInfoWithSubtyping(application);
  }

  private TopTypeLatticeElement top() {
    return TypeLatticeElement.TOP;
  }

  private BottomTypeLatticeElement bottom() {
    return TypeLatticeElement.BOTTOM;
  }

  private SingleTypeLatticeElement single() {
    return TypeLatticeElement.SINGLE;
  }

  private WideTypeLatticeElement wide() {
    return TypeLatticeElement.WIDE;
  }

  private TypeLatticeElement element(DexType type) {
    return TypeLatticeElement.fromDexType(type, true, appInfo);
  }

  private ArrayTypeLatticeElement array(int nesting, DexType base) {
    return (ArrayTypeLatticeElement) element(factory.createArrayType(nesting, base));
  }

  private TypeLatticeElement join(TypeLatticeElement... elements) {
    assertTrue(elements.length > 1);
    return TypeLatticeElement.join(Arrays.asList(elements), appInfo);
  }

  private boolean strictlyLessThan(TypeLatticeElement l1, TypeLatticeElement l2) {
    return l1.strictlyLessThan(l2, appInfo);
  }

  private boolean lessThanOrEqual(TypeLatticeElement l1, TypeLatticeElement l2) {
    return l1.lessThanOrEqual(l2, appInfo);
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
  public void joinDifferentKindsIsTop() {
    assertEquals(
        top(),
        join(element(factory.intType), element(factory.stringType)));
    assertEquals(
        top(),
        join(element(factory.stringType), element(factory.doubleType)));
    assertEquals(
        top(),
        join(single(), element(factory.objectType)));
    assertEquals(
        top(),
        join(element(factory.objectType), wide()));
  }

  @Test
  public void joinBottomIsUnit() {
    DexType charSequence = factory.createType("Ljava/lang/CharSequence;");
    assertEquals(
        element(charSequence),
        join(element(factory.stringType), element(charSequence), bottom()));
    assertEquals(
        element(charSequence),
        join(bottom(), element(factory.stringType), element(charSequence)));
    assertEquals(
        element(charSequence),
        join(element(factory.stringType), bottom(), element(charSequence)));
  }

  @Test
  public void joinPrimitiveTypes() {
    assertEquals(
        single(),
        join(element(factory.intType), element(factory.floatType)));
    assertEquals(
        wide(),
        join(element(factory.longType), element(factory.doubleType)));
    assertEquals(
        top(),
        join(element(factory.intType), element(factory.longType)));
  }

  @Test
  public void joinClassTypes() {
    DexType charSequence = factory.createType("Ljava/lang/CharSequence;");
    assertEquals(
        element(charSequence),
        join(element(factory.stringType), element(charSequence)));
  }

  @Test
  public void joinIdentity() {
    assertEquals(
        element(factory.stringType),
        join(element(factory.stringType), element(factory.stringType)));

    DexType arrayList = factory.createType("Ljava/util/ArrayList;");
    assertEquals(
        element(arrayList),
        join(element(arrayList), element(arrayList)));
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
  public void joinInterfaceWithSuperInterface() {
    DexType queue = factory.createType("Ljava/util/Queue;");
    DexType deque = factory.createType("Ljava/util/Deque;");
    assertEquals(
        element(queue),
        join(
            element(deque),
            element(queue)));
    assertEquals(
        element(queue),
        join(
            element(queue),
            element(deque)));
  }

  @Test
  public void joinInterfaces() {
    DexType collection = factory.createType("Ljava/util/Collection;");
    DexType set = factory.createType("Ljava/util/Set;");
    DexType list = factory.createType("Ljava/util/List;");
    assertEquals(
        element(collection),
        join(
            element(set),
            element(list)));
    assertEquals(
        element(collection),
        join(
            element(list),
            element(set)));
  }

  @Test
  public void joinInterfaceAndImplementer() {
    DexType list = factory.createType("Ljava/util/List;");
    DexType linkedList = factory.createType("Ljava/util/LinkedList;");
    assertEquals(
        element(list),
        join(
            element(list),
            element(linkedList)));
    assertEquals(
        element(list),
        join(
            element(linkedList),
            element(list)));

    DexType arrayList = factory.createType("Ljava/util/ArrayList;");
    assertEquals(
        element(list),
        join(
            element(list),
            element(arrayList)));
    assertEquals(
        element(list),
        join(
            element(arrayList),
            element(list)));

    DexType queue = factory.createType("Ljava/util/Queue;");
    DexType arrayDeque = factory.createType("Ljava/util/ArrayDeque;");
    assertEquals(
        element(queue),
        join(
            element(queue),
            element(arrayDeque)));
    assertEquals(
        element(queue),
        join(
            element(arrayDeque),
            element(queue)));

    DexType type = factory.createType("Ljava/lang/reflect/Type;");
    DexType wType = factory.createType("Ljava/lang/reflect/WildcardType;");
    DexType pType = factory.createType("Ljava/lang/reflect/ParameterizedType;");
    assertEquals(
        element(type),
        join(
            element(wType),
            element(pType)));
    assertEquals(
        element(type),
        join(
            element(wType),
            element(factory.classType)));
    assertEquals(
        element(type),
        join(
            element(wType),
            element(pType),
            element(factory.classType)));
    assertEquals(
        element(type),
        join(
            element(factory.classType),
            element(wType),
            element(pType),
            element(type)));

    DexType charSequence = factory.createType("Ljava/lang/CharSequence;");
    assertEquals(
        element(charSequence),
        join(
            element(factory.stringBuilderType),
            element(charSequence)));
    assertEquals(
        element(charSequence),
        join(
            element(charSequence),
            element(factory.stringBufferType)));
  }

  @Test
  public void joinInterfaceArrayAndImplementerArray() {
    DexType charSequence = factory.createType("Ljava/lang/CharSequence;");
    assertEquals(
        // TODO(b/119181813): This should be array(1, charSequence)
        array(1, factory.objectType),
        join(
            array(1, charSequence),
            array(1, factory.stringType)));
  }

  @Test
  public void joinImplementers() {
    DexType appendable = factory.createType("Ljava/lang/Appendable;");
    DexType writer = factory.createType("Ljava/io/Writer;");
    assertEquals(
        element(appendable),
        join(
            element(factory.stringBufferType),
            element(writer)));
    assertEquals(
        element(appendable),
        join(
            element(writer),
            element(factory.stringBufferType)));
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
    assertEquals(
        element(factory.objectType),
        join(
            array(1, factory.longType),
            array(1, factory.intType)));
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
        ReferenceTypeLatticeElement.getNullTypeLatticeElement(),
        array(1, factory.classType)));
  }

  @Test
  public void testSelfOrderWithoutSubtypingInfo() {
    DexType type = appInfo.dexItemFactory.createType("Lmy/Type;");
    TypeLatticeElement nonNullType = fromDexType(type, false, appInfo);
    TypeLatticeElement nullableType = nonNullType.asNullable();
    // TODO(zerny): Once the null lattice is used for null info check that the class-type null is
    // also more specific that the nullableType.
    assertTrue(strictlyLessThan(nonNullType, nullableType));
    assertTrue(lessThanOrEqual(nonNullType, nullableType));
    assertFalse(lessThanOrEqual(nullableType, nonNullType));
  }

  @Test
  public void testLeastUpperBoundOfInterfaces() {
    DexType collection = factory.createType("Ljava/util/Collection;");
    DexType set = factory.createType("Ljava/util/Set;");
    DexType list = factory.createType("Ljava/util/List;");
    DexType serializable = factory.serializableType;

    Set<DexType> lub = computeLeastUpperBoundOfInterfaces(appInfo,
        ImmutableSet.of(set), ImmutableSet.of(list));
    assertEquals(1, lub.size());
    assertTrue(lub.contains(collection));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(set), ImmutableSet.of(list));

    lub = computeLeastUpperBoundOfInterfaces(appInfo,
        ImmutableSet.of(set, serializable), ImmutableSet.of(list, serializable));
    assertEquals(2, lub.size());
    assertTrue(lub.contains(collection));
    assertTrue(lub.contains(serializable));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(set, serializable), ImmutableSet.of(list, serializable));

    lub = computeLeastUpperBoundOfInterfaces(appInfo,
        ImmutableSet.of(set), ImmutableSet.of(list, serializable));
    assertEquals(1, lub.size());
    assertTrue(lub.contains(collection));
    assertFalse(lub.contains(serializable));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(set), ImmutableSet.of(list, serializable));

    lub = computeLeastUpperBoundOfInterfaces(appInfo,
        ImmutableSet.of(set, serializable), ImmutableSet.of(list));
    assertEquals(1, lub.size());
    assertTrue(lub.contains(collection));
    assertFalse(lub.contains(serializable));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(set, serializable), ImmutableSet.of(list));

    DexType type = factory.createType("Ljava/lang/reflect/Type;");
    DexType wType = factory.createType("Ljava/lang/reflect/WildcardType;");
    DexType pType = factory.createType("Ljava/lang/reflect/ParameterizedType;");

    lub = computeLeastUpperBoundOfInterfaces(appInfo,
        ImmutableSet.of(wType), ImmutableSet.of(pType));
    assertEquals(1, lub.size());
    assertTrue(lub.contains(type));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(wType), ImmutableSet.of(pType));

    lub = computeLeastUpperBoundOfInterfaces(appInfo,
        ImmutableSet.of(list, serializable), ImmutableSet.of(pType));
    assertEquals(0, lub.size());
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(list, serializable), ImmutableSet.of(pType));

    DexType i1 = factory.createType(
        DescriptorUtils.javaTypeToDescriptor(I1.class.getCanonicalName()));
    DexType i2 = factory.createType(
        DescriptorUtils.javaTypeToDescriptor(I2.class.getCanonicalName()));
    DexType i3 = factory.createType(
        DescriptorUtils.javaTypeToDescriptor(I3.class.getCanonicalName()));
    DexType i4 = factory.createType(
        DescriptorUtils.javaTypeToDescriptor(I4.class.getCanonicalName()));
    lub = computeLeastUpperBoundOfInterfaces(appInfo,
        ImmutableSet.of(i3), ImmutableSet.of(i4));
    assertEquals(2, lub.size());
    assertTrue(lub.contains(i1));
    assertTrue(lub.contains(i2));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(i3), ImmutableSet.of(i4));
  }

  private void verifyViaPairwiseJoin(Set<DexType> lub, Set<DexType> s1, Set<DexType>s2) {
    ImmutableSet.Builder<DexType> builder = ImmutableSet.builder();
    for (DexType i1 : s1) {
      for (DexType i2 : s2) {
        Set<DexType> lubPerPair = computeLeastUpperBoundOfInterfaces(appInfo,
            ImmutableSet.of(i1), ImmutableSet.of(i2));
        for (DexType lubInterface : lubPerPair) {
          builder.add(lubInterface);
        }
      }
    }
    Set<DexType> pairwiseJoin = builder.build();
    ImmutableSet.Builder<DexType> lubBuilder = ImmutableSet.builder();
    for (DexType itf : pairwiseJoin) {
      // If there is a strict sub interface of this interface, it is not the least element.
      if (pairwiseJoin.stream().anyMatch(other -> other.isStrictSubtypeOf(itf, appInfo))) {
        continue;
      }
      lubBuilder.add(itf);
    }
    Set<DexType> pairwiseLub = lubBuilder.build();

    assertEquals(pairwiseLub.size(), lub.size());
    for (DexType i : pairwiseLub) {
      assertTrue(lub.contains(i));
    }
  }

}
