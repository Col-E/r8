// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.Comparator;

public class MissingDefinitionInfoUtils {

  private static final Comparator<MissingDefinitionInfo> COMPARATOR =
      (info, other) -> {
        IntBox result = new IntBox();
        if (isMissingClassInfo(info)) {
          ClassReference classReference = getClassReference(info);
          other.getMissingDefinition(
              otherClassReference ->
                  result.set(ClassReferenceUtils.compare(classReference, otherClassReference)),
              otherFieldReference ->
                  result.set(
                      ClassReferenceUtils.compare(
                          classReference, otherFieldReference.getHolderClass())),
              otherMethodReference ->
                  result.set(
                      ClassReferenceUtils.compare(
                          classReference, otherMethodReference.getHolderClass())));
        } else if (isMissingFieldInfo(info)) {
          FieldReference fieldReference = getFieldReference(info);
          other.getMissingDefinition(
              otherClassReference ->
                  result.set(
                      ClassReferenceUtils.compare(
                          fieldReference.getHolderClass(), otherClassReference)),
              otherFieldReference ->
                  result.set(FieldReferenceUtils.compare(fieldReference, otherFieldReference)),
              otherMethodReference ->
                  result.set(
                      ClassReferenceUtils.compare(
                          fieldReference.getHolderClass(), otherMethodReference.getHolderClass())));
        } else {
          MethodReference methodReference = getMethodReference(info);
          other.getMissingDefinition(
              otherClassReference ->
                  result.set(
                      ClassReferenceUtils.compare(
                          methodReference.getHolderClass(), otherClassReference)),
              otherFieldReference ->
                  result.set(
                      ClassReferenceUtils.compare(
                          methodReference.getHolderClass(), otherFieldReference.getHolderClass())),
              otherMethodReference ->
                  result.set(MethodReferenceUtils.compare(methodReference, otherMethodReference)));
        }
        return result.get();
      };

  public static Comparator<MissingDefinitionInfo> getComparator() {
    return COMPARATOR;
  }

  public static ClassReference getClassReference(MissingDefinitionInfo missingDefinitionInfo) {
    Box<ClassReference> classReference = new Box<>();
    missingDefinitionInfo.getMissingDefinition(
        classReference::set, emptyConsumer(), emptyConsumer());
    return classReference.get();
  }

  public static boolean isMissingClassInfo(MissingDefinitionInfo missingDefinitionInfo) {
    return getClassReference(missingDefinitionInfo) != null;
  }

  public static FieldReference getFieldReference(MissingDefinitionInfo missingDefinitionInfo) {
    Box<FieldReference> fieldReference = new Box<>();
    missingDefinitionInfo.getMissingDefinition(
        emptyConsumer(), fieldReference::set, emptyConsumer());
    return fieldReference.get();
  }

  public static boolean isMissingFieldInfo(MissingDefinitionInfo missingDefinitionInfo) {
    return getFieldReference(missingDefinitionInfo) != null;
  }

  public static MethodReference getMethodReference(MissingDefinitionInfo missingDefinitionInfo) {
    Box<MethodReference> methodReference = new Box<>();
    missingDefinitionInfo.getMissingDefinition(
        emptyConsumer(), emptyConsumer(), methodReference::set);
    return methodReference.get();
  }

  public static boolean isMissingMethodInfo(MissingDefinitionInfo missingDefinitionInfo) {
    return getMethodReference(missingDefinitionInfo) != null;
  }
}
