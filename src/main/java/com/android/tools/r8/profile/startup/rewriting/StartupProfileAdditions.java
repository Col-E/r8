// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.rewriting;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.profile.AbstractProfileRule;
import com.android.tools.r8.profile.rewriting.ProfileAdditions;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.profile.startup.profile.StartupProfileClassRule;
import com.android.tools.r8.profile.startup.profile.StartupProfileMethodRule;
import com.android.tools.r8.profile.startup.profile.StartupProfileRule;
import java.util.Comparator;

public class StartupProfileAdditions
    extends ProfileAdditions<
        StartupProfileAdditions,
        StartupProfileClassRule,
        StartupProfileClassRule.Builder,
        StartupProfileMethodRule,
        StartupProfileMethodRule.Builder,
        StartupProfileRule,
        StartupProfile,
        StartupProfile.Builder> {

  public StartupProfileAdditions(StartupProfile profile) {
    super(profile);
  }

  @Override
  public StartupProfileAdditions create() {
    return new StartupProfileAdditions(profile);
  }

  @Override
  public StartupProfileClassRule.Builder createClassRuleBuilder(DexType type) {
    return StartupProfileClassRule.builder().setClassReference(type);
  }

  @Override
  public StartupProfileMethodRule.Builder createMethodRuleBuilder(DexMethod method) {
    return StartupProfileMethodRule.builder().setMethod(method);
  }

  @Override
  public StartupProfile.Builder createProfileBuilder() {
    return StartupProfile.builder();
  }

  @Override
  public Comparator<AbstractProfileRule> getRuleComparator() {
    return Comparator.comparing(AbstractProfileRule::asStartupProfileRule);
  }

  @Override
  public StartupProfileAdditions self() {
    return this;
  }
}
