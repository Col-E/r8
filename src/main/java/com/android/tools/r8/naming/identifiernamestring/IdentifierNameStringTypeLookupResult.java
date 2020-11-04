// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.identifiernamestring;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;

public abstract class IdentifierNameStringTypeLookupResult
    extends IdentifierNameStringLookupResult<DexType> {

  IdentifierNameStringTypeLookupResult(DexType type) {
    super(type);
  }

  public abstract boolean isTypeInitializedFromUse();

  public abstract boolean isTypeInstantiatedFromUse(InternalOptions options);

  @Override
  public boolean isTypeResult() {
    return true;
  }

  @Override
  public IdentifierNameStringTypeLookupResult asTypeResult() {
    return this;
  }
}
