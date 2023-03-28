// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.rewriting.ProfileRewritingOutlineOptimizationEventConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collection;

public interface OutlineOptimizationEventConsumer {

  void acceptOutlineMethod(ProgramMethod method, Collection<ProgramMethod> contexts);

  void finished(AppView<AppInfoWithLiveness> appView);

  static OutlineOptimizationEventConsumer create(AppView<AppInfoWithLiveness> appView) {
    return ProfileRewritingOutlineOptimizationEventConsumer.attach(appView, empty());
  }

  static EmptyOutlineOptimizationEventConsumer empty() {
    return EmptyOutlineOptimizationEventConsumer.getInstance();
  }

  class EmptyOutlineOptimizationEventConsumer implements OutlineOptimizationEventConsumer {

    private static final EmptyOutlineOptimizationEventConsumer INSTANCE =
        new EmptyOutlineOptimizationEventConsumer();

    private EmptyOutlineOptimizationEventConsumer() {}

    static EmptyOutlineOptimizationEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptOutlineMethod(ProgramMethod method, Collection<ProgramMethod> contexts) {
      // Intentionally empty.
    }

    @Override
    public void finished(AppView<AppInfoWithLiveness> appView) {
      // Intentionally empty.
    }
  }
}
