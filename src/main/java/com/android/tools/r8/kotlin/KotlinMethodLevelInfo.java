// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

public interface KotlinMethodLevelInfo extends KotlinMemberLevelInfo {

  default boolean isConstructor() {
    return false;
  }

  default KotlinConstructorInfo asConstructor() {
    return null;
  }

  default boolean isFunction() {
    return false;
  }

  default KotlinFunctionInfo asFunction() {
    return null;
  }

  default boolean isProperty() {
    return false;
  }

  default KotlinPropertyInfo asProperty() {
    return null;
  }
}
