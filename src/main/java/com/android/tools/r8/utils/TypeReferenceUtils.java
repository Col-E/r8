// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.PrimitiveReference;
import com.android.tools.r8.references.TypeReference;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class TypeReferenceUtils {

  private static final Comparator<TypeReference> COMPARATOR =
      (type, other) -> {
        if (type == other) {
          return 0;
        }
        // Handle null inputs (void).
        if (type == null) {
          return -1;
        }
        if (other == null) {
          return 1;
        }
        return type.getDescriptor().compareTo(other.getDescriptor());
      };

  public static Comparator<TypeReference> getTypeReferenceComparator() {
    return COMPARATOR;
  }

  public static TypeReference getVoidType() {
    return null;
  }

  public static DexProto toDexProto(
      List<TypeReference> formalTypes, TypeReference returnType, DexItemFactory dexItemFactory) {
    return toDexProto(
        formalTypes,
        returnType,
        dexItemFactory,
        classReference -> ClassReferenceUtils.toDexType(classReference, dexItemFactory));
  }

  /**
   * Converts the given {@param formalTypes} and {@param returnType} to a {@link DexProto}.
   *
   * @param classReferenceConverter is used to convert {@link ClassReference} instances into {@link
   *     DexType}, to allow caching of intermediate results at the call site.
   */
  public static DexProto toDexProto(
      List<TypeReference> formalTypes,
      TypeReference returnType,
      DexItemFactory dexItemFactory,
      Function<ClassReference, DexType> classReferenceConverter) {
    return dexItemFactory.createProto(
        toDexType(returnType, dexItemFactory, classReferenceConverter),
        ListUtils.map(
            formalTypes,
            formalType -> toDexType(formalType, dexItemFactory, classReferenceConverter)));
  }

  public static DexType toDexType(TypeReference typeReference, DexItemFactory dexItemFactory) {
    return toDexType(
        typeReference,
        dexItemFactory,
        classReference -> ClassReferenceUtils.toDexType(classReference, dexItemFactory));
  }

  /**
   * Converts the given {@param typeReference} to a {@link DexType}.
   *
   * @param classReferenceConverter is used to convert {@link ClassReference} instances into {@link
   *     DexType}, to allow caching of intermediate results at the call site.
   */
  public static DexType toDexType(
      TypeReference typeReference,
      DexItemFactory dexItemFactory,
      Function<ClassReference, DexType> classReferenceConverter) {
    if (typeReference == null) {
      return dexItemFactory.voidType;
    }
    if (typeReference.isPrimitive()) {
      PrimitiveReference primitiveReference = typeReference.asPrimitive();
      switch (primitiveReference.getDescriptor().charAt(0)) {
        case 'Z':
          return dexItemFactory.booleanType;
        case 'B':
          return dexItemFactory.byteType;
        case 'C':
          return dexItemFactory.charType;
        case 'S':
          return dexItemFactory.shortType;
        case 'I':
          return dexItemFactory.intType;
        case 'F':
          return dexItemFactory.floatType;
        case 'J':
          return dexItemFactory.longType;
        case 'D':
          return dexItemFactory.doubleType;
        default:
          throw new Unreachable(
              "Invalid primitive descriptor: " + primitiveReference.getDescriptor());
      }
    }
    if (typeReference.isArray()) {
      ArrayReference arrayReference = typeReference.asArray();
      TypeReference baseType = arrayReference.getBaseType();
      return dexItemFactory.createArrayType(
          arrayReference.getDimensions(),
          toDexType(baseType, dexItemFactory, classReferenceConverter));
    }
    assert typeReference.isClass();
    return classReferenceConverter.apply(typeReference.asClass());
  }
}
