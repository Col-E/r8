// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;

public class SyntheticClasspathClassBuilder
    extends SyntheticClassBuilder<SyntheticClasspathClassBuilder, DexClasspathClass> {

  SyntheticClasspathClassBuilder(
      DexType type,
      SyntheticKind syntheticKind,
      SynthesizingContext context,
      DexItemFactory factory) {
    super(type, syntheticKind, context, factory);
    setOriginKind(Kind.CF);
  }

  @Override
  public ClassKind<DexClasspathClass> getClassKind() {
    return ClassKind.CLASSPATH;
  }

  @Override
  public SyntheticClasspathClassBuilder self() {
    return this;
  }
}
