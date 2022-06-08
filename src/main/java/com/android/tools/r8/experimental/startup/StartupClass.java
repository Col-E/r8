// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

public class StartupClass<T> {

  private static final int FLAG_SYNTHETIC = 1;

  private final int flags;
  private final T reference;

  public StartupClass(int flags, T reference) {
    this.flags = flags;
    this.reference = reference;
  }

  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  public int getFlags() {
    return flags;
  }

  public T getReference() {
    return reference;
  }

  public boolean isSynthetic() {
    return (flags & FLAG_SYNTHETIC) != 0;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    StartupClass<?> startupClass = (StartupClass<?>) obj;
    return flags == startupClass.flags && reference.equals(startupClass.reference);
  }

  @Override
  public int hashCode() {
    assert flags <= 1;
    return (reference.hashCode() << 1) | flags;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (isSynthetic()) {
      builder.append('S');
    }
    builder.append(reference);
    return builder.toString();
  }

  public static class Builder<T> {

    private int flags;
    private T reference;

    public Builder<T> setFlags(int flags) {
      this.flags = flags;
      return this;
    }

    public Builder<T> setReference(T reference) {
      this.reference = reference;
      return this;
    }

    public Builder<T> setSynthetic() {
      this.flags |= FLAG_SYNTHETIC;
      return this;
    }

    public StartupClass<T> build() {
      return new StartupClass<>(flags, reference);
    }
  }
}
