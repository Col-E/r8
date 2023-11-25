// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.profile.AbstractProfileRule;
import com.android.tools.r8.profile.art.ArtProfile;
import com.android.tools.r8.profile.art.ArtProfileClassRule;
import com.android.tools.r8.profile.art.ArtProfileClassRule.Builder;
import com.android.tools.r8.profile.art.ArtProfileMethodRule;
import com.android.tools.r8.profile.art.ArtProfileRule;
import com.android.tools.r8.profile.rewriting.ProfileAdditions;
import java.util.Comparator;

@SuppressWarnings("BadImport")
public class ArtProfileAdditions
    extends ProfileAdditions<
        ArtProfileAdditions,
        ArtProfileClassRule,
        Builder,
        ArtProfileMethodRule,
        ArtProfileMethodRule.Builder,
        ArtProfileRule,
        ArtProfile,
        ArtProfile.Builder> {

  public ArtProfileAdditions(ArtProfile profile) {
    super(profile);
  }

  @Override
  public ArtProfileAdditions create() {
    return new ArtProfileAdditions(profile);
  }

  @Override
  public ArtProfileClassRule.Builder createClassRuleBuilder(DexType type) {
    return ArtProfileClassRule.builder().setType(type);
  }

  @Override
  public ArtProfileMethodRule.Builder createMethodRuleBuilder(DexMethod method) {
    return ArtProfileMethodRule.builder().setMethod(method);
  }

  @Override
  public ArtProfile.Builder createProfileBuilder() {
    return ArtProfile.builder();
  }

  @Override
  public Comparator<AbstractProfileRule> getRuleComparator() {
    return Comparator.comparing(AbstractProfileRule::asArtProfileRule);
  }

  @Override
  @SuppressWarnings("BadImport")
  public ArtProfileAdditions self() {
    return this;
  }
}
