// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.ArrayList;
import java.util.List;

public abstract class KeepPreconditions {

  private static class KeepPreconditionsAlways extends KeepPreconditions {

    private static KeepPreconditionsAlways INSTANCE = null;

    public static KeepPreconditionsAlways getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepPreconditionsAlways();
      }
      return INSTANCE;
    }
  }

  private static class KeepPreconditionsSome extends KeepPreconditions {

    private final List<KeepCondition> preconditions;

    private KeepPreconditionsSome(List<KeepCondition> preconditions) {
      this.preconditions = preconditions;
    }
  }

  public static class Builder {

    private List<KeepCondition> preconditions = new ArrayList<>();

    private Builder() {}

    public Builder addCondition(KeepCondition condition) {
      preconditions.add(condition);
      return this;
    }

    public KeepPreconditions build() {
      return new KeepPreconditionsSome(preconditions);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static KeepPreconditions always() {
    return KeepPreconditionsAlways.getInstance();
  }
}
