// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;

public class SyntheticProgramClassBuilder
    extends SyntheticClassBuilder<SyntheticProgramClassBuilder, DexProgramClass> {

  SyntheticProgramClassBuilder(
      DexType type,
      SyntheticKind syntheticKind,
      SynthesizingContext context,
      DexItemFactory factory) {
    super(type, syntheticKind, context, factory);
  }

  @Override
  public ClassKind<DexProgramClass> getClassKind() {
    return ClassKind.PROGRAM;
  }

  @Override
  public SyntheticProgramClassBuilder self() {
    return this;
  }
}
