// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;

public class DexMemberAnnotation<R extends DexMember<?, R>, S extends DexItem> extends DexItem {

  public final R item;
  public final S annotations;

  public DexMemberAnnotation(R item, S annotations) {
    this.item = item;
    this.annotations = annotations;
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    annotations.collectMixedSectionItems(mixedItems);
  }

  @Override
  public int hashCode() {
    return item.hashCode() * 7 + annotations.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof DexMemberAnnotation) {
      DexMemberAnnotation<?,?> otherMember = (DexMemberAnnotation<?,?>) other;
      return item.equals(otherMember.item) && annotations.equals(otherMember.annotations);
    }
    return false;
  }

  public static class DexFieldAnnotation extends DexMemberAnnotation<DexField, DexAnnotationSet> {

    public DexFieldAnnotation(DexField item, DexAnnotationSet annotations) {
      super(item, annotations);
    }

    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      item.collectIndexedItems(appView, indexedItems);
      annotations.collectIndexedItems(appView, indexedItems);
    }
  }

  public static class DexMethodAnnotation extends DexMemberAnnotation<DexMethod, DexAnnotationSet> {

    public DexMethodAnnotation(DexMethod item, DexAnnotationSet annotations) {
      super(item, annotations);
    }

    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      item.collectIndexedItems(appView, indexedItems);
      annotations.collectIndexedItems(appView, indexedItems);
    }
  }

  public static class DexParameterAnnotation extends
      DexMemberAnnotation<DexMethod, ParameterAnnotationsList> {

    public DexParameterAnnotation(DexMethod item, ParameterAnnotationsList annotations) {
      super(item, annotations);
    }

    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      item.collectIndexedItems(appView, indexedItems);
      annotations.collectIndexedItems(appView, indexedItems);
    }
  }
}
