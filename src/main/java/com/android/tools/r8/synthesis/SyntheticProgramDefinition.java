// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexProgramClass;
import java.util.function.Consumer;

public interface SyntheticProgramDefinition {

  void apply(
      Consumer<SyntheticMethodDefinition> onMethod,
      Consumer<SyntheticProgramClassDefinition> onClass);

  DexProgramClass getHolder();
}
