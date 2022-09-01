// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;

public class EmptyArtProfileRuleConsumer implements ArtProfileRuleConsumer {

  private static final EmptyArtProfileRuleConsumer INSTANCE = new EmptyArtProfileRuleConsumer();

  private EmptyArtProfileRuleConsumer() {}

  public static EmptyArtProfileRuleConsumer getInstance() {
    return INSTANCE;
  }

  public static ArtProfileRuleConsumer orEmpty(ArtProfileRuleConsumer ruleConsumer) {
    return ruleConsumer != null ? ruleConsumer : getInstance();
  }

  @Override
  public void acceptClassRule(
      ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo) {
    // Intentionally empty.
  }

  @Override
  public void acceptMethodRule(
      MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
    // Intentionally empty.
  }
}
