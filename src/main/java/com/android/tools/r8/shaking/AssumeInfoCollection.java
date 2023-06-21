// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.Timing;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AssumeInfoCollection {

  private final Map<DexMember<?, ?>, AssumeInfo> backing;

  AssumeInfoCollection(Map<DexMember<?, ?>, AssumeInfo> backing) {
    assert backing.values().stream().noneMatch(AssumeInfo::isEmpty);
    this.backing = backing;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean contains(DexClassAndMember<?, ?> member) {
    return backing.containsKey(member.getReference());
  }

  public AssumeInfo get(DexMember<?, ?> member) {
    return backing.getOrDefault(member, AssumeInfo.empty());
  }

  public AssumeInfo get(DexClassAndMember<?, ?> member) {
    return get(member.getReference());
  }

  public boolean isEmpty() {
    return backing.isEmpty();
  }

  public boolean isMaterializableInAllContexts(
      AppView<AppInfoWithLiveness> appView, DexClassAndMember<?, ?> member) {
    AbstractValue assumeValue = get(member).getAssumeValue();
    return assumeValue.isSingleValue()
        && assumeValue.asSingleValue().isMaterializableInAllContexts(appView);
  }

  public boolean isSideEffectFree(DexMember<?, ?> member) {
    return get(member).isSideEffectFree();
  }

  public boolean isSideEffectFree(DexClassAndMember<?, ?> member) {
    return isSideEffectFree(member.getReference());
  }

  public AssumeInfoCollection rewrittenWithLens(
      AppView<?> appView, GraphLens graphLens, GraphLens appliedLens, Timing timing) {
    return timing.time(
        "Rewrite AssumeInfoCollection", () -> rewrittenWithLens(appView, graphLens, appliedLens));
  }

  private AssumeInfoCollection rewrittenWithLens(
      AppView<?> appView, GraphLens graphLens, GraphLens appliedLens) {
    Map<DexMember<?, ?>, AssumeInfo> rewrittenCollection = new IdentityHashMap<>();
    backing.forEach(
        (reference, info) -> {
          DexMember<?, ?> rewrittenReference =
              graphLens.getRenamedMemberSignature(reference, appliedLens);
          AssumeInfo rewrittenInfo = info.rewrittenWithLens(appView, graphLens);
          assert !rewrittenInfo.isEmpty();
          rewrittenCollection.put(rewrittenReference, rewrittenInfo);
        });
    return new AssumeInfoCollection(rewrittenCollection);
  }

  public AssumeInfoCollection withoutPrunedItems(PrunedItems prunedItems, Timing timing) {
    timing.begin("Prune AssumeInfoCollection");
    Map<DexMember<?, ?>, AssumeInfo> rewrittenCollection = new IdentityHashMap<>();
    backing.forEach(
        (reference, info) -> {
          if (!prunedItems.isRemoved(reference)) {
            AssumeInfo rewrittenInfo = info.withoutPrunedItems(prunedItems);
            if (!rewrittenInfo.isEmpty()) {
              rewrittenCollection.put(reference, rewrittenInfo);
            }
          }
        });
    AssumeInfoCollection result = new AssumeInfoCollection(rewrittenCollection);
    timing.end();
    return result;
  }

  public static class Builder {

    private final Map<DexMember<?, ?>, AssumeInfo.Builder> backing = new ConcurrentHashMap<>();

    public Builder applyIf(boolean condition, Consumer<Builder> consumer) {
      if (condition) {
        consumer.accept(this);
      }
      return this;
    }

    public AssumeInfo buildInfo(DexClassAndMember<?, ?> member) {
      AssumeInfo.Builder builder = backing.get(member.getReference());
      return builder != null ? builder.build() : AssumeInfo.empty();
    }

    private AssumeInfo.Builder getOrCreateAssumeInfo(DexMember<?, ?> member) {
      return backing.computeIfAbsent(member, ignoreKey(AssumeInfo::builder));
    }

    private AssumeInfo.Builder getOrCreateAssumeInfo(DexClassAndMember<?, ?> member) {
      return getOrCreateAssumeInfo(member.getReference());
    }

    public boolean isEmpty() {
      return backing.isEmpty();
    }

    public Builder meet(DexMember<?, ?> member, AssumeInfo assumeInfo) {
      getOrCreateAssumeInfo(member).meet(assumeInfo);
      return this;
    }

    public Builder meetAssumeType(DexClassAndMember<?, ?> member, DynamicType assumeType) {
      getOrCreateAssumeInfo(member).meetAssumeType(assumeType);
      return this;
    }

    public Builder meetAssumeValue(DexMember<?, ?> member, AbstractValue assumeValue) {
      getOrCreateAssumeInfo(member).meetAssumeValue(assumeValue);
      return this;
    }

    public Builder meetAssumeValue(DexClassAndMember<?, ?> member, AbstractValue assumeValue) {
      return meetAssumeValue(member.getReference(), assumeValue);
    }

    public Builder setIsSideEffectFree(DexMember<?, ?> member) {
      getOrCreateAssumeInfo(member).setIsSideEffectFree();
      return this;
    }

    public Builder setIsSideEffectFree(DexClassAndMember<?, ?> member) {
      return setIsSideEffectFree(member.getReference());
    }

    public AssumeInfoCollection build() {
      return new AssumeInfoCollection(
          MapUtils.newIdentityHashMap(
              builder ->
                  backing.forEach(
                      (reference, infoBuilder) -> {
                        AssumeInfo info = infoBuilder.build();
                        if (!info.isEmpty()) {
                          builder.accept(reference, info);
                        }
                      }),
              backing.size()));
    }
  }
}
