// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import kotlinx.metadata.KmValueParameter;

// Provides access to Kotlin information about value parameter.
class KotlinValueParameterInfo {
  // TODO(b/70169921): When to use original param name v.s. when to *not* use?
  // Original parameter name.
  final String name;
  // Original parameter flag, e.g., has default value.
  final int flag;
  // Indicates whether the formal parameter is originally `vararg`.
  final boolean isVararg;

  private KotlinValueParameterInfo(String name, int flag, boolean isVararg) {
    this.name = name;
    this.flag = flag;
    this.isVararg = isVararg;
  }

  static KotlinValueParameterInfo fromKmValueParameter(KmValueParameter kmValueParameter) {
    return new KotlinValueParameterInfo(
        kmValueParameter.getName(),
        kmValueParameter.getFlags(),
        kmValueParameter.getVarargElementType() != null);
  }
}
