// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.function.Consumer;

public class ArtProfileMethodRule extends ArtProfileRule {

  private final DexMethod method;
  private final ArtProfileMethodRuleInfo info;

  ArtProfileMethodRule(DexMethod method, ArtProfileMethodRuleInfo info) {
    this.method = method;
    this.info = info;
  }

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  @Override
  public void accept(
      Consumer<ArtProfileClassRule> classRuleConsumer,
      Consumer<ArtProfileMethodRule> methodRuleConsumer) {
    methodRuleConsumer.accept(this);
  }

  public DexMethod getMethod() {
    return method;
  }

  public MethodReference getMethodReference() {
    return method.asMethodReference();
  }

  public ArtProfileMethodRuleInfo getMethodRuleInfo() {
    return info;
  }

  @Override
  public boolean isMethodRule() {
    return true;
  }

  @Override
  public ArtProfileMethodRule asMethodRule() {
    return this;
  }

  @Override
  public ArtProfileMethodRule rewrittenWithLens(GraphLens lens) {
    return new ArtProfileMethodRule(lens.getRenamedMethodSignature(method), info);
  }

  @Override
  public ArtProfileRule rewrittenWithLens(DexItemFactory dexItemFactory, NamingLens lens) {
    return new ArtProfileMethodRule(lens.lookupMethod(method, dexItemFactory), info);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtProfileMethodRule that = (ArtProfileMethodRule) o;
    return method.equals(that.method) && info.equals(that.info);
  }

  @Override
  public int hashCode() {
    // A profile does not have two rules with the same reference but different flags, so no need to
    // include the flags in the hash.
    return method.hashCode();
  }

  @Override
  public String toString() {
    return info.toString() + method.toSmaliString();
  }

  public static class Builder implements ArtProfileMethodRuleBuilder {

    private final DexItemFactory dexItemFactory;

    private DexMethod method;
    private ArtProfileMethodRuleInfo methodRuleInfo = ArtProfileMethodRuleInfoImpl.empty();

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public ArtProfileMethodRuleBuilder setMethodReference(MethodReference methodReference) {
      this.method = MethodReferenceUtils.toDexMethod(methodReference, dexItemFactory);
      return this;
    }

    @Override
    public ArtProfileMethodRuleBuilder setMethodRuleInfo(
        Consumer<ArtProfileMethodRuleInfoBuilder> methodRuleInfoBuilderConsumer) {
      ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder =
          ArtProfileMethodRuleInfoImpl.builder();
      methodRuleInfoBuilderConsumer.accept(methodRuleInfoBuilder);
      methodRuleInfo = methodRuleInfoBuilder.build();
      return this;
    }

    public ArtProfileMethodRule build() {
      return new ArtProfileMethodRule(method, methodRuleInfo);
    }
  }
}
