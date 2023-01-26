// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class ArtProfileClassRule extends ArtProfileRule {

  private final DexType type;

  ArtProfileClassRule(DexType type) {
    this.type = type;
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
      throws E1 {
    classRuleConsumer.accept(this);
  }

  public ClassReference getClassReference() {
    return Reference.classFromDescriptor(type.toDescriptorString());
  }

  public ArtProfileClassRuleInfo getClassRuleInfo() {
    return ArtProfileClassRuleInfoImpl.empty();
  }

  @Override
  public DexReference getReference() {
    return getType();
  }

  public DexType getType() {
    return type;
  }

  @Override
  public boolean isClassRule() {
    return true;
  }

  @Override
  public ArtProfileClassRule asClassRule() {
    return this;
  }

  @Override
  public void writeHumanReadableRuleString(OutputStreamWriter writer) throws IOException {
    writer.write(type.toDescriptorString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtProfileClassRule that = (ArtProfileClassRule) o;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return type.toDescriptorString();
  }

  public static class Builder extends ArtProfileRule.Builder implements ArtProfileClassRuleBuilder {

    private final DexItemFactory dexItemFactory;
    private DexType type;

    Builder() {
      this(null);
    }

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public boolean isClassRuleBuilder() {
      return true;
    }

    @Override
    Builder asClassRuleBuilder() {
      return this;
    }

    @Override
    public Builder setClassReference(ClassReference classReference) {
      assert dexItemFactory != null;
      return setType(dexItemFactory.createType(classReference.getDescriptor()));
    }

    public Builder setType(DexType type) {
      this.type = type;
      return this;
    }

    @Override
    public ArtProfileClassRule build() {
      return new ArtProfileClassRule(type);
    }
  }
}
