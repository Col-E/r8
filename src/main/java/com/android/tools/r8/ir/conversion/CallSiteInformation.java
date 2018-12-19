// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexMethod;

public abstract class CallSiteInformation {

  /**
   * Check if the <code>method</code> is guaranteed to only have a single call site.
   * <p>
   * For pinned methods (methods kept through Proguard keep rules) this will always answer
   * <code>false</code>.
   */
  public abstract boolean hasSingleCallSite(DexMethod method);

  public abstract boolean hasDoubleCallSite(DexMethod method);

  public static CallSiteInformation empty() {
    return EmptyCallSiteInformation.EMPTY_INFO;
  }

  private static class EmptyCallSiteInformation extends CallSiteInformation {

    private static final EmptyCallSiteInformation EMPTY_INFO = new EmptyCallSiteInformation();

    @Override
    public boolean hasSingleCallSite(DexMethod method) {
      return false;
    }

    @Override
    public boolean hasDoubleCallSite(DexMethod method) {
      return false;
    }
  }
}
