// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.bytecodemetadata;

/**
 * A piece of information that can be attached to instructions in {@link
 * com.android.tools.r8.graph.CfCode} and {@link com.android.tools.r8.graph.DexCode}.
 */
public class BytecodeInstructionMetadata {

  /**
   * Set for instance and static field read instructions which are only used to write the same
   * field.
   *
   * <p>Used by {@link com.android.tools.r8.ir.analysis.fieldaccess.TrivialFieldAccessReprocessor}
   * to skip such instructions in the "is-field-read" analysis.
   */
  private final boolean isReadForWrite;

  BytecodeInstructionMetadata(boolean isReadForWrite) {
    this.isReadForWrite = isReadForWrite;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BytecodeInstructionMetadata none() {
    return null;
  }

  public boolean isReadForWrite() {
    return isReadForWrite;
  }

  public static class Builder {

    private boolean isReadForWrite;

    private boolean isEmpty() {
      return !isReadForWrite;
    }

    public Builder setIsReadForWrite() {
      isReadForWrite = true;
      return this;
    }

    public BytecodeInstructionMetadata build() {
      assert !isEmpty();
      return new BytecodeInstructionMetadata(isReadForWrite);
    }
  }
}
