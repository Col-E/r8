// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.model;

import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents an ART baseline profile (for testing).
 *
 * <p>This is similar to {@link com.android.tools.r8.profile.art.ArtProfile}, but unlike ArtProfile,
 * this ExternalArtProfile is backed by {@link com.android.tools.r8.references.Reference}, and not
 * {@link com.android.tools.r8.graph.DexItem}, so that no {@link
 * com.android.tools.r8.graph.DexItemFactory} is needed to create an ExternalArtProfile.
 */
public class ExternalArtProfile {

  private final List<ExternalArtProfileRule> rules;

  ExternalArtProfile(List<ExternalArtProfileRule> rules) {
    this.rules = rules;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void forEach(
      Consumer<ExternalArtProfileClassRule> classRuleConsumer,
      Consumer<ExternalArtProfileMethodRule> methodRuleConsumer) {
    for (ExternalArtProfileRule rule : rules) {
      rule.accept(classRuleConsumer, methodRuleConsumer);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ExternalArtProfile profile = (ExternalArtProfile) obj;
    return rules.equals(profile.rules);
  }

  @Override
  public int hashCode() {
    return rules.hashCode();
  }

  @Override
  public String toString() {
    return StringUtils.join(System.lineSeparator(), rules, ExternalArtProfileRule::toString);
  }

  public static class Builder {

    private final List<ExternalArtProfileRule> rules = new ArrayList<>();

    public Builder addClassRule(ClassReference classReference) {
      return addRule(
          ExternalArtProfileClassRule.builder().setClassReference(classReference).build());
    }

    public Builder addMethodRule(MethodReference methodReference) {
      return addMethodRule(methodReference, ArtProfileMethodRuleInfoImpl.empty());
    }

    public Builder addMethodRule(
        MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
      return addRule(
          ExternalArtProfileMethodRule.builder()
              .setMethodReference(methodReference)
              .setMethodRuleInfo(methodRuleInfo)
              .build());
    }

    public Builder addRule(ExternalArtProfileRule rule) {
      rules.add(rule);
      return this;
    }

    public Builder addRules(ExternalArtProfileRule... rules) {
      Collections.addAll(this.rules, rules);
      return this;
    }

    public ExternalArtProfile build() {
      return new ExternalArtProfile(rules);
    }
  }
}
