// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors.dontwarn;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Set;

public class EmptyDontWarnConfiguration extends DontWarnConfiguration {

  @Override
  public Set<DexType> getNonMatches(Set<DexType> types) {
    return types;
  }

  @Override
  public boolean matches(DexType type) {
    return false;
  }

  @Override
  public boolean validate(InternalOptions options) {
    return true;
  }
}
