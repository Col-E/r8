// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.NestedGraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

class EnumUnboxingLens extends NestedGraphLens {

  private final AbstractValueFactory abstractValueFactory;
  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod;
  private final EnumDataMap unboxedEnums;

  EnumUnboxingLens(
      AppView<?> appView,
      BidirectionalOneToOneMap<DexField, DexField> fieldMap,
      BidirectionalOneToManyRepresentativeMap<DexMethod, DexMethod> methodMap,
      Map<DexType, DexType> typeMap,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod) {
    super(appView, fieldMap, methodMap::getRepresentativeValue, typeMap, methodMap);
    assert !appView.unboxedEnums().isEmpty();
    this.abstractValueFactory = appView.abstractValueFactory();
    this.prototypeChangesPerMethod = prototypeChangesPerMethod;
    this.unboxedEnums = appView.unboxedEnums();
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    // Rewrite the single value of the given RewrittenPrototypeDescription if it is referring to an
    // unboxed enum field.
    if (prototypeChanges.hasRewrittenReturnInfo()) {
      RewrittenTypeInfo rewrittenTypeInfo = prototypeChanges.getRewrittenReturnInfo();
      if (rewrittenTypeInfo.hasSingleValue()) {
        SingleValue singleValue = rewrittenTypeInfo.getSingleValue();
        if (singleValue.isSingleFieldValue()) {
          SingleFieldValue singleFieldValue = singleValue.asSingleFieldValue();
          if (unboxedEnums.hasUnboxedValueFor(singleFieldValue.getField())) {
            prototypeChanges =
                prototypeChanges.withRewrittenReturnInfo(
                    new RewrittenTypeInfo(
                        rewrittenTypeInfo.getOldType(),
                        rewrittenTypeInfo.getNewType(),
                        abstractValueFactory.createSingleNumberValue(
                            unboxedEnums.getUnboxedValue(singleFieldValue.getField()))));
          }
        }
      }
    }

    // During the second IR processing enum unboxing is the only optimization rewriting
    // prototype description, if this does not hold, remove the assertion and merge
    // the two prototype changes.
    RewrittenPrototypeDescription enumUnboxingPrototypeChanges =
        prototypeChangesPerMethod.getOrDefault(method, RewrittenPrototypeDescription.none());
    return prototypeChanges.combine(enumUnboxingPrototypeChanges);
  }

  @Override
  protected Invoke.Type mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, Invoke.Type type) {
    if (typeMap.containsKey(originalMethod.getHolderType())) {
      // Methods moved from unboxed enums to the utility class are either static or statified.
      assert newMethod != originalMethod;
      return Invoke.Type.STATIC;
    }
    return type;
  }

  public static Builder enumUnboxingLensBuilder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  static class Builder {

    private final DexItemFactory dexItemFactory;
    private final Map<DexType, DexType> typeMap = new IdentityHashMap<>();
    private final MutableBidirectionalOneToOneMap<DexField, DexField> newFieldSignatures =
        new BidirectionalOneToOneHashMap<>();
    private final MutableBidirectionalOneToManyRepresentativeMap<DexMethod, DexMethod>
        newMethodSignatures = new BidirectionalOneToManyRepresentativeHashMap<>();

    private Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod =
        new IdentityHashMap<>();

    Builder(AppView<AppInfoWithLiveness> appView) {
      this.dexItemFactory = appView.dexItemFactory();
    }

    public Builder mapUnboxedEnums(Set<DexType> enumsToUnbox) {
      for (DexType enumToUnbox : enumsToUnbox) {
        typeMap.put(enumToUnbox, dexItemFactory.intType);
      }
      return this;
    }

    public void move(DexField from, DexField to) {
      if (from == to) {
        return;
      }
      newFieldSignatures.put(from, to);
    }

    public void move(DexMethod from, DexMethod to, boolean fromStatic, boolean toStatic) {
      move(from, to, fromStatic, toStatic, 0);
    }

    public RewrittenPrototypeDescription move(
        DexMethod from,
        DexMethod to,
        boolean fromStatic,
        boolean toStatic,
        int numberOfExtraNullParameters) {
      assert from != to;
      newMethodSignatures.put(from, to);
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
      RewrittenPrototypeDescription prototypeChanges =
          RewrittenPrototypeDescription.createForRewrittenTypes(returnInfo, builder.build())
              .withExtraUnusedNullParameters(numberOfExtraNullParameters);
      prototypeChangesPerMethod.put(to, prototypeChanges);
      return prototypeChanges;
    }

    void recordCheckNotZeroMethod(
        ProgramMethod checkNotNullMethod, ProgramMethod checkNotZeroMethod) {
      DexMethod originalCheckNotNullMethodSignature =
          newMethodSignatures.getKeyOrDefault(
              checkNotNullMethod.getReference(), checkNotNullMethod.getReference());
      newMethodSignatures.put(
          originalCheckNotNullMethodSignature, checkNotNullMethod.getReference());
      newMethodSignatures.put(
          originalCheckNotNullMethodSignature, checkNotZeroMethod.getReference());
      newMethodSignatures.setRepresentative(
          originalCheckNotNullMethodSignature, checkNotNullMethod.getReference());
    }

    public EnumUnboxingLens build(AppView<?> appView) {
      assert !typeMap.isEmpty();
      return new EnumUnboxingLens(
          appView,
          newFieldSignatures,
          newMethodSignatures,
          typeMap,
          ImmutableMap.copyOf(prototypeChangesPerMethod));
    }
  }
}
