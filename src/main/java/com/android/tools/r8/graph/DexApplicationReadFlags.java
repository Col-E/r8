// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

// Flags set based on the application when it was read.
// Note that in r8, once classes are pruned, the flags may not reflect the application anymore.
public class DexApplicationReadFlags {

  public static class Builder {

    private boolean hasReadProgramClassFromDex;
    private boolean hasReadProgramClassFromCf;
    private final ImmutableSet.Builder<DexType> recordWitnessesBuilder = ImmutableSet.builder();
    private final ImmutableSet.Builder<DexType> varHandleWitnessesBuilder = ImmutableSet.builder();
    private final ImmutableSet.Builder<DexType> methodHandlesLookupWitnessesBuilder =
        ImmutableSet.builder();

    private Builder() {}

    public Builder setHasReadProgramClassFromDex(boolean value) {
      hasReadProgramClassFromDex = value;
      return this;
    }

    public Builder setHasReadProgramClassFromCf(boolean value) {
      hasReadProgramClassFromCf = value;
      return this;
    }

    public Builder addRecordWitness(DexType witness) {
      synchronized (recordWitnessesBuilder) {
        recordWitnessesBuilder.add(witness);
      }
      return this;
    }

    public Builder addVarHandleWitness(DexType witness) {
      synchronized (varHandleWitnessesBuilder) {
        varHandleWitnessesBuilder.add(witness);
      }
      return this;
    }

    public Builder addMethodHandlesLookupWitness(DexType witness) {
      synchronized (methodHandlesLookupWitnessesBuilder) {
        methodHandlesLookupWitnessesBuilder.add(witness);
      }
      return this;
    }

    public DexApplicationReadFlags build() {
      return new DexApplicationReadFlags(
          hasReadProgramClassFromDex,
          hasReadProgramClassFromCf,
          recordWitnessesBuilder.build(),
          varHandleWitnessesBuilder.build(),
          methodHandlesLookupWitnessesBuilder.build());
    }
  }

  private final boolean hasReadProgramClassFromDex;
  private final boolean hasReadProgramClassFromCf;
  private final Set<DexType> recordWitnesses;
  private final Set<DexType> varHandleWitnesses;
  private final Set<DexType> methodHandlesLookupWitnesses;

  public static Builder builder() {
    return new Builder();
  }

  private DexApplicationReadFlags(
      boolean hasReadProgramClassFromDex,
      boolean hasReadProgramClassFromCf,
      Set<DexType> recordWitnesses,
      Set<DexType> varHandleWitnesses,
      Set<DexType> methodHandlesLookupWitnesses) {
    this.hasReadProgramClassFromDex = hasReadProgramClassFromDex;
    this.hasReadProgramClassFromCf = hasReadProgramClassFromCf;
    this.recordWitnesses = recordWitnesses;
    this.varHandleWitnesses = varHandleWitnesses;
    this.methodHandlesLookupWitnesses = methodHandlesLookupWitnesses;
  }

  public boolean hasReadProgramClassFromCf() {
    return hasReadProgramClassFromCf;
  }

  public boolean hasReadProgramClassFromDex() {
    return hasReadProgramClassFromDex;
  }

  public boolean hasReadRecordReferenceFromProgramClass() {
    return !recordWitnesses.isEmpty();
  }

  public Set<DexType> getRecordWitnesses() {
    return recordWitnesses;
  }

  public boolean hasReadMethodHandlesLookupReferenceFromProgramClass() {
    return !methodHandlesLookupWitnesses.isEmpty();
  }

  public Set<DexType> getMethodHandlesLookupWitnesses() {
    return methodHandlesLookupWitnesses;
  }

  public boolean hasReadVarHandleReferenceFromProgramClass() {
    return !varHandleWitnesses.isEmpty();
  }

  public Set<DexType> getVarHandleWitnesses() {
    return varHandleWitnesses;
  }
}
