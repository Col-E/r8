// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.CachedHashValueDexItem;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.InternalOptions;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

abstract class MemberNameMinifier<MemberType, StateType extends CachedHashValueDexItem> {

  protected final AppInfoWithLiveness appInfo;
  protected final RootSet rootSet;
  protected final InternalOptions options;
  protected final List<String> dictionary;

  protected final Map<MemberType, DexString> renaming = new IdentityHashMap<>();
  protected final Map<DexType, NamingState<StateType, ?>> states = new IdentityHashMap<>();
  protected final NamingState<StateType, ?> globalState;
  protected final boolean useUniqueMemberNames;
  protected final boolean overloadAggressively;

  MemberNameMinifier(AppInfoWithLiveness appInfo, RootSet rootSet, InternalOptions options) {
    this.appInfo = appInfo;
    this.rootSet = rootSet;
    this.options = options;
    this.dictionary = options.getProguardConfiguration().getObfuscationDictionary();
    this.useUniqueMemberNames = options.getProguardConfiguration().isUseUniqueClassMemberNames();
    this.overloadAggressively =
        options.getProguardConfiguration().isOverloadAggressivelyWithoutUseUniqueClassMemberNames();
    this.globalState = NamingState.createRoot(
        appInfo.dexItemFactory, dictionary, getKeyTransform(), useUniqueMemberNames);
  }

  abstract Function<StateType, ?> getKeyTransform();

  protected NamingState<StateType, ?> computeStateIfAbsent(
      DexType type, Function<DexType, NamingState<StateType, ?>> f) {
    return useUniqueMemberNames ? globalState : states.computeIfAbsent(type, f);
  }

  protected NamingState<StateType, ?> getState(DexType type) {
    return useUniqueMemberNames ? globalState : states.get(type);
  }
}
