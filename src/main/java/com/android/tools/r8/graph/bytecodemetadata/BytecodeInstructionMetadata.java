// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.bytecodemetadata;

/**
 * A piece of information that can be attached to instructions in {@link
 * com.android.tools.r8.graph.CfCode} and {@link com.android.tools.r8.graph.DexCode}.
 */
public class BytecodeInstructionMetadata {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    public BytecodeInstructionMetadata build() {
      return new BytecodeInstructionMetadata();
    }
  }
}
