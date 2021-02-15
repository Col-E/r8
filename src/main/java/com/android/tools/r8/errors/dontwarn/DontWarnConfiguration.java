// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors.dontwarn;

import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.InternalOptions;

public abstract class DontWarnConfiguration {

  public static DontWarnConfiguration create(ProguardConfiguration proguardConfiguration) {
    if (proguardConfiguration != null && proguardConfiguration.hasDontWarnPatterns()) {
      return new NonEmptyDontWarnConfiguration(
          proguardConfiguration.getDontWarnPatterns(new Witness()));
    }
    return empty();
  }

  public static EmptyDontWarnConfiguration empty() {
    return new EmptyDontWarnConfiguration();
  }

  public final boolean matches(Definition clazz) {
    return matches(clazz.getContextType());
  }

  public abstract boolean matches(DexType type);

  public abstract boolean validate(InternalOptions options);

  // Witness that can only be instantiated by the DontWarnConfiguration, to ensure that the dont
  // warn patterns are only accessed via the DontWarnConfiguration.
  public static class Witness {

    private Witness() {}
  }
}
