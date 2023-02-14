// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.Consumer;

public class ArtProfileMethodRule extends ArtProfileRule {

  private final DexMethod method;
  private final ArtProfileMethodRuleInfoImpl info;

  ArtProfileMethodRule(DexMethod method, ArtProfileMethodRuleInfoImpl info) {
    this.method = method;
    this.info = info;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void accept(
      ThrowingConsumer<ArtProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<ArtProfileMethodRule, E2> methodRuleConsumer)
      throws E2 {
    methodRuleConsumer.accept(this);
  }

  public DexMethod getMethod() {
    return method;
  }

  public MethodReference getMethodReference() {
    return method.asMethodReference();
  }

  public ArtProfileMethodRuleInfoImpl getMethodRuleInfo() {
    return info;
  }

  @Override
  public DexMethod getReference() {
    return getMethod();
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
  public void writeHumanReadableRuleString(OutputStreamWriter writer) throws IOException {
    info.writeHumanReadableFlags(writer);
    writer.write(method.toSmaliString());
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

  public static class Builder extends ArtProfileRule.Builder
      implements ArtProfileMethodRuleBuilder {

    private final DexItemFactory dexItemFactory;

    private DexMethod method;
    private ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder =
        ArtProfileMethodRuleInfoImpl.builder();

    Builder() {
      this(null);
    }

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    ArtProfileMethodRuleInfoImpl.Builder getMethodRuleInfoBuilder() {
      return methodRuleInfoBuilder;
    }

    @Override
    public boolean isMethodRuleBuilder() {
      return true;
    }

    @Override
    Builder asMethodRuleBuilder() {
      return this;
    }

    @Override
    public Builder setMethodReference(MethodReference methodReference) {
      assert dexItemFactory != null;
      return setMethod(MethodReferenceUtils.toDexMethod(methodReference, dexItemFactory));
    }

    public Builder setMethod(DexMethod method) {
      this.method = method;
      return this;
    }

    @Override
    public Builder setMethodRuleInfo(
        Consumer<ArtProfileMethodRuleInfoBuilder> methodRuleInfoBuilderConsumer) {
      methodRuleInfoBuilder.clear();
      return acceptMethodRuleInfoBuilder(methodRuleInfoBuilderConsumer);
    }

    public Builder acceptMethodRuleInfoBuilder(
        Consumer<? super ArtProfileMethodRuleInfoImpl.Builder> methodRuleInfoBuilderConsumer) {
      methodRuleInfoBuilderConsumer.accept(methodRuleInfoBuilder);
      return this;
    }

    @Override
    public ArtProfileMethodRule build() {
      return new ArtProfileMethodRule(method, methodRuleInfoBuilder.build());
    }
  }
}
