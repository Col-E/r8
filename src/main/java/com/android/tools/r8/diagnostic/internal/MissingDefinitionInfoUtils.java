// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.MissingClassInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingFieldInfo;
import com.android.tools.r8.diagnostic.MissingMethodInfo;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.Comparator;
import java.util.function.Consumer;

public class MissingDefinitionInfoUtils {

  private static final Comparator<MissingDefinitionInfo> COMPARATOR =
      (info, other) -> {
        if (info.isMissingClass()) {
          ClassReference classReference = info.asMissingClass().getClassReference();
          if (other.isMissingClass()) {
            return ClassReferenceUtils.compare(
                classReference, other.asMissingClass().getClassReference());
          }
          if (other.isMissingField()) {
            return ClassReferenceUtils.compare(
                classReference, other.asMissingField().getFieldReference());
          }
          return ClassReferenceUtils.compare(
              classReference, other.asMissingMethod().getMethodReference());
        }
        if (info.isMissingField()) {
          FieldReference fieldReference = info.asMissingField().getFieldReference();
          if (other.isMissingClass()) {
            return FieldReferenceUtils.compare(
                fieldReference, other.asMissingClass().getClassReference());
          }
          if (other.isMissingField()) {
            return FieldReferenceUtils.compare(
                fieldReference, other.asMissingField().getFieldReference());
          }
          return FieldReferenceUtils.compare(
              fieldReference, other.asMissingMethod().getMethodReference());
        }
        MethodReference methodReference = info.asMissingMethod().getMethodReference();
        if (other.isMissingClass()) {
          return MethodReferenceUtils.compare(
              methodReference, other.asMissingClass().getClassReference());
        }
        if (other.isMissingField()) {
          MethodReferenceUtils.compare(methodReference, other.asMissingField().getFieldReference());
        }
        return MethodReferenceUtils.compare(
            methodReference, other.asMissingMethod().getMethodReference());
      };

  public static void accept(
      MissingDefinitionInfo missingDefinitionInfo,
      Consumer<MissingClassInfo> missingClassInfoConsumer,
      Consumer<MissingFieldInfo> missingFieldInfoConsumer,
      Consumer<MissingMethodInfo> missingMethodInfoConsumer) {
    if (missingDefinitionInfo.isMissingClass()) {
      missingClassInfoConsumer.accept(missingDefinitionInfo.asMissingClass());
    } else if (missingDefinitionInfo.isMissingField()) {
      missingFieldInfoConsumer.accept(missingDefinitionInfo.asMissingField());
    } else {
      assert missingDefinitionInfo.isMissingMethod();
      missingMethodInfoConsumer.accept(missingDefinitionInfo.asMissingMethod());
    }
  }

  public static Comparator<MissingDefinitionInfo> getComparator() {
    return COMPARATOR;
  }
}
