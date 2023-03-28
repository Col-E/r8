// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This lens is used to populate the rebound field reference during lookup, such that both the
 * non-rebound and rebound field references are available to all descendants of this lens.
 *
 * <p>TODO(b/157616970): All uses of this should be replaced by {@link MemberRebindingIdentityLens}.
 */
public class FieldRebindingIdentityLens extends DefaultNonIdentityGraphLens {

  private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap;

  private FieldRebindingIdentityLens(
      Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap,
      DexItemFactory dexItemFactory,
      GraphLens previousLens) {
    super(dexItemFactory, previousLens);
    this.nonReboundFieldReferenceToDefinitionMap = nonReboundFieldReferenceToDefinitionMap;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean hasCodeRewritings() {
    return false;
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    assert !previous.hasReadCastType();
    assert !previous.hasReboundReference();
    return FieldLookupResult.builder(this)
        .setReference(previous.getReference())
        .setReboundReference(getReboundFieldReference(previous.getReference()))
        .build();
  }

  private DexField getReboundFieldReference(DexField field) {
    return nonReboundFieldReferenceToDefinitionMap.getOrDefault(field, field);
  }

  public static class Builder {

    private final Map<DexField, DexField> nonReboundFieldReferenceToDefinitionMap =
        new IdentityHashMap<>();

    private Builder() {}

    void recordDefinitionForNonReboundFieldReference(
        DexField nonReboundFieldReference, DexField reboundFieldReference) {
      nonReboundFieldReferenceToDefinitionMap.put(nonReboundFieldReference, reboundFieldReference);
    }

    FieldRebindingIdentityLens build(DexItemFactory dexItemFactory) {
      // This intentionally does not return null when the map is empty. In this case there are no
      // non-rebound field references, but the member rebinding lens is still needed to populate the
      // rebound reference during field lookup.
      return new FieldRebindingIdentityLens(
          nonReboundFieldReferenceToDefinitionMap, dexItemFactory, GraphLens.getIdentityLens());
    }
  }
}
