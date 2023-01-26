// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.profile.art.ArtProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;

public abstract class MethodRuleAdditionConfig {

  public static MethodRuleAdditionConfig getDefault() {
    return DefaultMethodRuleAdditionConfig.getInstance();
  }

  public abstract void configureMethodRuleInfo(
      ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder,
      ArtProfileMethodRule contextMethodRule);

  private static class DefaultMethodRuleAdditionConfig extends MethodRuleAdditionConfig {

    private static final DefaultMethodRuleAdditionConfig INSTANCE =
        new DefaultMethodRuleAdditionConfig();

    private DefaultMethodRuleAdditionConfig() {}

    static DefaultMethodRuleAdditionConfig getInstance() {
      return INSTANCE;
    }

    @Override
    public void configureMethodRuleInfo(
        ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder,
        ArtProfileMethodRule contextMethodRule) {
      methodRuleInfoBuilder.joinFlags(contextMethodRule.getMethodRuleInfo());
    }
  }
}
