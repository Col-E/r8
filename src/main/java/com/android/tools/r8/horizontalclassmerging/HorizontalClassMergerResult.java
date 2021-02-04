// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;

public class HorizontalClassMergerResult {

  private final FieldAccessInfoCollectionModifier fieldAccessInfoCollectionModifier;
  private final HorizontalClassMergerGraphLens graphLens;

  HorizontalClassMergerResult(
      FieldAccessInfoCollectionModifier fieldAccessInfoCollectionModifier,
      HorizontalClassMergerGraphLens graphLens) {
    this.fieldAccessInfoCollectionModifier = fieldAccessInfoCollectionModifier;
    this.graphLens = graphLens;
  }

  public FieldAccessInfoCollectionModifier getFieldAccessInfoCollectionModifier() {
    return fieldAccessInfoCollectionModifier;
  }

  public HorizontalClassMergerGraphLens getGraphLens() {
    return graphLens;
  }
}
