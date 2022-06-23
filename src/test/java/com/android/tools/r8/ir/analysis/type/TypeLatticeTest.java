// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.ClassTypeElement.computeLeastUpperBoundOfInterfaces;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.ir.analysis.type.TypeElement.fromDexType;
import static com.android.tools.r8.ir.analysis.type.TypeElement.getBottom;
import static com.android.tools.r8.ir.analysis.type.TypeElement.getTop;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection.Builder;
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
  private static AppView<AppInfoWithClassHierarchy> appView;

  @BeforeClass
  public static void makeAppInfo() throws Exception {
    InternalOptions options = new InternalOptions();
    List<Path> testClassPaths = ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(TypeLatticeTest.class.getPackage()),
        path -> path.getFileName().toString().startsWith("I"));
    D8Command.Builder d8CommandBuilder = D8Command.builder();
    d8CommandBuilder.addProgramFiles(testClassPaths);
    AndroidApp testClassApp = ToolHelper.runD8(d8CommandBuilder);
    DirectMappedDexApplication application =
        new ApplicationReader(
                AndroidApp.builder(testClassApp)
                    .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
                    .build(),
                options,
                Timing.empty())
            .read()
            .toDirect();
    factory = options.itemFactory;
    appView = AppView.createForR8(application);
  }

  private TopTypeElement top() {
    return getTop();
  }

  private BottomTypeElement bottom() {
    return getBottom();
  }

  private SinglePrimitiveTypeElement single() {
    return TypeElement.getSingle();
  }

  private WidePrimitiveTypeElement wide() {
    return TypeElement.getWide();
  }

  private TypeElement element(DexType type) {
    return element(type, Nullability.maybeNull());
  }

  private TypeElement element(DexType type, Nullability nullability) {
    return TypeElement.fromDexType(type, nullability, appView);
  }

  private ClassTypeElement element(DexType type, Nullability nullability, DexType... interfaces) {
    assert type.isClassType();
    InterfaceCollection.Builder interfaceCollectionBuilder = InterfaceCollection.builder();
    Arrays.asList(interfaces).forEach(interfaceCollectionBuilder::addKnownInterface);
    return ClassTypeElement.create(type, nullability, appView, interfaceCollectionBuilder.build());
  }

  private ArrayTypeElement array(int nesting, DexType base) {
    return (ArrayTypeElement) element(factory.createArrayType(nesting, base));
  }

  private ArrayTypeElement array(TypeElement baseType) {
    return array(baseType, maybeNull());
  }

  private ArrayTypeElement array(TypeElement baseType, Nullability nullability) {
    return ArrayTypeElement.create(baseType, nullability);
  }

  private TypeElement join(TypeElement... elements) {
    assertTrue(elements.length > 1);
    return TypeElement.join(Arrays.asList(elements), appView);
  }

  private boolean strictlyLessThan(TypeElement l1, TypeElement l2) {
    return l1.strictlyLessThan(l2, appView);
  }

  private boolean lessThanOrEqual(TypeElement l1, TypeElement l2) {
    return l1.lessThanOrEqual(l2, appView);
  }

  private boolean lessThanOrEqualUpToNullability(TypeElement l1, TypeElement l2) {
    return l1.lessThanOrEqualUpToNullability(l2, appView);
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
    assertEquals(
        element(factory.charSequenceType),
        join(element(factory.stringType), element(factory.charSequenceType), bottom()));
    assertEquals(
        element(factory.charSequenceType),
        join(bottom(), element(factory.stringType), element(factory.charSequenceType)));
    assertEquals(
        element(factory.charSequenceType),
        join(element(factory.stringType), bottom(), element(factory.charSequenceType)));
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
    assertEquals(
        element(factory.charSequenceType),
        join(element(factory.stringType), element(factory.charSequenceType)));
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

    assertEquals(
        element(factory.charSequenceType),
        join(
            element(factory.stringBuilderType),
            element(factory.charSequenceType)));
    assertEquals(
        element(factory.charSequenceType),
        join(
            element(factory.charSequenceType),
            element(factory.stringBufferType)));
  }

  @Test
  public void joinInterfaceArrayAndImplementerArray() {
    DexType queue = factory.createType("Ljava/util/Queue;");
    DexType arrayDeque = factory.createType("Ljava/util/ArrayDeque;");
    assertEquals(
        array(1, queue),
        join(
            array(1, queue),
            array(1, arrayDeque)));
    assertEquals(
        array(2, queue),
        join(
            array(2, arrayDeque),
            array(2, queue)));

    DexType type = factory.createType("Ljava/lang/reflect/Type;");
    DexType wType = factory.createType("Ljava/lang/reflect/WildcardType;");
    DexType pType = factory.createType("Ljava/lang/reflect/ParameterizedType;");
    assertEquals(
        array(1, type),
        join(
            array(1, wType),
            array(1, pType)));
    assertEquals(
        array(2, type),
        join(
            array(2, wType),
            array(2, factory.classType)));
    assertEquals(
        array(1, type),
        join(
            array(1, wType),
            array(1, pType),
            array(1, factory.classType)));

    assertEquals(
        array(1, factory.charSequenceType),
        join(
            array(1, factory.charSequenceType),
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
        array(
            array(
                element(
                    factory.objectType,
                    maybeNull(),
                    factory.cloneableType,
                    factory.serializableType))),
        join(array(3, factory.intType), array(3, factory.floatType)));
  }

  @Test
  public void joinDistinctTypesNestingOnePrimitiveArrays() {
    assertEquals(
        element(factory.objectType, maybeNull(), factory.cloneableType, factory.serializableType),
        join(array(1, factory.intType), array(1, factory.floatType)));
    assertEquals(
        element(factory.objectType, maybeNull(), factory.cloneableType, factory.serializableType),
        join(array(1, factory.longType), array(1, factory.intType)));

    // Test primitive types smaller than int.
    assertEquals(
        element(factory.objectType, maybeNull(), factory.cloneableType, factory.serializableType),
        join(array(1, factory.intType), array(1, factory.byteType)));
    assertEquals(
        element(factory.objectType, maybeNull(), factory.cloneableType, factory.serializableType),
        join(array(1, factory.charType), array(1, factory.shortType)));
  }

  @Test
  public void joinDistinctTypesNestingOneRightPrimitiveArrays() {
    assertEquals(
        element(factory.objectType, maybeNull(), factory.cloneableType, factory.serializableType),
        join(array(5, factory.intType), array(1, factory.floatType)));
  }

  @Test
  public void joinDistinctTypesNestingOneLeftPrimitiveArrays() {
    assertEquals(
        element(factory.objectType, maybeNull(), factory.cloneableType, factory.serializableType),
        join(array(1, factory.intType), array(5, factory.floatType)));
  }

  @Test
  public void joinDistinctNestingPrimitiveArrays() {
    assertEquals(
        array(
            array(
                element(
                    factory.objectType,
                    maybeNull(),
                    factory.cloneableType,
                    factory.serializableType))),
        join(array(3, factory.intType), array(4, factory.intType)));
  }

  @Test
  public void joinPrimitiveAndClassArrays() {
    assertEquals(
        array(array(array(element(factory.objectType, maybeNull(), factory.serializableType)))),
        join(array(4, factory.intType), array(3, factory.stringType)));
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
        array(3, factory.serializableType),
        join(
            array(3, factory.stringType),
            array(3, factory.classType)));
  }

  @Test
  public void joinDistinctNestingClassArrays() {
    assertEquals(
        array(array(array(element(factory.objectType, maybeNull(), factory.serializableType)))),
        join(array(3, factory.stringType), array(4, factory.stringType)));
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
    assertTrue(strictlyLessThan(TypeElement.getNull(), array(1, factory.classType)));
  }

  @Test
  public void testLessThanOrEqualUpToNullability() {
    assertTrue(
        lessThanOrEqualUpToNullability(
            element(factory.objectType, Nullability.maybeNull()),
            element(factory.objectType, definitelyNotNull())));
    assertTrue(
        lessThanOrEqualUpToNullability(
            element(factory.objectType, definitelyNotNull()),
            element(factory.objectType, Nullability.maybeNull())));
    assertFalse(
        lessThanOrEqualUpToNullability(array(3, factory.stringType), array(4, factory.stringType)));
    assertTrue(lessThanOrEqualUpToNullability(getBottom(), element(factory.objectType)));
    assertFalse(lessThanOrEqualUpToNullability(element(factory.objectType), getBottom()));
    assertFalse(lessThanOrEqualUpToNullability(getTop(), element(factory.objectType)));
    assertTrue(lessThanOrEqualUpToNullability(element(factory.objectType), getTop()));
  }

  @Test
  public void testSelfOrderWithoutSubtypingInfo() {
    DexType type = factory.createType("Lmy/Type;");
    TypeElement nonNullType = fromDexType(type, definitelyNotNull(), appView);
    ReferenceTypeElement nullableType =
        nonNullType.asReferenceType().getOrCreateVariant(Nullability.maybeNull());
    assertTrue(strictlyLessThan(nonNullType, nullableType));
    assertTrue(lessThanOrEqual(nonNullType, nullableType));
    assertFalse(lessThanOrEqual(nullableType, nonNullType));

    // Check that the class-type null is also more specific than nullableType.
    assertTrue(strictlyLessThan(TypeElement.getNull(), nullableType));
    assertTrue(
        strictlyLessThan(
            TypeElement.getNull(), nullableType.getOrCreateVariant(Nullability.definitelyNull())));
  }

  @Test
  public void testNotNullOfNullGivesBottom() {
    assertEquals(
        Nullability.bottom(), ReferenceTypeElement.getNull().asMeetWithNotNull().nullability());
  }

  private static InterfaceCollection itfs(DexType... types) {
    Builder builder = InterfaceCollection.builder();
    for (DexType type : types) {
      builder.addInterface(type, true);
    }
    return builder.build();
  }

  @Test
  public void testLeastUpperBoundOfInterfaces() {
    DexType collection = factory.createType("Ljava/util/Collection;");
    DexType set = factory.createType("Ljava/util/Set;");
    DexType list = factory.createType("Ljava/util/List;");
    DexType serializable = factory.serializableType;

    InterfaceCollection lub = computeLeastUpperBoundOfInterfaces(appView, itfs(set), itfs(list));
    assertEquals(1, lub.size());
    assertTrue(lub.containsKnownInterface(collection));
    assertEquals(collection, lub.getSingleKnownInterface());
    verifyViaPairwiseJoin(lub, ImmutableSet.of(set), ImmutableSet.of(list));

    lub =
        computeLeastUpperBoundOfInterfaces(
            appView, itfs(set, serializable), itfs(list, serializable));
    assertEquals(2, lub.size());
    assertTrue(lub.containsKnownInterface(collection));
    assertTrue(lub.containsKnownInterface(serializable));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(set, serializable), ImmutableSet.of(list, serializable));

    lub = computeLeastUpperBoundOfInterfaces(appView, itfs(set), itfs(list, serializable));
    assertEquals(1, lub.size());
    assertTrue(lub.containsKnownInterface(collection));
    assertFalse(lub.containsKnownInterface(serializable));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(set), ImmutableSet.of(list, serializable));

    lub = computeLeastUpperBoundOfInterfaces(appView, itfs(set, serializable), itfs(list));
    assertEquals(1, lub.size());
    assertTrue(lub.containsKnownInterface(collection));
    assertFalse(lub.containsKnownInterface(serializable));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(set, serializable), ImmutableSet.of(list));

    DexType type = factory.createType("Ljava/lang/reflect/Type;");
    DexType wType = factory.createType("Ljava/lang/reflect/WildcardType;");
    DexType pType = factory.createType("Ljava/lang/reflect/ParameterizedType;");

    lub = computeLeastUpperBoundOfInterfaces(appView, itfs(wType), itfs(pType));
    assertEquals(1, lub.size());
    assertTrue(lub.containsKnownInterface(type));
    assertEquals(type, lub.getSingleKnownInterface());
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(wType), ImmutableSet.of(pType));

    lub = computeLeastUpperBoundOfInterfaces(appView, itfs(list, serializable), itfs(pType));
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
    lub = computeLeastUpperBoundOfInterfaces(appView, itfs(i3), itfs(i4));
    assertEquals(2, lub.size());
    assertTrue(lub.containsKnownInterface(i1));
    assertTrue(lub.containsKnownInterface(i2));
    verifyViaPairwiseJoin(lub,
        ImmutableSet.of(i3), ImmutableSet.of(i4));
  }

  private void verifyViaPairwiseJoin(InterfaceCollection lub, Set<DexType> s1, Set<DexType> s2) {
    InterfaceCollection.Builder builder = InterfaceCollection.builder();
    for (DexType i1 : s1) {
      for (DexType i2 : s2) {
        InterfaceCollection lubPerPair =
            computeLeastUpperBoundOfInterfaces(
                appView, InterfaceCollection.singleton(i1), InterfaceCollection.singleton(i2));
        lubPerPair.forEach(builder::addInterface);
      }
    }
    InterfaceCollection pairwiseJoin = builder.build();
    InterfaceCollection.Builder lubBuilder = InterfaceCollection.builder();
    pairwiseJoin.forEach(
        (itf, isKnown) -> {
          // If there is a strict sub interface of this interface, it is not the least element.
          if (!pairwiseJoin.anyMatch(
              (other, ignoredOtherIsKnown) -> appView.appInfo().isStrictSubtypeOf(other, itf))) {
            lubBuilder.addInterface(itf, isKnown);
          }
        });
    InterfaceCollection pairwiseLub = lubBuilder.build();
    assertEquals(pairwiseLub, lub);
  }

}
