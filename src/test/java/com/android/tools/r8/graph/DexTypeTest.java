// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

public class DexTypeTest {

  private static DexItemFactory factory;
  private static AppInfoWithSubtyping appInfo;

  @BeforeClass
  public static void makeAppInfo() throws Exception {
    InternalOptions options = new InternalOptions();
    DexApplication application =
        new ApplicationReader(
            AndroidApp.builder()
                .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
                .addLibraryFiles(ToolHelper.getKotlinStdlibJar())
                .build(),
            options,
            new Timing(DexType.class.getName()))
        .read()
        .toDirect();
    factory = options.itemFactory;
    appInfo = new AppInfoWithSubtyping(application);
  }

  @Test
  public void implementedInterfaces_collections_java() {
    DexType serializable = factory.createType("Ljava/io/Serializable;");
    DexType iterable = factory.createType("Ljava/lang/Iterable;");
    // interface Collection extends Iterable
    DexType collection = factory.createType("Ljava/util/Collection;");
    // interface List extends Collection
    DexType list = factory.createType("Ljava/util/List;");
    // interface Queue extends Collection
    DexType queue = factory.createType("Ljava/util/Queue;");
    // interface Deque extends Queue
    DexType deque = factory.createType("Ljava/util/Deque;");

    // class ArrayList implements List
    DexType arrayList = factory.createType("Ljava/util/ArrayList;");
    Set<DexType> interfaces = arrayList.implementedInterfaces(appInfo);
    assertThat(interfaces, hasItems(serializable));
    assertThat(interfaces, hasItems(iterable));
    assertThat(interfaces, hasItems(collection));
    assertThat(interfaces, hasItems(list));
    assertThat(interfaces, not(hasItems(queue)));

    // class LinkedList implements List, Deque
    DexType linkedList = factory.createType("Ljava/util/LinkedList;");
    interfaces = linkedList.implementedInterfaces(appInfo);
    assertThat(interfaces, hasItems(serializable));
    assertThat(interfaces, hasItems(iterable));
    assertThat(interfaces, hasItems(collection));
    assertThat(interfaces, hasItems(list));
    assertThat(interfaces, hasItems(deque));
    assertThat(interfaces, hasItems(queue));
  }

  @Test
  public void implementedInterfaces_collections_kotlin() {
    DexType iterable = factory.createType("Ljava/lang/Iterable;");
    // interface Collection extends Iterable
    DexType collection = factory.createType("Ljava/util/Collection;");
    // interface List extends Collection
    DexType list = factory.createType("Ljava/util/List;");
    // interface Set extends Collection
    DexType set = factory.createType("Ljava/util/Set;");

    DexType ktAbsList = factory.createType("Lkotlin/collections/AbstractList;");
    DexType ktAbsSet = factory.createType("Lkotlin/collections/AbstractSet;");

    Set<DexType> interfaces = ktAbsList.implementedInterfaces(appInfo);
    assertThat(interfaces, hasItems(iterable));
    assertThat(interfaces, hasItems(collection));
    assertThat(interfaces, hasItems(list));
    assertThat(interfaces, not(hasItems(set)));

    interfaces = ktAbsSet.implementedInterfaces(appInfo);
    assertThat(interfaces, hasItems(iterable));
    assertThat(interfaces, hasItems(collection));
    assertThat(interfaces, hasItems(set));
    assertThat(interfaces, not(hasItems(list)));
  }

  @Test
  public void implementedInterfaces_reflect_java() {
    DexType serializable = factory.createType("Ljava/io/Serializable;");
    DexType annotatedElement = factory.createType("Ljava/lang/reflect/AnnotatedElement;");
    // interface GenericDeclaration extends AnnotatedElement
    DexType genericDeclaration = factory.createType("Ljava/lang/reflect/GenericDeclaration;");
    DexType type = factory.createType("Ljava/lang/reflect/Type;");
    DexType pType = factory.createType("Ljava/lang/reflect/ParameterizedType;");
    DexType klass = factory.createType("Ljava/lang/Class;");

    Set<DexType> interfaces = klass.implementedInterfaces(appInfo);
    assertThat(interfaces, hasItems(serializable));
    assertThat(interfaces, hasItems(annotatedElement));
    assertThat(interfaces, hasItems(genericDeclaration));
    assertThat(interfaces, hasItems(type));
    assertThat(interfaces, not(hasItems(pType)));
  }

  @Test
  public void implementedInterfaces_reflect_kotlin() {
    DexType kCallable = factory.createType("Lkotlin/reflect/KCallable;");
    // interface KProperty : KFunction
    DexType kFunction = factory.createType("Lkotlin/reflect/KFunction;");
    // interface KProperty : KCallable
    DexType kProperty = factory.createType("Lkotlin/reflect/KProperty;");
    // interface KMutableProperty : KProperty
    DexType kMutableProperty = factory.createType("Lkotlin/reflect/KMutableProperty;");
    // interface KMutableProperty0 : KProperty, KMutableProperty
    DexType kMutableProperty0 = factory.createType("Lkotlin/reflect/KMutableProperty0;");
    // class MutablePropertyReference0 : KMutableProperty0
    DexType mutableReference0 =
        factory.createType("Lkotlin/jvm/internal/MutablePropertyReference0;");

    Set<DexType> interfaces = mutableReference0.implementedInterfaces(appInfo);
    assertThat(interfaces, hasItems(kCallable));
    assertThat(interfaces, hasItems(kProperty));
    assertThat(interfaces, hasItems(kMutableProperty));
    assertThat(interfaces, hasItems(kMutableProperty0));
    assertThat(interfaces, not(hasItems(kFunction)));
  }

  @Test
  public void implementedInterfaces_lambda_kotlin() {
    DexType function = factory.createType("Lkotlin/Function;");
    DexType functionBase = factory.createType("Lkotlin/jvm/internal/FunctionBase;");
    // class Lambda : Function, FunctionBase
    DexType lambda = factory.createType("Lkotlin/jvm/internal/Lambda;");
    // interface Function0 : Function
    DexType function0 = factory.createType("Lkotlin/jvm/functions/Function0;");

    Set<DexType> interfaces = lambda.implementedInterfaces(appInfo);
    assertThat(interfaces, not(hasItems(lambda)));
    assertThat(interfaces, hasItems(function));
    assertThat(interfaces, hasItems(functionBase));

    interfaces = function0.implementedInterfaces(appInfo);
    assertThat(interfaces, hasItems(function0));
    assertThat(interfaces, hasItems(function));
    assertThat(interfaces, not(hasItems(functionBase)));
  }

}
