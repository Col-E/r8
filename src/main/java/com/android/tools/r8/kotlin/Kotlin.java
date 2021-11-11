// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Class provides basic information about symbols related to Kotlin support. */
public final class Kotlin {

  public final DexItemFactory factory;

  public final Functional functional;
  public final Intrinsics intrinsics;
  public final Metadata metadata;
  public final _Assertions assertions;

  public static final String NAME = "kotlin";
  public static final String PACKAGE_PREFIX = "L" + NAME + "/";

  public final DexString kotlinJvmTypePrefix;

  public static final class ClassClassifiers {

    public static final String arrayBinaryName = NAME + "/Array";
    public static final String arrayDescriptor = PACKAGE_PREFIX + "Array;";
    public static final String anyDescriptor = PACKAGE_PREFIX + "Any;";
    public static final String unitDescriptor = PACKAGE_PREFIX + "Unit;";
    public static final String booleanDescriptor = PACKAGE_PREFIX + "Boolean";
    public static final String charDescriptor = PACKAGE_PREFIX + "Char";
    public static final String byteDescriptor = PACKAGE_PREFIX + "Byte";
    public static final String uByteDescriptor = PACKAGE_PREFIX + "UByte";
    public static final String shortDescriptor = PACKAGE_PREFIX + "Short;";
    public static final String uShortDescriptor = PACKAGE_PREFIX + "UShort;";
    public static final String intDescriptor = PACKAGE_PREFIX + "Int;";
    public static final String uIntDescriptor = PACKAGE_PREFIX + "UInt;";
    public static final String floatDescriptor = PACKAGE_PREFIX + "Float;";
    public static final String longDescriptor = PACKAGE_PREFIX + "Long;";
    public static final String uLongDescriptor = PACKAGE_PREFIX + "ULong;";
    public static final String doubleDescriptor = PACKAGE_PREFIX + "Double;";
    public static final String functionDescriptor = PACKAGE_PREFIX + "Function;";
    public static final String kFunctionDescriptor = PACKAGE_PREFIX + "KFunction;";
    public static final String anyName = NAME + "/Any";

    public static final Set<String> kotlinPrimitivesDescriptors =
        ImmutableSet.<String>builder()
            .add(booleanDescriptor)
            .add(charDescriptor)
            .add(byteDescriptor)
            .add(uByteDescriptor)
            .add(shortDescriptor)
            .add(uShortDescriptor)
            .add(intDescriptor)
            .add(uIntDescriptor)
            .add(floatDescriptor)
            .add(longDescriptor)
            .add(uLongDescriptor)
            .add(doubleDescriptor)
            .build();

    // Kotlin static known types is a possible not complete collection of descriptors that kotlinc
    // and kotlin reflect know and expect the existence of.
    public static final Set<String> kotlinStaticallyKnownTypes;

    static {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      kotlinPrimitivesDescriptors.forEach(
          primitive -> {
            builder.add(primitive);
            builder.add(primitive.substring(0, primitive.length() - 1) + "Array;");
          });
      builder.add(unitDescriptor);
      builder.add(anyDescriptor);
      builder.add(arrayDescriptor);
      builder.add(functionDescriptor);
      builder.add(kFunctionDescriptor);
      kotlinStaticallyKnownTypes = builder.build();
    }
  }

  public Kotlin(DexItemFactory factory) {
    this.factory = factory;
    this.functional = new Functional();
    this.intrinsics = new Intrinsics();
    this.metadata = new Metadata();
    this.assertions = new _Assertions();
    kotlinJvmTypePrefix = factory.createString("Lkotlin/jvm/");
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
    private final Object2IntMap<DexType> functions =
        new Object2IntArrayMap<>(
            IntStream.rangeClosed(0, 22)
                .boxed()
                .collect(
                    Collectors.toMap(
                        i ->
                            factory.createType(PACKAGE_PREFIX + "jvm/functions/Function" + i + ";"),
                        Function.identity())));

    private Functional() {
    }

    public final DexType lambdaType = factory.createType(PACKAGE_PREFIX + "jvm/internal/Lambda;");

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
    public final DexType type = factory.createType(PACKAGE_PREFIX + "_Assertions;");
    public final DexString enabledFieldName = factory.createString("ENABLED");
    public final DexField enabledField =
        factory.createField(type, factory.booleanType, enabledFieldName);
  }

  // kotlin.jvm.internal.Intrinsics class
  public final class Intrinsics {
    public final DexType type = factory.createType(PACKAGE_PREFIX + "jvm/internal/Intrinsics;");
    public final DexMethod throwParameterIsNullException = factory.createMethod(type,
        factory.createProto(factory.voidType, factory.stringType), "throwParameterIsNullException");
    public final DexMethod throwParameterIsNullNPE =
        factory.createMethod(
            type,
            factory.createProto(factory.voidType, factory.stringType),
            "throwParameterIsNullNPE");
    public final DexMethod throwParameterIsNullIAE =
        factory.createMethod(
            type,
            factory.createProto(factory.voidType, factory.stringType),
            "throwParameterIsNullIAE");
    public final DexMethod checkParameterIsNotNull = factory.createMethod(type,
        factory.createProto(factory.voidType, factory.objectType, factory.stringType),
        "checkParameterIsNotNull");
    public final DexMethod checkNotNullParameter =
        factory.createMethod(
            type,
            factory.createProto(factory.voidType, factory.objectType, factory.stringType),
            "checkNotNullParameter");
    public final DexMethod throwNpe = factory.createMethod(
        type, factory.createProto(factory.voidType), "throwNpe");
  }
}
