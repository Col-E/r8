// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.ProgramField;
import com.google.common.base.Equivalence;

public class ProgramFieldEquivalence extends Equivalence<ProgramField> {

  private static final ProgramFieldEquivalence INSTANCE = new ProgramFieldEquivalence();

  private ProgramFieldEquivalence() {}

  public static ProgramFieldEquivalence get() {
    return INSTANCE;
  }

  @Override
  protected boolean doEquivalent(ProgramField field, ProgramField other) {
    return field.getDefinition() == other.getDefinition();
  }

  @Override
  protected int doHash(ProgramField field) {
    return field.getReference().hashCode();
  }
}
