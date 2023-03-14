// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.rewriting.ProfileRewritingMemberRebindingEventConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public interface MemberRebindingEventConsumer {

  void acceptMemberRebindingBridgeMethod(
      ProgramMethod bridgeMethod, DexClassAndMethod targetMethod);

  default void finished(
      AppView<AppInfoWithLiveness> appView, MemberRebindingLens memberRebindingLens) {}

  static MemberRebindingEventConsumer create(AppView<AppInfoWithLiveness> appView) {
    return ProfileRewritingMemberRebindingEventConsumer.attach(appView, empty());
  }

  static EmptyMemberRebindingEventConsumer empty() {
    return EmptyMemberRebindingEventConsumer.getInstance();
  }

  class EmptyMemberRebindingEventConsumer implements MemberRebindingEventConsumer {

    private static final EmptyMemberRebindingEventConsumer INSTANCE =
        new EmptyMemberRebindingEventConsumer();

    private EmptyMemberRebindingEventConsumer() {}

    static EmptyMemberRebindingEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptMemberRebindingBridgeMethod(
        ProgramMethod bridgeMethod, DexClassAndMethod targetMethod) {
      // Intentionally empty.
    }
  }
}
