// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.profile.AbstractProfileRule;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.util.function.Function;

public abstract class StartupProfileRule
    implements AbstractProfileRule, Comparable<StartupProfileRule> {

  public abstract <E1 extends Exception, E2 extends Exception> void accept(
      ThrowingConsumer<StartupProfileClassRule, E1> classConsumer,
      ThrowingConsumer<StartupProfileMethodRule, E2> methodConsumer)
      throws E1, E2;

  public abstract <T> T apply(
      Function<StartupProfileClassRule, T> classFunction,
      Function<StartupProfileMethodRule, T> methodFunction);

  @Override
  public final int compareTo(StartupProfileRule rule) {
    return getReference().compareTo(rule.getReference());
  }

  public abstract DexReference getReference();

  public abstract void write(Appendable appendable) throws IOException;
}
