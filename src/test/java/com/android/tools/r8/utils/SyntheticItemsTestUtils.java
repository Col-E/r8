// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItems;
import java.lang.reflect.Method;

public class SyntheticItemsTestUtils {

  public static ClassReference syntheticClass(Class<?> clazz, int id) {
    return Reference.classFromTypeName(
        clazz.getTypeName() + SyntheticItems.EXTERNAL_SYNTHETIC_CLASS_SEPARATOR + id);
  }

  public static MethodReference syntheticMethod(Class<?> clazz, int id, Method method) {
    ClassReference syntheticHolder = syntheticClass(clazz, id);
    MethodReference originalMethod = Reference.methodFromMethod(method);
    return Reference.methodFromDescriptor(
        syntheticHolder.getDescriptor(),
        SyntheticItems.INTERNAL_SYNTHETIC_METHOD_PREFIX + 0,
        originalMethod.getMethodDescriptor());
  }
}
