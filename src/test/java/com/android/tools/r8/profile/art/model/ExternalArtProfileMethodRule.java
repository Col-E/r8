// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.model;

import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

/** Represents a method rule from an ART baseline profile, backed by {@link MethodReference}. */
public class ExternalArtProfileMethodRule extends ExternalArtProfileRule {

  private final MethodReference methodReference;
  private final ArtProfileMethodRuleInfo methodRuleInfo;

  ExternalArtProfileMethodRule(
      MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
    assert methodReference != null;
    assert methodRuleInfo != null;
    this.methodReference = methodReference;
    this.methodRuleInfo = methodRuleInfo;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void accept(
      Consumer<ExternalArtProfileClassRule> classRuleConsumer,
      Consumer<ExternalArtProfileMethodRule> methodRuleConsumer) {
    methodRuleConsumer.accept(this);
  }

  public MethodReference getMethodReference() {
    return methodReference;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ExternalArtProfileMethodRule methodRule = (ExternalArtProfileMethodRule) obj;
    return methodReference.equals(methodRule.methodReference);
  }

  @Override
  public int hashCode() {
    return methodReference.hashCode();
  }

  public static class Builder {

    private MethodReference methodReference;
    private ArtProfileMethodRuleInfo methodRuleInfo = ArtProfileMethodRuleInfoImpl.empty();

    public Builder setMethodReference(MethodReference methodReference) {
      this.methodReference = methodReference;
      return this;
    }

    public Builder setMethodRuleInfo(ArtProfileMethodRuleInfo methodRuleInfo) {
      this.methodRuleInfo = methodRuleInfo;
      return this;
    }

    public ExternalArtProfileMethodRule build() {
      return new ExternalArtProfileMethodRule(methodReference, methodRuleInfo);
    }
  }
}
