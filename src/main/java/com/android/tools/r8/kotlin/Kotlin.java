// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Class provides basic information about symbols related to Kotlin support. */
public final class Kotlin {

  // Simply "kotlin", but to avoid being renamed by Shadow.relocate in build.gradle.
  public static final String NAME = String.join("", ImmutableList.of("k", "o", "t", "l", "i", "n"));

  // Simply "Lkotlin/", but to avoid being renamed by Shadow.relocate in build.gradle.
  private static final String KOTLIN =
      String.join("", ImmutableList.of("L", "k", "o", "t", "l", "i", "n", "/"));

  static String addKotlinPrefix(String str) {
    return KOTLIN + str;
  }

  public final DexItemFactory factory;

  public final Functional functional;
  public final Intrinsics intrinsics;
  public final Metadata metadata;
  public final _Assertions assertions;

  public static final class ClassClassifiers {

    public static final String arrayBinaryName = NAME + "/Array";
    public static final String anyName = NAME + "/Any";
  }

  // Mappings from JVM types to Kotlin types (of type DexType)
  final Map<DexType, DexType> knownTypeConversion;

  public Kotlin(DexItemFactory factory) {
    this.factory = factory;

    this.functional = new Functional();
    this.intrinsics = new Intrinsics();
    this.metadata = new Metadata();
    this.assertions = new _Assertions();

    // See {@link org.jetbrains.kotlin.metadata.jvm.deserialization.ClassMapperLite}
    this.knownTypeConversion =
        ImmutableMap.<DexType, DexType>builder()
            // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/index.html
            // Boxed primitives and arrays
            .put(factory.booleanType, factory.createType(addKotlinPrefix("Boolean;")))
            .put(factory.boxedBooleanType, factory.createType(addKotlinPrefix("Boolean;")))
            .put(factory.booleanArrayType, factory.createType(addKotlinPrefix("BooleanArray;")))
            .put(factory.byteType, factory.createType(addKotlinPrefix("Byte;")))
            .put(factory.byteArrayType, factory.createType(addKotlinPrefix("ByteArray;")))
            .put(factory.charType, factory.createType(addKotlinPrefix("Char;")))
            .put(factory.charArrayType, factory.createType(addKotlinPrefix("CharArray;")))
            .put(factory.shortType, factory.createType(addKotlinPrefix("Short;")))
            .put(factory.shortArrayType, factory.createType(addKotlinPrefix("ShortArray;")))
            .put(factory.intType, factory.createType(addKotlinPrefix("Int;")))
            .put(factory.boxedIntType, factory.createType(addKotlinPrefix("Int;")))
            .put(factory.intArrayType, factory.createType(addKotlinPrefix("IntArray;")))
            .put(factory.longType, factory.createType(addKotlinPrefix("Long;")))
            .put(factory.longArrayType, factory.createType(addKotlinPrefix("LongArray;")))
            .put(factory.floatType, factory.createType(addKotlinPrefix("Float;")))
            .put(factory.floatArrayType, factory.createType(addKotlinPrefix("FloatArray;")))
            .put(factory.doubleType, factory.createType(addKotlinPrefix("Double;")))
            .put(factory.doubleArrayType, factory.createType(addKotlinPrefix("DoubleArray;")))
            // Other intrinsics
            .put(factory.voidType, factory.createType(addKotlinPrefix("Unit;")))
            .put(factory.objectType, factory.createType(addKotlinPrefix("Any;")))
            .put(factory.boxedVoidType, factory.createType(addKotlinPrefix("Nothing;")))
            .put(factory.stringType, factory.createType(addKotlinPrefix("String;")))
            .put(factory.charSequenceType, factory.createType(addKotlinPrefix("CharSequence;")))
            .put(factory.throwableType, factory.createType(addKotlinPrefix("Throwable;")))
            .put(factory.cloneableType, factory.createType(addKotlinPrefix("Cloneable;")))
            .put(factory.boxedNumberType, factory.createType(addKotlinPrefix("Number;")))
            .put(factory.comparableType, factory.createType(addKotlinPrefix("Comparable;")))
            .put(factory.enumType, factory.createType(addKotlinPrefix("Enum;")))
            // Collections
            .put(factory.iteratorType, factory.createType(addKotlinPrefix("collections/Iterator;")))
            .put(
                factory.collectionType,
                factory.createType(addKotlinPrefix("collections/Collection;")))
            .put(factory.listType, factory.createType(addKotlinPrefix("collections/List;")))
            .put(factory.setType, factory.createType(addKotlinPrefix("collections/Set;")))
            .put(factory.mapType, factory.createType(addKotlinPrefix("collections/Map;")))
            .put(
                factory.listIteratorType,
                factory.createType(addKotlinPrefix("collections/ListIterator;")))
            .put(factory.iterableType, factory.createType(addKotlinPrefix("collections/Iterable;")))
            .put(
                factory.mapEntryType, factory.createType(addKotlinPrefix("collections/Map$Entry;")))
            // .../jvm/functions/FunctionN -> .../FunctionN
            .putAll(
                IntStream.rangeClosed(0, 22)
                    .boxed()
                    .collect(
                        Collectors.toMap(
                            i ->
                                factory.createType(
                                    addKotlinPrefix("jvm/functions/Function" + i + ";")),
                            i -> factory.createType(addKotlinPrefix("Function" + i + ";")))))
            .build();
  }

  public final class Functional {
    // NOTE: Kotlin stdlib defines interface Function0 till Function22 explicitly, see:
    //    https://github.com/JetBrains/kotlin/blob/master/libraries/
    //                    stdlib/jvm/runtime/kotlin/jvm/functions/Functions.kt
    //
    // For functions with arity bigger that 22 it is supposed to use FunctionN as described
    // in https://github.com/JetBrains/kotlin/blob/master/spec-docs/function-types.md,
    // but in current implementation (v1.2.21) defining such a lambda results in:
    //   > "Error: A JNI error has occurred, please check your installation and try again"
    //
    // This implementation just ignores lambdas with arity > 22.
    private final Object2IntMap<DexType> functions = new Object2IntArrayMap<>(
        IntStream.rangeClosed(0, 22).boxed().collect(
            Collectors.toMap(
                i -> factory.createType(addKotlinPrefix("jvm/functions/Function") + i + ";"),
                Function.identity()))
    );

    private Functional() {
    }

    public final DexString kotlinStyleLambdaInstanceName = factory.createString("INSTANCE");

    public final DexType functionBase =
        factory.createType(addKotlinPrefix("jvm/internal/FunctionBase;"));
    public final DexType lambdaType = factory.createType(addKotlinPrefix("jvm/internal/Lambda;"));

    public final DexMethod lambdaInitializerMethod = factory.createMethod(
        lambdaType,
        factory.createProto(factory.voidType, factory.intType),
        factory.constructorMethodName);

    public boolean isFunctionInterface(DexType type) {
      return functions.containsKey(type);
    }

    public int getArity(DexType type) {
      assert isFunctionInterface(type)
          : "Request to retrieve the arity from non-Kotlin-Function: " + type.toSourceString();
      return functions.getInt(type);
    }
  }

  public final class Metadata {
    public final DexString kind = factory.createString("k");
    public final DexString metadataVersion = factory.createString("mv");
    public final DexString bytecodeVersion = factory.createString("bv");
    public final DexString data1 = factory.createString("d1");
    public final DexString data2 = factory.createString("d2");
    public final DexString extraString = factory.createString("xs");
    public final DexString packageName = factory.createString("pn");
    public final DexString extraInt = factory.createString("xi");
  }

  public final class _Assertions {
    public final DexType type = factory.createType(addKotlinPrefix("_Assertions;"));
    public final DexString enabledFieldName = factory.createString("ENABLED");
    public final DexField enabledField =
        factory.createField(type, factory.booleanType, enabledFieldName);
  }

  // kotlin.jvm.internal.Intrinsics class
  public final class Intrinsics {
    public final DexType type = factory.createType(addKotlinPrefix("jvm/internal/Intrinsics;"));
    public final DexMethod throwParameterIsNullException = factory.createMethod(type,
        factory.createProto(factory.voidType, factory.stringType), "throwParameterIsNullException");
    public final DexMethod checkParameterIsNotNull = factory.createMethod(type,
        factory.createProto(factory.voidType, factory.objectType, factory.stringType),
        "checkParameterIsNotNull");
    public final DexMethod throwNpe = factory.createMethod(
        type, factory.createProto(factory.voidType), "throwNpe");
  }
}
