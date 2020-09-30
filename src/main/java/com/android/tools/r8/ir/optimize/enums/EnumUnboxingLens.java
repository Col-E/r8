// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

class EnumUnboxingLens extends GraphLens.NestedGraphLens {

  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod;
  private final Set<DexType> unboxedEnums;

  EnumUnboxingLens(
      Map<DexType, DexType> typeMap,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexField, DexField> fieldMap,
      BiMap<DexField, DexField> originalFieldSignatures,
      BiMap<DexMethod, DexMethod> originalMethodSignatures,
      GraphLens previousLens,
      DexItemFactory dexItemFactory,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod,
      Set<DexType> unboxedEnums) {
    super(
        typeMap,
        methodMap,
        fieldMap,
        originalFieldSignatures,
        originalMethodSignatures,
        previousLens,
        dexItemFactory);
    this.prototypeChangesPerMethod = prototypeChangesPerMethod;
    this.unboxedEnums = unboxedEnums;
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    // During the second IR processing enum unboxing is the only optimization rewriting
    // prototype description, if this does not hold, remove the assertion and merge
    // the two prototype changes.
    assert prototypeChanges.isEmpty();
    return prototypeChangesPerMethod.getOrDefault(method, RewrittenPrototypeDescription.none());
  }

  @Override
  protected Invoke.Type mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, Invoke.Type type) {
    if (unboxedEnums.contains(originalMethod.holder)) {
      // Methods moved from unboxed enums to the utility class are either static or statified.
      assert newMethod != originalMethod;
      return Invoke.Type.STATIC;
    }
    return type;
  }

  public static Builder enumUnboxingLensBuilder() {
    return new Builder();
  }

  static class Builder {

    protected final Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    protected final BiMap<DexField, DexField> originalFieldSignatures = HashBiMap.create();
    protected final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

    private Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod =
        new IdentityHashMap<>();

    public void map(DexType from, DexType to) {
      if (from == to) {
        return;
      }
      typeMap.put(from, to);
    }

    public void move(DexField from, DexField to) {
      if (from == to) {
        return;
      }
      originalFieldSignatures.put(to, from);
    }

    public void move(DexMethod from, DexMethod to, boolean fromStatic, boolean toStatic) {
      move(from, to, fromStatic, toStatic, 0);
    }

    public void move(
        DexMethod from,
        DexMethod to,
        boolean fromStatic,
        boolean toStatic,
        int numberOfExtraNullParameters) {
      assert from != to;
      originalMethodSignatures.put(to, from);
      int offsetDiff = 0;
      int toOffset = BooleanUtils.intValue(!toStatic);
      RewrittenPrototypeDescription.ArgumentInfoCollection.Builder builder =
          RewrittenPrototypeDescription.ArgumentInfoCollection.builder();
      if (fromStatic != toStatic) {
        assert toStatic;
        offsetDiff = 1;
        builder.addArgumentInfo(
            0,
            new RewrittenPrototypeDescription.RewrittenTypeInfo(
                from.holder, to.proto.parameters.values[0]));
      }
      for (int i = 0; i < from.proto.parameters.size(); i++) {
        DexType fromType = from.proto.parameters.values[i];
        DexType toType = to.proto.parameters.values[i + offsetDiff];
        if (fromType != toType) {
          builder.addArgumentInfo(
              i + offsetDiff + toOffset,
              new RewrittenPrototypeDescription.RewrittenTypeInfo(fromType, toType));
        }
      }
      RewrittenPrototypeDescription.RewrittenTypeInfo returnInfo =
          from.proto.returnType == to.proto.returnType
              ? null
              : new RewrittenPrototypeDescription.RewrittenTypeInfo(
                  from.proto.returnType, to.proto.returnType);
      prototypeChangesPerMethod.put(
          to,
          RewrittenPrototypeDescription.createForRewrittenTypes(returnInfo, builder.build())
              .withExtraUnusedNullParameters(numberOfExtraNullParameters));
    }

    public EnumUnboxingLens build(
        DexItemFactory dexItemFactory, GraphLens previousLens, Set<DexType> unboxedEnums) {
      if (typeMap.isEmpty()
          && originalFieldSignatures.isEmpty()
          && originalMethodSignatures.isEmpty()) {
        return null;
      }
      return new EnumUnboxingLens(
          typeMap,
          originalMethodSignatures.inverse(),
          originalFieldSignatures.inverse(),
          originalFieldSignatures,
          originalMethodSignatures,
          previousLens,
          dexItemFactory,
          ImmutableMap.copyOf(prototypeChangesPerMethod),
          ImmutableSet.copyOf(unboxedEnums));
    }
  }
}
