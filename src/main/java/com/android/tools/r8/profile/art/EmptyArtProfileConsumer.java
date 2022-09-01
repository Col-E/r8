// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.DiagnosticsHandler;

public class EmptyArtProfileConsumer implements ArtProfileConsumer {

  private static final EmptyArtProfileConsumer INSTANCE = new EmptyArtProfileConsumer();

  private EmptyArtProfileConsumer() {}

  public static EmptyArtProfileConsumer getInstance() {
    return INSTANCE;
  }

  public static ArtProfileConsumer orEmpty(ArtProfileConsumer artProfileConsumer) {
    return artProfileConsumer != null ? artProfileConsumer : getInstance();
  }

  @Override
  public ArtProfileRuleConsumer getRuleConsumer() {
    return EmptyArtProfileRuleConsumer.getInstance();
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    // Intentionally empty.
  }
}
