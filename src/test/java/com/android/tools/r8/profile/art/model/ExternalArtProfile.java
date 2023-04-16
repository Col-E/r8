// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.model;

import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import java.util.LinkedHashMap;
import java.util.Map;
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

  private abstract static class ReferenceBox<R> {

    private final R reference;

    ReferenceBox(R reference) {
      this.reference = reference;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ReferenceBox<?> that = (ReferenceBox<?>) o;
      return reference.equals(that.reference);
    }

    @Override
    public int hashCode() {
      return reference.hashCode();
    }
  }

  private static class ClassReferenceBox extends ReferenceBox<ClassReference> {

    ClassReferenceBox(ClassReference reference) {
      super(reference);
    }
  }

  private static class MethodReferenceBox extends ReferenceBox<MethodReference> {

    MethodReferenceBox(MethodReference reference) {
      super(reference);
    }
  }

  private final Map<ReferenceBox<?>, ExternalArtProfileRule> rules;

  ExternalArtProfile(Map<ReferenceBox<?>, ExternalArtProfileRule> rules) {
    this.rules = rules;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean containsClassRule(ClassReference classReference) {
    return rules.containsKey(new ClassReferenceBox(classReference));
  }

  public boolean containsMethodRule(MethodReference methodReference) {
    return rules.containsKey(new MethodReferenceBox(methodReference));
  }

  public void forEach(
      Consumer<ExternalArtProfileClassRule> classRuleConsumer,
      Consumer<ExternalArtProfileMethodRule> methodRuleConsumer) {
    for (ExternalArtProfileRule rule : rules.values()) {
      rule.accept(classRuleConsumer, methodRuleConsumer);
    }
  }

  public ExternalArtProfileClassRule getClassRule(ClassReference classReference) {
    return (ExternalArtProfileClassRule) rules.get(new ClassReferenceBox(classReference));
  }

  public ExternalArtProfileMethodRule getMethodRule(MethodReference methodReference) {
    return (ExternalArtProfileMethodRule) rules.get(new MethodReferenceBox(methodReference));
  }

  public int size() {
    return rules.size();
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
    return StringUtils.join(
        System.lineSeparator(), rules.values(), ExternalArtProfileRule::toString);
  }

  public static class Builder {

    private final Map<ReferenceBox<?>, ExternalArtProfileRule> rules = new LinkedHashMap<>();

    public Builder addClassRule(ClassReference classReference) {
      return addRule(
          ExternalArtProfileClassRule.builder().setClassReference(classReference).build());
    }

    public Builder addMethodRule(MethodReference methodReference) {
      return addMethodRule(
          methodReference,
          ArtProfileMethodRuleInfoImpl.builder()
              .setIsHot()
              .setIsStartup()
              .setIsPostStartup()
              .build());
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
      rule.accept(
          classRule -> rules.put(new ClassReferenceBox(classRule.getClassReference()), classRule),
          methodRule ->
              rules.put(new MethodReferenceBox(methodRule.getMethodReference()), methodRule));
      return this;
    }

    public Builder addRules(ExternalArtProfileRule... rules) {
      for (ExternalArtProfileRule rule : rules) {
        addRule(rule);
      }
      return this;
    }

    public ExternalArtProfile build() {
      return new ExternalArtProfile(rules);
    }
  }
}
