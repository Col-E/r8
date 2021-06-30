// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class EnumUnboxingUtilityClass {

  private final DexProgramClass synthesizingContext;

  EnumUnboxingUtilityClass(DexProgramClass synthesizingContext) {
    this.synthesizingContext = synthesizingContext;
  }

  public abstract void ensureMethods(AppView<AppInfoWithLiveness> appView);

  public abstract DexProgramClass getDefinition();

  public final DexProgramClass getSynthesizingContext() {
    return synthesizingContext;
  }
}
