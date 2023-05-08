// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexClassAndField;
import com.google.common.base.Equivalence;

public class DexClassAndFieldEquivalence extends Equivalence<DexClassAndField> {

  private static final DexClassAndFieldEquivalence INSTANCE = new DexClassAndFieldEquivalence();

  private DexClassAndFieldEquivalence() {}

  public static DexClassAndFieldEquivalence get() {
    return INSTANCE;
  }

  @Override
  protected boolean doEquivalent(DexClassAndField field, DexClassAndField other) {
    return field.getDefinition() == other.getDefinition();
  }

  @Override
  protected int doHash(DexClassAndField field) {
    return field.getReference().hashCode();
  }
}
