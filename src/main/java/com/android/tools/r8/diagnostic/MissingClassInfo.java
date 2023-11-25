// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;

@KeepForApi
public interface MissingClassInfo extends MissingDefinitionInfo {

  /** Returns the reference of the missing class. */
  ClassReference getClassReference();

  @Override
  default boolean isMissingClass() {
    return true;
  }

  @Override
  default MissingClassInfo asMissingClass() {
    return this;
  }
}
