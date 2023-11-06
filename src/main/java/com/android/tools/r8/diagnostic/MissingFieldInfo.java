// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.FieldReference;

@KeepForApi
public interface MissingFieldInfo extends MissingDefinitionInfo {

  /** Returns the reference of the missing field. */
  FieldReference getFieldReference();

  @Override
  default boolean isMissingField() {
    return true;
  }

  @Override
  default MissingFieldInfo asMissingField() {
    return this;
  }
}
