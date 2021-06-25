// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeterPostProcessor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.RetargetingInfo;
import java.util.Collections;
import java.util.List;

public abstract class CfPostProcessingDesugaringCollection {

  public static CfPostProcessingDesugaringCollection create(
      AppView<?> appView, RetargetingInfo retargetingInfo) {
    if (appView.options().desugarState.isOn()) {
      return NonEmptyCfPostProcessingDesugaringCollection.create(appView, retargetingInfo);
    }
    return empty();
  }

  static CfPostProcessingDesugaringCollection empty() {
    return EmptyCfPostProcessingDesugaringCollection.getInstance();
  }

  public abstract void postProcessingDesugaring(
      CfPostProcessingDesugaringEventConsumer eventConsumer);

  public static class NonEmptyCfPostProcessingDesugaringCollection
      extends CfPostProcessingDesugaringCollection {

    private final List<CfPostProcessingDesugaring> desugarings;

    public NonEmptyCfPostProcessingDesugaringCollection(
        List<CfPostProcessingDesugaring> desugarings) {
      this.desugarings = desugarings;
    }

    public static CfPostProcessingDesugaringCollection create(
        AppView<?> appView, RetargetingInfo retargetingInfo) {
      if (appView.options().desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty()) {
        return empty();
      }
      return new NonEmptyCfPostProcessingDesugaringCollection(
          Collections.singletonList(
              new DesugaredLibraryRetargeterPostProcessor(appView, retargetingInfo)));
    }

    @Override
    public void postProcessingDesugaring(CfPostProcessingDesugaringEventConsumer eventConsumer) {
      for (CfPostProcessingDesugaring desugaring : desugarings) {
        desugaring.postProcessingDesugaring(eventConsumer);
      }
    }
  }

  public static class EmptyCfPostProcessingDesugaringCollection
      extends CfPostProcessingDesugaringCollection {

    private static final EmptyCfPostProcessingDesugaringCollection INSTANCE =
        new EmptyCfPostProcessingDesugaringCollection();

    private EmptyCfPostProcessingDesugaringCollection() {}

    private static EmptyCfPostProcessingDesugaringCollection getInstance() {
      return INSTANCE;
    }

    @Override
    public void postProcessingDesugaring(CfPostProcessingDesugaringEventConsumer eventConsumer) {
      // Intentionally empty.
    }
  }
}
