// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.profile;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.startup.SyntheticStartupMethodBuilder;
import java.util.function.Function;

public class ExternalSyntheticStartupMethod extends ExternalStartupItem {

  private final ClassReference syntheticContextReference;

  ExternalSyntheticStartupMethod(ClassReference syntheticContextReference) {
    this.syntheticContextReference = syntheticContextReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public <T> T apply(
      Function<ExternalStartupClass, T> classFunction,
      Function<ExternalStartupMethod, T> methodFunction,
      Function<ExternalSyntheticStartupMethod, T> syntheticMethodFunction) {
    return syntheticMethodFunction.apply(this);
  }

  public ClassReference getSyntheticContextReference() {
    return syntheticContextReference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExternalSyntheticStartupMethod that = (ExternalSyntheticStartupMethod) o;
    return syntheticContextReference.equals(that.syntheticContextReference);
  }

  @Override
  public int hashCode() {
    return syntheticContextReference.hashCode();
  }

  @Override
  public String toString() {
    return "S(" + syntheticContextReference.getTypeName() + ")";
  }

  public static class Builder implements SyntheticStartupMethodBuilder {

    private ClassReference syntheticContextReference;

    @Override
    public Builder setSyntheticContextReference(ClassReference syntheticContextReference) {
      this.syntheticContextReference = syntheticContextReference;
      return this;
    }

    public ExternalSyntheticStartupMethod build() {
      return new ExternalSyntheticStartupMethod(syntheticContextReference);
    }
  }
}
