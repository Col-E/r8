// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.profile;

import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.startup.StartupMethodBuilder;
import com.android.tools.r8.startup.profile.ExternalStartupClass.Builder;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.function.Function;

public class ExternalStartupMethod extends ExternalStartupItem {

  private final MethodReference methodReference;

  ExternalStartupMethod(MethodReference methodReference) {
    this.methodReference = methodReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public <T> T apply(
      Function<ExternalStartupClass, T> classFunction,
      Function<ExternalStartupMethod, T> methodFunction,
      Function<ExternalSyntheticStartupMethod, T> syntheticMethodFunction) {
    return methodFunction.apply(this);
  }

  public MethodReference getReference() {
    return methodReference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExternalStartupMethod that = (ExternalStartupMethod) o;
    return methodReference.equals(that.methodReference);
  }

  @Override
  public int hashCode() {
    return methodReference.hashCode();
  }

  @Override
  public String toString() {
    return MethodReferenceUtils.toSourceString(methodReference);
  }

  public static class Builder implements StartupMethodBuilder {

    private MethodReference methodReference;

    @Override
    public Builder setMethodReference(MethodReference methodReference) {
      this.methodReference = methodReference;
      return this;
    }

    public ExternalStartupMethod build() {
      return new ExternalStartupMethod(methodReference);
    }
  }
}
