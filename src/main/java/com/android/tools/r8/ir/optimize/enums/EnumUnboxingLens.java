// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnumUnboxingLens extends NestedGraphLens {

  private final AbstractValueFactory abstractValueFactory;
  private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod;
  private final EnumDataMap unboxedEnums;

  EnumUnboxingLens(
      AppView<?> appView,
      BidirectionalOneToOneMap<DexField, DexField> fieldMap,
      BidirectionalOneToManyRepresentativeMap<DexMethod, DexMethod> renamedSignatures,
      Map<DexType, DexType> typeMap,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod) {
    super(appView, fieldMap, methodMap, typeMap, renamedSignatures);
    assert !appView.unboxedEnums().isEmpty();
    this.abstractValueFactory = appView.abstractValueFactory();
    this.prototypeChangesPerMethod = prototypeChangesPerMethod;
    this.unboxedEnums = appView.unboxedEnums();
  }

  @Override
  public boolean hasCustomCodeRewritings() {
    return true;
  }

  @Override
  public boolean isEnumUnboxerLens() {
    return true;
  }

  @Override
  public EnumUnboxingLens asEnumUnboxerLens() {
    return this;
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    return !unboxedEnums.hasAnyEnumsWithSubtypes()
        && getPrevious().isContextFreeForMethods(codeLens);
  }

  @Override
  public boolean verifyIsContextFreeForMethod(DexMethod method, GraphLens codeLens) {
    if (codeLens == this) {
      return true;
    }
    assert getPrevious().verifyIsContextFreeForMethod(getPreviousMethodSignature(method), codeLens);
    DexMethod previous =
        getPrevious()
            .lookupMethod(getPreviousMethodSignature(method), null, null, codeLens)
            .getReference();
    assert unboxedEnums.representativeType(previous.getHolderType()) == previous.getHolderType();
    return true;
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    assert context != null || verifyIsContextFreeForMethod(previous.getReference(), codeLens);
    assert context == null || previous.getType() != null;
    DexMethod result;
    if (previous.getType() == InvokeType.SUPER) {
      assert context != null;
      DexType superEnum = unboxedEnums.representativeType(context.getHolderType());
      if (superEnum != context.getHolderType()) {
        // TODO(b/271385332): implement this.
        throw new Unreachable();
      } else {
        result = methodMap.apply(previous.getReference());
      }
    } else {
      result = methodMap.apply(previous.getReference());
    }
    if (result == null) {
      return previous;
    }
    return MethodLookupResult.builder(this)
        .setReference(result)
        .setPrototypeChanges(
            internalDescribePrototypeChanges(previous.getPrototypeChanges(), result))
        .setType(mapInvocationType(result, previous.getReference(), previous.getType()))
        .build();
  }

  @Override
  protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
      RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
    // Rewrite the single value of the given RewrittenPrototypeDescription if it is referring to an
    // unboxed enum field.
    if (prototypeChanges.hasRewrittenReturnInfo()) {
      RewrittenTypeInfo rewrittenReturnInfo = prototypeChanges.getRewrittenReturnInfo();
      if (rewrittenReturnInfo.hasSingleValue()) {
        SingleValue singleValue = rewrittenReturnInfo.getSingleValue();
        SingleValue rewrittenSingleValue = rewriteSingleValue(singleValue);
        if (rewrittenSingleValue != singleValue) {
          prototypeChanges =
              prototypeChanges.withRewrittenReturnInfo(
                  RewrittenTypeInfo.builder()
                      .setCastType(rewrittenReturnInfo.getCastType())
                      .setOldType(rewrittenReturnInfo.getOldType())
                      .setNewType(rewrittenReturnInfo.getNewType())
                      .setSingleValue(rewrittenSingleValue)
                      .build());
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

  private SingleValue rewriteSingleValue(SingleValue singleValue) {
    if (singleValue.isSingleFieldValue()) {
      SingleFieldValue singleFieldValue = singleValue.asSingleFieldValue();
      if (unboxedEnums.hasUnboxedValueFor(singleFieldValue.getField())) {
        return abstractValueFactory.createSingleNumberValue(
            unboxedEnums.getUnboxedValue(singleFieldValue.getField()));
      }
    }
    return singleValue;
  }

  @Override
  protected InvokeType mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, InvokeType type) {
    if (typeMap.containsKey(originalMethod.getHolderType())) {
      // Methods moved from unboxed enums to the utility class are either static or statified.
      assert newMethod != originalMethod;
      return InvokeType.STATIC;
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
    private final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();

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

    private RewrittenPrototypeDescription recordPrototypeChanges(
        DexMethod from,
        DexMethod to,
        boolean fromStatic,
        boolean toStatic,
        boolean virtualReceiverAlreadyRemapped,
        List<ExtraUnusedNullParameter> extraUnusedNullParameters) {
      assert from != to;
      RewrittenPrototypeDescription prototypeChanges =
          computePrototypeChanges(
              from,
              to,
              fromStatic,
              toStatic,
              virtualReceiverAlreadyRemapped,
              extraUnusedNullParameters);
      prototypeChangesPerMethod.put(to, prototypeChanges);
      return prototypeChanges;
    }

    public void moveAndMap(DexMethod from, DexMethod to, boolean fromStatic) {
      moveAndMap(from, to, fromStatic, true, Collections.emptyList());
    }

    public RewrittenPrototypeDescription moveVirtual(DexMethod from, DexMethod to) {
      newMethodSignatures.put(from, to);
      return recordPrototypeChanges(from, to, false, true, false, Collections.emptyList());
    }

    public RewrittenPrototypeDescription mapToDispatch(DexMethod from, DexMethod to) {
      methodMap.put(from, to);
      return recordPrototypeChanges(from, to, false, true, true, Collections.emptyList());
    }

    public RewrittenPrototypeDescription moveAndMap(
        DexMethod from,
        DexMethod to,
        boolean fromStatic,
        boolean toStatic,
        List<ExtraUnusedNullParameter> extraUnusedNullParameters) {
      newMethodSignatures.put(from, to);
      methodMap.put(from, to);
      return recordPrototypeChanges(
          from, to, fromStatic, toStatic, false, extraUnusedNullParameters);
    }

    private RewrittenPrototypeDescription computePrototypeChanges(
        DexMethod from,
        DexMethod to,
        boolean fromStatic,
        boolean toStatic,
        boolean virtualReceiverAlreadyRemapped,
        List<ExtraUnusedNullParameter> extraUnusedNullParameters) {
      int offsetDiff = 0;
      int toOffset = BooleanUtils.intValue(!toStatic);
      ArgumentInfoCollection.Builder builder =
          ArgumentInfoCollection.builder()
              .setArgumentInfosSize(from.getNumberOfArguments(fromStatic));
      if (fromStatic != toStatic) {
        assert toStatic;
        offsetDiff = 1;
        if (!virtualReceiverAlreadyRemapped) {
          builder
              .addArgumentInfo(
                  0,
                  RewrittenTypeInfo.builder()
                      .setOldType(from.getHolderType())
                      .setNewType(to.getParameter(0))
                      .build())
              .setIsConvertedToStaticMethod();
        } else {
          assert to.getParameter(0).isIntType();
          assert !fromStatic;
          assert toStatic;
          assert from.getArity() == to.getArity() - 1;
        }
      }
      for (int i = 0; i < from.getParameters().size(); i++) {
        DexType fromType = from.getParameter(i);
        DexType toType = to.getParameter(i + offsetDiff);
        if (fromType != toType) {
          builder.addArgumentInfo(
              i + offsetDiff + toOffset,
              RewrittenTypeInfo.builder().setOldType(fromType).setNewType(toType).build());
        }
      }
      RewrittenTypeInfo returnInfo =
          from.getReturnType() == to.getReturnType()
              ? null
              : RewrittenTypeInfo.builder()
                  .setOldType(from.getReturnType())
                  .setNewType(to.getReturnType())
                  .build();
      return RewrittenPrototypeDescription.createForRewrittenTypes(returnInfo, builder.build())
          .withExtraParameters(extraUnusedNullParameters);
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
          methodMap,
          ImmutableMap.copyOf(prototypeChangesPerMethod));
    }
  }
}
