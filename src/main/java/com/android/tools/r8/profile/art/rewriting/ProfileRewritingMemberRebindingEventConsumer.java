// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.MemberRebindingEventConsumer;
import com.android.tools.r8.optimize.MemberRebindingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ProfileRewritingMemberRebindingEventConsumer implements MemberRebindingEventConsumer {

  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final MemberRebindingEventConsumer parent;

  private ProfileRewritingMemberRebindingEventConsumer(
      ConcreteProfileCollectionAdditions additionsCollection, MemberRebindingEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static MemberRebindingEventConsumer attach(
      AppView<AppInfoWithLiveness> appView, MemberRebindingEventConsumer eventConsumer) {
    ProfileCollectionAdditions additionsCollection = ProfileCollectionAdditions.create(appView);
    if (additionsCollection.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingMemberRebindingEventConsumer(
        additionsCollection.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptMemberRebindingBridgeMethod(
      ProgramMethod bridgeMethod, DexClassAndMethod targetMethod) {
    additionsCollection.addMethodIfContextIsInProfile(bridgeMethod, targetMethod, emptyConsumer());
    parent.acceptMemberRebindingBridgeMethod(bridgeMethod, targetMethod);
  }

  @Override
  public void finished(
      AppView<AppInfoWithLiveness> appView, MemberRebindingLens memberRebindingLens) {
    additionsCollection.commit(appView);
    parent.finished(appView, memberRebindingLens);
  }
}
