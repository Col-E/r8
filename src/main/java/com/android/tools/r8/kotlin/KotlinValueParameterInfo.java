// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.jvm.JvmExtensionsKt;

// Provides access to Kotlin information about value parameter.
class KotlinValueParameterInfo {
  // TODO(b/151193860): When to use original param name v.s. when to *not* use?
  // Original parameter name.
  final String name;
  // Original parameter flag, e.g., has default value.
  final int flag;
  // Indicates whether the formal parameter is originally `vararg`.
  final boolean isVararg;
  // TODO(b/151194869): Should we treat them as normal annotations? E.g., shrinking and renaming?
  // Annotations on the type of value parameter.
  final List<KmAnnotation> annotations;

  private KotlinValueParameterInfo(
      String name, int flag, boolean isVararg, List<KmAnnotation> annotations) {
    this.name = name;
    this.flag = flag;
    this.isVararg = isVararg;
    this.annotations = annotations;
  }

  static KotlinValueParameterInfo fromKmValueParameter(KmValueParameter kmValueParameter) {
    KmType kmType = kmValueParameter.getType();
    return new KotlinValueParameterInfo(
        kmValueParameter.getName(),
        kmValueParameter.getFlags(),
        kmValueParameter.getVarargElementType() != null,
        kmType != null ? JvmExtensionsKt.getAnnotations(kmType) : ImmutableList.of());
  }
}
