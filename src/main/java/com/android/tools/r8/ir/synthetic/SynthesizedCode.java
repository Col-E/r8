// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.UseRegistry;
import java.util.function.Consumer;

public abstract class SynthesizedCode extends AbstractSynthesizedCode {

  private final SourceCodeProvider sourceCodeProvider;

  public SynthesizedCode(SourceCodeProvider sourceCodeProvider) {
    this.sourceCodeProvider = sourceCodeProvider;
  }

  @Override
  public SourceCodeProvider getSourceCodeProvider() {
    return sourceCodeProvider;
  }

  @Override
  public abstract Consumer<UseRegistry> getRegistryCallback(DexClassAndMethod method);
}
