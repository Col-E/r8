// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;

public class DexEncodedAnnotation extends DexItem implements StructuralItem<DexEncodedAnnotation> {

  private static final int UNSORTED = 0;

  public final DexType type;
  public final DexAnnotationElement[] elements;

  private int sorted = UNSORTED;

  private static void specify(StructuralSpecification<DexEncodedAnnotation, ?> spec) {
    spec.withItem(a -> a.type).withItemArray(a -> a.elements);
  }

  public DexEncodedAnnotation(DexType type, DexAnnotationElement[] elements) {
    this.type = type;
    this.elements = elements;
  }

  @Override
  public DexEncodedAnnotation self() {
    return this;
  }

  @Override
  public StructuralMapping<DexEncodedAnnotation> getStructuralMapping() {
    return DexEncodedAnnotation::specify;
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    type.collectIndexedItems(appView, indexedItems);
    for (DexAnnotationElement element : elements) {
      element.collectIndexedItems(appView, indexedItems);
    }
  }

  public void forEachElement(Consumer<DexAnnotationElement> consumer) {
    for (DexAnnotationElement element : elements) {
      consumer.accept(element);
    }
  }

  public DexAnnotationElement getElement(int i) {
    return elements[i];
  }

  public int getNumberOfElements() {
    return elements.length;
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    // Should never be called.
    assert false;
  }

  @Override
  public String toString() {
    return "Encoded annotation " + type + " " + Arrays.toString(elements);
  }

  @Override
  public int hashCode() {
    return type.hashCode() * 7 + Arrays.hashCode(elements);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof DexEncodedAnnotation) {
      DexEncodedAnnotation that = (DexEncodedAnnotation) other;
      return that.type.equals(type) && Arrays.equals(that.elements, elements);
    }
    return false;
  }

  public void sort() {
    if (sorted != UNSORTED) {
      assert sorted == sortedHashCode();
      return;
    }
    Arrays.sort(elements, (a, b) -> a.name.compareTo(b.name));
    for (DexAnnotationElement element : elements) {
      element.value.sort();
    }
    sorted = sortedHashCode();
  }

  private int sortedHashCode() {
    int hashCode = hashCode();
    return hashCode == UNSORTED ? 1 : hashCode;
  }

  @SuppressWarnings("ReferenceEquality")
  public DexEncodedAnnotation rewrite(
      Function<DexType, DexType> typeRewriter,
      Function<DexAnnotationElement, DexAnnotationElement> elementRewriter) {
    DexType rewrittenType = typeRewriter.apply(type);
    DexAnnotationElement[] rewrittenElements =
        ArrayUtils.map(elements, elementRewriter, DexAnnotationElement.EMPTY_ARRAY);
    if (rewrittenType == type && rewrittenElements == elements) {
      return this;
    }
    return new DexEncodedAnnotation(rewrittenType, rewrittenElements);
  }
}
