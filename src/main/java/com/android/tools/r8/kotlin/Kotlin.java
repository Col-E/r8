// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.Sets;
import java.util.Set;

/** Class provides basic information about symbols related to Kotlin support. */
public final class Kotlin {
  public final DexItemFactory factory;

  public final Functional functional;
  public final Intrinsics intrinsics;
  public final Metadata metadata;

  public Kotlin(DexItemFactory factory) {
    this.factory = factory;

    this.functional = new Functional();
    this.intrinsics = new Intrinsics();
    this.metadata = new Metadata();
  }

  public final class Functional {
    private final Set<DexType> functions = Sets.newIdentityHashSet();

    private Functional() {
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
      for (int i = 0; i <= 22; i++) {
        functions.add(factory.createType("Lkotlin/jvm/functions/Function" + i + ";"));
      }
    }

    public final DexString kotlinStyleLambdaInstanceName = factory.createString("INSTANCE");

    public final DexType functionBase = factory.createType("Lkotlin/jvm/internal/FunctionBase;");
    public final DexType lambdaType = factory.createType("Lkotlin/jvm/internal/Lambda;");

    public final DexMethod lambdaInitializerMethod = factory.createMethod(
        lambdaType,
        factory.createProto(factory.voidType, factory.intType),
        factory.constructorMethodName);

    public boolean isFunctionInterface(DexType type) {
      return functions.contains(type);
    }
  }

  public final class Metadata {
    public final DexType kotlinMetadataType = factory.createType("Lkotlin/Metadata;");
    public final DexString kind = factory.createString("k");
    public final DexString metadataVersion = factory.createString("mv");
    public final DexString bytecodeVersion = factory.createString("bv");
    public final DexString data1 = factory.createString("d1");
    public final DexString data2 = factory.createString("d2");
    public final DexString extraString = factory.createString("xs");
    public final DexString packageName = factory.createString("pn");
    public final DexString extraInt = factory.createString("xi");
  }

  // kotlin.jvm.internal.Intrinsics class
  public final class Intrinsics {
    public final DexType type = factory.createType("Lkotlin/jvm/internal/Intrinsics;");
    public final DexMethod throwParameterIsNullException = factory.createMethod(type,
        factory.createProto(factory.voidType, factory.stringType), "throwParameterIsNullException");
    public final DexMethod throwNpe = factory.createMethod(
        type, factory.createProto(factory.voidType), "throwNpe");
  }

  // Calculates kotlin info for a class.
  public KotlinInfo getKotlinInfo(DexClass clazz, DiagnosticsHandler reporter) {
    return KotlinClassMetadataReader.getKotlinInfo(this, clazz, reporter);
  }
}
