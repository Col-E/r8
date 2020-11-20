// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.ir.code.InvokeMethod;

public class AlwaysTrueInstanceInitializerInfoContext extends InstanceInitializerInfoContext {

  private static final AlwaysTrueInstanceInitializerInfoContext INSTANCE =
      new AlwaysTrueInstanceInitializerInfoContext();

  private AlwaysTrueInstanceInitializerInfoContext() {}

  public static AlwaysTrueInstanceInitializerInfoContext getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isAlwaysTrue() {
    return true;
  }

  @Override
  public boolean isSatisfiedBy(InvokeMethod invoke) {
    return true;
  }
}
