// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;

public class DexAnnotationElement extends DexItem implements StructuralItem<DexAnnotationElement> {
  public static final DexAnnotationElement[] EMPTY_ARRAY = {};

  public final DexString name;
  public final DexValue value;

  private static void specify(StructuralSpecification<DexAnnotationElement, ?> spec) {
    spec.withItem(e -> e.name).withItem(e -> e.value);
  }

  public DexAnnotationElement(DexString name, DexValue value) {
    this.name = name;
    this.value = value;
  }

  @Override
  public DexAnnotationElement self() {
    return this;
  }

  @Override
  public StructuralMapping<DexAnnotationElement> getStructuralMapping() {
    return DexAnnotationElement::specify;
  }

  public DexValue getValue() {
    return value;
  }

  @Override
  public int hashCode() {
    return name.hashCode() + value.hashCode() * 3;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof DexAnnotationElement) {
      DexAnnotationElement o = (DexAnnotationElement) other;
      return name.equals(o.name) && value.equals(o.value);
    }
    return false;
  }

  @Override
  public String toString() {
    return name + "=" + value;
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    name.collectIndexedItems(indexedItems);
    value.collectIndexedItems(appView, indexedItems);
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    // Should never be visited.
    assert false;
  }

  public DexString getName() {
    return name;
  }
}
