// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;

@Keep
public interface MissingDefinitionClassContext extends MissingDefinitionContext {

  /** Returns the reference of the class context. */
  ClassReference getClassReference();

  @Override
  default boolean isClassContext() {
    return true;
  }

  @Override
  default MissingDefinitionClassContext asClassContext() {
    return this;
  }
}
