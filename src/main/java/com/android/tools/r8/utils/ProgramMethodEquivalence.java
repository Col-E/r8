// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.ProgramMethod;
import com.google.common.base.Equivalence;

public class ProgramMethodEquivalence extends Equivalence<ProgramMethod> {

  private static final ProgramMethodEquivalence INSTANCE = new ProgramMethodEquivalence();

  private ProgramMethodEquivalence() {}

  public static ProgramMethodEquivalence get() {
    return INSTANCE;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  protected boolean doEquivalent(ProgramMethod method, ProgramMethod other) {
    return method.getDefinition() == other.getDefinition();
  }

  @Override
  protected int doHash(ProgramMethod method) {
    return method.getReference().hashCode();
  }
}
