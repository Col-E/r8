// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.art.rewriting.ProfileRewritingArgumentPropagatorSyntheticEventConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public interface ArgumentPropagatorSyntheticEventConsumer {

  void acceptInitializerArgumentClass(DexProgramClass clazz, ProgramMethod context);

  void finished(AppView<AppInfoWithLiveness> appView);

  static ArgumentPropagatorSyntheticEventConsumer create(AppView<AppInfoWithLiveness> appView) {
    return ProfileRewritingArgumentPropagatorSyntheticEventConsumer.attach(appView, empty());
  }

  static ArgumentPropagatorSyntheticEventConsumer empty() {
    return EmptyArgumentPropagatorSyntheticEventConsumer.getInstance();
  }

  class EmptyArgumentPropagatorSyntheticEventConsumer
      implements ArgumentPropagatorSyntheticEventConsumer {

    private static EmptyArgumentPropagatorSyntheticEventConsumer INSTANCE =
        new EmptyArgumentPropagatorSyntheticEventConsumer();

    private EmptyArgumentPropagatorSyntheticEventConsumer() {}

    static EmptyArgumentPropagatorSyntheticEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptInitializerArgumentClass(DexProgramClass clazz, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void finished(AppView<AppInfoWithLiveness> appView) {
      // Intentionally empty.
    }
  }
}
