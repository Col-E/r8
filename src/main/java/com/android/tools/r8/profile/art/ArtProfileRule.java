// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.io.OutputStreamWriter;

public abstract class ArtProfileRule implements Comparable<ArtProfileRule> {

  public abstract <E1 extends Exception, E2 extends Exception> void accept(
      ThrowingConsumer<ArtProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<ArtProfileMethodRule, E2> methodRuleConsumer)
      throws E1, E2;

  @Override
  public final int compareTo(ArtProfileRule rule) {
    return getReference().compareTo(rule.getReference());
  }

  public abstract DexReference getReference();

  public boolean isClassRule() {
    return false;
  }

  public ArtProfileClassRule asClassRule() {
    return null;
  }

  public boolean isMethodRule() {
    return false;
  }

  public ArtProfileMethodRule asMethodRule() {
    return null;
  }

  public abstract void writeHumanReadableRuleString(OutputStreamWriter writer) throws IOException;

  public abstract static class Builder {

    public boolean isClassRuleBuilder() {
      return false;
    }

    ArtProfileClassRule.Builder asClassRuleBuilder() {
      return null;
    }

    public boolean isMethodRuleBuilder() {
      return false;
    }

    ArtProfileMethodRule.Builder asMethodRuleBuilder() {
      return null;
    }

    public abstract ArtProfileRule build();
  }
}
