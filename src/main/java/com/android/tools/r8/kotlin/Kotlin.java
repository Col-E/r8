// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.kotlin.KotlinSyntheticClass.Flavour;
import com.android.tools.r8.utils.StringDiagnostic;
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

    public final DexType lambdaType = factory.createType("Lkotlin/jvm/internal/Lambda;");

    public final DexMethod lambdaInitializerMethod = factory.createMethod(lambdaType,
        factory.createProto(factory.voidType, factory.intType), factory.constructorMethodName);

    public boolean isFunctionInterface(DexType type) {
      return functions.contains(type);
    }
  }

  public final class Metadata {
    public final DexType kotlinMetadataType = factory.createType("Lkotlin/Metadata;");
    public final DexString elementNameK = factory.createString("k");
    public final DexString elementNameD1 = factory.createString("d1");
    public final DexString elementNameD2 = factory.createString("d2");
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
    if (clazz.annotations.isEmpty()) {
      return null;
    }
    DexAnnotation meta = clazz.annotations.getFirstMatching(metadata.kotlinMetadataType);
    if (meta != null) {
      try {
        return createKotlinInfo(clazz, meta);
      } catch (MetadataError e) {
        reporter.warning(
            new StringDiagnostic("Class " + clazz.type.toSourceString() +
                " has malformed kotlin.Metadata: " + e.getMessage()));
      }
    }
    return null;
  }

  private KotlinInfo createKotlinInfo(DexClass clazz, DexAnnotation meta) {
    DexAnnotationElement kindElement = getAnnotationElement(meta, metadata.elementNameK);
    if (kindElement == null) {
      throw new MetadataError("element 'k' is missing");
    }

    DexValue value = kindElement.value;
    if (!(value instanceof DexValueInt)) {
      throw new MetadataError("invalid 'k' value: " + value.toSourceString());
    }

    DexValueInt intValue = (DexValueInt) value;
    switch (intValue.value) {
      case 1:
        return new KotlinClass();
      case 2:
        return new KotlinFile();
      case 3:
        return createSyntheticClass(clazz, meta);
      case 4:
        return new KotlinClassFacade();
      case 5:
        return new KotlinClassPart();
      default:
        throw new MetadataError("unsupported 'k' value: " + value.toSourceString());
    }
  }

  private KotlinSyntheticClass createSyntheticClass(DexClass clazz, DexAnnotation meta) {
    if (isKotlinStyleLambda(clazz)) {
      return new KotlinSyntheticClass(Flavour.KotlinStyleLambda);
    }
    if (isJavaStyleLambda(clazz, meta)) {
      return new KotlinSyntheticClass(Flavour.JavaStyleLambda);
    }
    return new KotlinSyntheticClass(Flavour.Unclassified);
  }

  private boolean isKotlinStyleLambda(DexClass clazz) {
    // TODO: replace with direct hints from kotlin metadata when available.
    return clazz.superType == this.functional.lambdaType;
  }

  private boolean isJavaStyleLambda(DexClass clazz, DexAnnotation meta) {
    assert !isKotlinStyleLambda(clazz);
    return clazz.superType == this.factory.objectType &&
        clazz.interfaces.size() == 1 &&
        isAnnotationElementNotEmpty(meta, metadata.elementNameD1);
  }

  private DexAnnotationElement getAnnotationElement(DexAnnotation annotation, DexString name) {
    for (DexAnnotationElement element : annotation.annotation.elements) {
      if (element.name == name) {
        return element;
      }
    }
    return null;
  }

  private boolean isAnnotationElementNotEmpty(DexAnnotation annotation, DexString name) {
    for (DexAnnotationElement element : annotation.annotation.elements) {
      if (element.name == name && element.value instanceof DexValueArray) {
        DexValue[] values = ((DexValueArray) element.value).getValues();
        if (values.length == 1 && values[0] instanceof DexValueString) {
          return true;
        }
        // Must be broken metadata.
        assert false;
      }
    }
    return false;
  }

  private static class MetadataError extends RuntimeException {
    MetadataError(String cause) {
      super(cause);
    }
  }
}
