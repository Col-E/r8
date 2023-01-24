// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.model;

import com.android.tools.r8.references.ClassReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Represents a class rule from an ART baseline profile, backed by {@link ClassReference}. Class
 * rules currently contain no other information than the class reference.
 */
public class ExternalArtProfileClassRule extends ExternalArtProfileRule {

  private final ClassReference classReference;

  ExternalArtProfileClassRule(ClassReference classReference) {
    assert classReference != null;
    this.classReference = classReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void accept(
      Consumer<ExternalArtProfileClassRule> classRuleConsumer,
      Consumer<ExternalArtProfileMethodRule> methodRuleConsumer) {
    classRuleConsumer.accept(this);
  }

  public ClassReference getClassReference() {
    return classReference;
  }

  @Override
  public boolean test(
      Predicate<ExternalArtProfileClassRule> classRuleConsumer,
      Predicate<ExternalArtProfileMethodRule> methodRuleConsumer) {
    return classRuleConsumer.test(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ExternalArtProfileClassRule methodRule = (ExternalArtProfileClassRule) obj;
    return classReference.equals(methodRule.classReference);
  }

  @Override
  public int hashCode() {
    return classReference.hashCode();
  }

  @Override
  public String toString() {
    return classReference.getDescriptor();
  }

  public static class Builder {

    private ClassReference classReference;

    public Builder setClassReference(ClassReference classReference) {
      this.classReference = classReference;
      return this;
    }

    public ExternalArtProfileClassRule build() {
      return new ExternalArtProfileClassRule(classReference);
    }
  }
}
