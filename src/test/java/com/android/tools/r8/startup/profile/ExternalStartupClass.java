// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.profile;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.startup.StartupClassBuilder;
import java.util.function.Function;

public class ExternalStartupClass extends ExternalStartupItem {

  private final ClassReference classReference;

  ExternalStartupClass(ClassReference classReference) {
    this.classReference = classReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public <T> T apply(
      Function<ExternalStartupClass, T> classFunction,
      Function<ExternalStartupMethod, T> methodFunction,
      Function<ExternalSyntheticStartupMethod, T> syntheticMethodFunction) {
    return classFunction.apply(this);
  }

  public ClassReference getReference() {
    return classReference;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExternalStartupClass that = (ExternalStartupClass) o;
    return classReference.equals(that.classReference);
  }

  @Override
  public int hashCode() {
    return classReference.hashCode();
  }

  @Override
  public String toString() {
    return classReference.getTypeName();
  }

  public static class Builder implements StartupClassBuilder {

    private ClassReference classReference;

    @Override
    public Builder setClassReference(ClassReference classReference) {
      this.classReference = classReference;
      return this;
    }

    public ExternalStartupClass build() {
      return new ExternalStartupClass(classReference);
    }
  }
}
