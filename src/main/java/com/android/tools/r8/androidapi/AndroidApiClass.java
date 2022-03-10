// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.utils.AndroidApiLevel.getAndroidApiLevel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** This is a base class for all generated classes from api-versions.xml. */
public abstract class AndroidApiClass {

  private final ClassReference classReference;

  public AndroidApiClass(ClassReference classReference) {
    this.classReference = classReference;
  }

  public abstract AndroidApiLevel getApiLevel();

  public abstract int getMemberCount();

  public TraversalContinuation<?> visitFields(
      BiFunction<FieldReference, AndroidApiLevel, TraversalContinuation<?>> visitor) {
    return visitFields(visitor, classReference, 1);
  }

  public TraversalContinuation<?> visitMethods(
      BiFunction<MethodReference, AndroidApiLevel, TraversalContinuation<?>> visitor) {
    return visitMethods(visitor, classReference, 1);
  }

  protected abstract TraversalContinuation<?> visitFields(
      BiFunction<FieldReference, AndroidApiLevel, TraversalContinuation<?>> visitor,
      ClassReference holder,
      int minApiClass);

  protected abstract TraversalContinuation<?> visitMethods(
      BiFunction<MethodReference, AndroidApiLevel, TraversalContinuation<?>> visitor,
      ClassReference holder,
      int minApiClass);

  protected TraversalContinuation<?> visitField(
      BiFunction<FieldReference, AndroidApiLevel, TraversalContinuation<?>> visitor,
      ClassReference holder,
      int minApiClass,
      int minApiField,
      String name,
      String typeDescriptor) {
    return visitor.apply(
        Reference.field(holder, name, Reference.typeFromDescriptor(typeDescriptor)),
        getAndroidApiLevel(Integer.max(minApiClass, minApiField)));
  }

  protected TraversalContinuation<?> visitMethod(
      BiFunction<MethodReference, AndroidApiLevel, TraversalContinuation<?>> visitor,
      ClassReference holder,
      int minApiClass,
      int minApiMethod,
      String name,
      String[] formalTypeDescriptors,
      String returnType) {
    List<TypeReference> typeReferenceList = new ArrayList<>(formalTypeDescriptors.length);
    for (String formalTypeDescriptor : formalTypeDescriptors) {
      typeReferenceList.add(Reference.typeFromDescriptor(formalTypeDescriptor));
    }
    return visitor.apply(
        Reference.method(
            holder,
            name,
            typeReferenceList,
            returnType == null ? null : Reference.returnTypeFromDescriptor(returnType)),
        getAndroidApiLevel(Integer.max(minApiClass, minApiMethod)));
  }
}
