// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.profile.AbstractProfileMethodRule;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.ThrowingFunction;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.Consumer;

public class ArtProfileMethodRule extends ArtProfileRule implements AbstractProfileMethodRule {

  private final DexMethod method;
  private final ArtProfileMethodRuleInfoImpl info;

  ArtProfileMethodRule(DexMethod method, ArtProfileMethodRuleInfoImpl info) {
    assert info.getFlags() != 0;
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
      ThrowingConsumer<? super ArtProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<? super ArtProfileMethodRule, E2> methodRuleConsumer)
      throws E2 {
    methodRuleConsumer.accept(this);
  }

  @Override
  public <T, E1 extends Exception, E2 extends Exception> T apply(
      ThrowingFunction<ArtProfileClassRule, T, E1> classRuleFunction,
      ThrowingFunction<ArtProfileMethodRule, T, E2> methodRuleFunction)
      throws E2 {
    return methodRuleFunction.apply(this);
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
  public void writeHumanReadableRuleString(OutputStreamWriter writer) throws IOException {
    info.writeHumanReadableFlags(writer);
    writer.write(method.toSmaliString());
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
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
      implements ArtProfileMethodRuleBuilder,
          AbstractProfileMethodRule.Builder<ArtProfileMethodRule, Builder> {

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
    public boolean isGreaterThanOrEqualTo(Builder builder) {
      return methodRuleInfoBuilder.isGreaterThanOrEqualTo(builder.methodRuleInfoBuilder);
    }

    @Override
    Builder asMethodRuleBuilder() {
      return this;
    }

    @Override
    public Builder join(Builder builder) {
      methodRuleInfoBuilder.joinFlags(builder);
      return this;
    }

    @Override
    public Builder join(Builder builder, Runnable onChangedHandler) {
      int oldFlags = methodRuleInfoBuilder.getFlags();
      join(builder);
      if (methodRuleInfoBuilder.getFlags() != oldFlags) {
        onChangedHandler.run();
      }
      return this;
    }

    @Override
    public Builder join(ArtProfileMethodRule methodRule) {
      methodRuleInfoBuilder.joinFlags(methodRule.getMethodRuleInfo());
      return this;
    }

    @Override
    public Builder setIsStartup() {
      methodRuleInfoBuilder.setIsStartup();
      return this;
    }

    @Override
    public Builder setMethodReference(MethodReference methodReference) {
      assert dexItemFactory != null;
      return setMethod(MethodReferenceUtils.toDexMethod(methodReference, dexItemFactory));
    }

    @Override
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
