// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.bytecodemetadata;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.fieldaccess.TrivialFieldAccessReprocessor;
import java.util.Collections;
import java.util.Set;

/**
 * A piece of information that can be attached to instructions in {@link
 * com.android.tools.r8.graph.CfCode} and {@link com.android.tools.r8.graph.DexCode}.
 */
public class BytecodeInstructionMetadata {

  /**
   * Set for field-get instructions that are only used to invoke one or more instance methods.
   *
   * <p>This is used to skip field-get instructions that will be pruned as a result of method
   * staticizing in the {@link TrivialFieldAccessReprocessor}. A common use case for this is Kotlin
   * companion fields, which will generally not be read after the methods of a given companion class
   * have been staticized.
   */
  private final Set<DexMethod> isReadForInvokeReceiver;

  /**
   * Set for instance and static field read instructions which are only used to write the same
   * field.
   *
   * <p>Used by {@link com.android.tools.r8.ir.analysis.fieldaccess.TrivialFieldAccessReprocessor}
   * to skip such instructions in the "is-field-read" analysis.
   */
  private final boolean isReadForWrite;

  private BytecodeInstructionMetadata(
      Set<DexMethod> isReadForInvokeReceiver, boolean isReadForWrite) {
    this.isReadForInvokeReceiver = isReadForInvokeReceiver;
    this.isReadForWrite = isReadForWrite;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BytecodeInstructionMetadata none() {
    return null;
  }

  public boolean isReadForInvokeReceiver() {
    return isReadForInvokeReceiver != null;
  }

  public Set<DexMethod> getReadForInvokeReceiver() {
    return isReadForInvokeReceiver;
  }

  public boolean isReadForWrite() {
    return isReadForWrite;
  }

  public static class Builder {

    private Set<DexMethod> isReadForInvokeReceiver = Collections.emptySet();
    private boolean isReadForWrite;

    private boolean isEmpty() {
      return isReadForInvokeReceiver.isEmpty() && !isReadForWrite;
    }

    public Builder setIsReadForInvokeReceiver(Set<DexMethod> isReadForInvokeReceiver) {
      assert isReadForInvokeReceiver != null;
      assert !isReadForInvokeReceiver.isEmpty();
      this.isReadForInvokeReceiver = isReadForInvokeReceiver;
      return this;
    }

    public Builder setIsReadForWrite() {
      isReadForWrite = true;
      return this;
    }

    public BytecodeInstructionMetadata build() {
      assert !isEmpty();
      return new BytecodeInstructionMetadata(isReadForInvokeReceiver, isReadForWrite);
    }
  }
}
