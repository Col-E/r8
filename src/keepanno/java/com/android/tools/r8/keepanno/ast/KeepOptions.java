// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class KeepOptions {
  private static final KeepOptions ALLOW_NONE_INSTANCE = new KeepOptions(ImmutableSet.of());

  public boolean isKeepAll() {
    return allowedOptions.isEmpty();
  }

  public enum KeepOption {
    SHRINKING,
    OPTIMIZING,
    OBFUSCATING,
    ACCESS_MODIFYING,
  }

  public static KeepOptions keepAll() {
    return ALLOW_NONE_INSTANCE;
  }

  public static Builder allowBuilder() {
    return new Builder(true);
  }

  public static Builder disallowBuilder() {
    return new Builder(false);
  }

  public static KeepOptions allow(KeepOption... options) {
    return allowBuilder().addAll(options).build();
  }

  public static KeepOptions disallow(KeepOption... options) {
    return disallowBuilder().addAll(options).build();
  }

  public static class Builder {
    public final boolean allowIfSet;
    public Set<KeepOption> options = new HashSet<>();

    private Builder(boolean allowIfSet) {
      this.allowIfSet = allowIfSet;
    }

    public Builder add(KeepOption option) {
      options.add(option);
      return this;
    }

    public Builder addAll(KeepOption... options) {
      return addAll(Arrays.asList(options));
    }

    public Builder addAll(Collection<KeepOption> options) {
      this.options.addAll(options);
      return this;
    }

    public KeepOptions build() {
      // Fast path check for the two variants of "keep all".
      if (options.isEmpty()) {
        if (allowIfSet) {
          return keepAll();
        }
        throw new KeepEdgeException("Invalid keep options that disallow nothing.");
      }
      if (options.size() == KeepOption.values().length) {
        if (!allowIfSet) {
          return keepAll();
        }
        throw new KeepEdgeException("Invalid keep options that allow everything.");
      }
      // The normalized options is the "allow variant", if not of that form invert it on build.
      if (allowIfSet) {
        return new KeepOptions(ImmutableSet.copyOf(options));
      }
      ImmutableSet.Builder<KeepOption> invertedOptions = ImmutableSet.builder();
      for (KeepOption option : KeepOption.values()) {
        if (!options.contains(option)) {
          invertedOptions.add(option);
        }
      }
      return new KeepOptions(invertedOptions.build());
    }
  }

  private final ImmutableSet<KeepOption> allowedOptions;

  private KeepOptions(ImmutableSet<KeepOption> options) {
    this.allowedOptions = options;
  }

  public boolean isAllowed(KeepOption option) {
    return allowedOptions.contains(option);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepOptions that = (KeepOptions) o;
    return allowedOptions.equals(that.allowedOptions);
  }

  @Override
  public int hashCode() {
    return allowedOptions.hashCode();
  }

  @Override
  public String toString() {
    return "KeepOptions{"
        + allowedOptions.stream().map(Objects::toString).collect(Collectors.joining(", "))
        + '}';
  }
}
