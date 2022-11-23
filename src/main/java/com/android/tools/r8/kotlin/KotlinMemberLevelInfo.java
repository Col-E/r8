// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;

public interface KotlinMemberLevelInfo extends EnqueuerMetadataTraceable {

  default boolean isNoKotlinInformation() {
    return false;
  }

  default boolean isCompanion() {
    return false;
  }

  default KotlinCompanionInfo asCompanion() {
    return null;
  }

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

  default boolean isEnumEntry() {
    return false;
  }

  default KotlinEnumEntryInfo asEnumEntry() {
    return null;
  }
}
