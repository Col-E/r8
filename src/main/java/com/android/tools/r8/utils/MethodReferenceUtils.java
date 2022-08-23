// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.ClassReferenceUtils.getClassReferenceComparator;
import static com.android.tools.r8.utils.TypeReferenceUtils.getTypeReferenceComparator;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class MethodReferenceUtils {

  private static final Comparator<MethodReference> COMPARATOR =
      (method, other) -> {
        CompareResult holderClassCompareResult =
            CompareResult.compare(
                method.getHolderClass(), other.getHolderClass(), getClassReferenceComparator());
        if (!holderClassCompareResult.isEqual()) {
          return holderClassCompareResult.getComparisonResult();
        }
        CompareResult methodNameCompareResult =
            CompareResult.compare(method.getMethodName(), other.getMethodName());
        if (!methodNameCompareResult.isEqual()) {
          return methodNameCompareResult.getComparisonResult();
        }
        CompareResult returnTypeCompareResult =
            CompareResult.compare(
                method.getReturnType(), other.getReturnType(), getTypeReferenceComparator());
        if (!returnTypeCompareResult.isEqual()) {
          return returnTypeCompareResult.getComparisonResult();
        }
        for (int i = 0;
            i < Math.min(method.getFormalTypes().size(), other.getFormalTypes().size());
            i++) {
          CompareResult formalTypeCompareResult =
              CompareResult.compare(
                  method.getFormalTypes().get(i),
                  other.getFormalTypes().get(i),
                  getTypeReferenceComparator());
          if (!formalTypeCompareResult.isEqual()) {
            return formalTypeCompareResult.getComparisonResult();
          }
        }
        return method.getFormalTypes().size() - other.getFormalTypes().size();
      };

  public static MethodReference classConstructor(Class<?> clazz) {
    return classConstructor(Reference.classFromClass(clazz));
  }

  public static MethodReference classConstructor(ClassReference type) {
    return Reference.classConstructor(type);
  }

  public static MethodReference instanceConstructor(Class<?> clazz) {
    return instanceConstructor(Reference.classFromClass(clazz));
  }

  public static MethodReference instanceConstructor(ClassReference type) {
    return Reference.method(type, "<init>", Collections.emptyList(), null);
  }

  public static int compare(MethodReference methodReference, ClassReference other) {
    return ClassReferenceUtils.compare(other, methodReference) * -1;
  }

  public static int compare(MethodReference methodReference, FieldReference other) {
    return FieldReferenceUtils.compare(other, methodReference) * -1;
  }

  public static int compare(MethodReference methodReference, MethodReference other) {
    return getMethodReferenceComparator().compare(methodReference, other);
  }

  public static Comparator<MethodReference> getMethodReferenceComparator() {
    return COMPARATOR;
  }

  public static MethodReference mainMethod(Class<?> clazz) {
    return mainMethod(Reference.classFromClass(clazz));
  }

  public static MethodReference mainMethod(ClassReference type) {
    ArrayReference stringArrayType = Reference.array(Reference.classFromClass(String.class), 1);
    return Reference.method(type, "main", ImmutableList.of(stringArrayType), null);
  }

  public static MethodReference methodFromMethod(
      Class<?> clazz, String name, Class<?>... parameterTypes) {
    try {
      return Reference.methodFromMethod(clazz.getDeclaredMethod(name, parameterTypes));
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static DexMethod toDexMethod(
      MethodReference methodReference, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(
        ClassReferenceUtils.toDexType(methodReference.getHolderClass(), dexItemFactory),
        TypeReferenceUtils.toDexProto(
            methodReference.getFormalTypes(), methodReference.getReturnType(), dexItemFactory),
        methodReference.getMethodName());
  }

  public static String toSourceStringWithoutHolderAndReturnType(MethodReference methodReference) {
    return toSourceString(methodReference, false, false);
  }

  public static String toSourceString(MethodReference methodReference) {
    return toSourceString(methodReference, true, true);
  }

  public static String toSourceString(
      MethodReference methodReference, boolean includeHolder, boolean includeReturnType) {
    StringBuilder builder = new StringBuilder();
    if (includeReturnType) {
      builder
          .append(
              methodReference.getReturnType() != null
                  ? methodReference.getReturnType().getTypeName()
                  : "void")
          .append(" ");
    }
    if (includeHolder) {
      builder.append(methodReference.getHolderClass().getTypeName()).append(".");
    }
    builder.append(methodReference.getMethodName()).append("(");
    Iterator<TypeReference> formalTypesIterator = methodReference.getFormalTypes().iterator();
    if (formalTypesIterator.hasNext()) {
      builder.append(formalTypesIterator.next().getTypeName());
      while (formalTypesIterator.hasNext()) {
        builder.append(", ").append(formalTypesIterator.next().getTypeName());
      }
    }
    return builder.append(")").toString();
  }

  public static MethodReference methodFromSmali(String smali) {
    int holderEndIndex = smali.indexOf(";") + 1;
    int methodDescriptorIndex = smali.indexOf("(", holderEndIndex);
    return Reference.methodFromDescriptor(
        smali.substring(0, holderEndIndex),
        smali.substring(holderEndIndex, methodDescriptorIndex),
        smali.substring(methodDescriptorIndex));
  }
}
