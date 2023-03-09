// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.structural.StructuralMapping;
import org.objectweb.asm.TypePath;

public class DexTypeAnnotation extends DexAnnotation {

  private final int typeRef;
  private final TypePath typePath;

  public DexTypeAnnotation(
      int visibility, DexEncodedAnnotation annotation, int typeRef, TypePath typePath) {
    super(visibility, annotation);
    this.typeRef = typeRef;
    this.typePath = typePath;
  }

  @Override
  public boolean isTypeAnnotation() {
    return true;
  }

  @Override
  public DexTypeAnnotation asTypeAnnotation() {
    return this;
  }

  @Override
  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    throw new Unreachable("Should not collect type annotation in DEX");
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    throw new Unreachable("Should not collect type annotation in DEX");
  }

  public int getTypeRef() {
    return typeRef;
  }

  public TypePath getTypePath() {
    return typePath;
  }

  @Override
  public StructuralMapping<DexAnnotation> getStructuralMapping() {
    return spec ->
        spec.withInt(t -> typeRef)
            .withIntArray(
                annotation -> {
                  int totalCount = typePath.getLength() * 2;
                  int[] serializedArr = new int[totalCount];
                  for (int i = 0; i < totalCount; i++) {
                    int startIndex = i * 2;
                    serializedArr[startIndex] = typePath.getStep(i);
                    serializedArr[startIndex + 1] = typePath.getStepArgument(i);
                  }
                  return serializedArr;
                })
            .withSpec(DexAnnotation::specify);
  }
}
