// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import java.util.Comparator;

public class ClassReferenceUtils {

  private static final Comparator<ClassReference> COMPARATOR =
      Comparator.comparing(ClassReference::getDescriptor);

  public static int compare(ClassReference classReference, ClassReference other) {
    return getClassReferenceComparator().compare(classReference, other);
  }

  public static int compare(ClassReference classReference, FieldReference other) {
    int comparisonResult =
        getClassReferenceComparator().compare(classReference, other.getHolderClass());
    return comparisonResult != 0 ? comparisonResult : -1;
  }

  public static int compare(ClassReference classReference, MethodReference other) {
    int comparisonResult =
        getClassReferenceComparator().compare(classReference, other.getHolderClass());
    return comparisonResult != 0 ? comparisonResult : -1;
  }

  public static Comparator<ClassReference> getClassReferenceComparator() {
    return COMPARATOR;
  }

  public static ClassReference parseClassDescriptor(String classDescriptor) {
    if (DescriptorUtils.isClassDescriptor(classDescriptor)) {
      return Reference.classFromDescriptor(classDescriptor);
    } else {
      return null;
    }
  }

  public static DexType toDexType(ClassReference classReference, DexItemFactory dexItemFactory) {
    return dexItemFactory.createType(classReference.getDescriptor());
  }
}
