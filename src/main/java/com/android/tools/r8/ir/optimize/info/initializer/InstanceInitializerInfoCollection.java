// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MapUtils;
import com.google.common.collect.ImmutableMap;

public abstract class InstanceInitializerInfoCollection {

  public static Builder builder() {
    return new Builder();
  }

  public static InstanceInitializerInfoCollection empty() {
    return EmptyInstanceInitializerInfoCollection.getInstance();
  }

  public static InstanceInitializerInfoCollection of(InstanceInitializerInfo info) {
    if (info != null && info.isNonTrivialInstanceInitializerInfo()) {
      return new ContextInsensitiveInstanceInitializerInfoCollection(
          info.asNonTrivialInstanceInitializerInfo());
    }
    return empty();
  }

  public abstract InstanceInitializerInfo getContextInsensitive();

  public abstract InstanceInitializerInfo get(InvokeDirect invoke);

  public abstract boolean isEmpty();

  public abstract InstanceInitializerInfoCollection fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection argumentInfoCollection);

  public abstract InstanceInitializerInfoCollection rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView,
      GraphLens lens,
      GraphLens codeLens,
      PrunedItems prunedItems);

  public static class Builder {

    private final ImmutableMap.Builder<
            InstanceInitializerInfoContext, NonTrivialInstanceInitializerInfo>
        infosBuilder = ImmutableMap.builder();

    private Builder() {}

    public Builder put(InstanceInitializerInfoContext context, InstanceInitializerInfo info) {
      if (info.isNonTrivialInstanceInitializerInfo()) {
        infosBuilder.put(context, info.asNonTrivialInstanceInitializerInfo());
      }
      return this;
    }

    public InstanceInitializerInfoCollection build() {
      ImmutableMap<InstanceInitializerInfoContext, NonTrivialInstanceInitializerInfo> infos =
          infosBuilder.build();
      if (infos.isEmpty()) {
        return empty();
      }
      if (infos.size() == 1 && MapUtils.firstKey(infos).isAlwaysTrue()) {
        return new ContextInsensitiveInstanceInitializerInfoCollection(MapUtils.firstValue(infos));
      }
      return new ContextSensitiveInstanceInitializerInfoCollection(infos);
    }
  }
}
