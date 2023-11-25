// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;

/**
 * Result of a method lookup in a GraphLens.
 *
 * <p>This provides the new target and invoke type to use, along with a description of the prototype
 * changes that have been made to the target method and the corresponding required changes to the
 * invoke arguments.
 */
public class MethodLookupResult extends MemberLookupResult<DexMethod> {

  private final InvokeType type;
  private final RewrittenPrototypeDescription prototypeChanges;

  public MethodLookupResult(
      DexMethod reference,
      DexMethod reboundReference,
      InvokeType type,
      RewrittenPrototypeDescription prototypeChanges) {
    super(reference, reboundReference);
    this.type = type;
    this.prototypeChanges = prototypeChanges;
  }

  public static Builder builder(GraphLens lens) {
    return new Builder(lens);
  }

  public InvokeType getType() {
    return type;
  }

  @SuppressWarnings("UnusedVariable")
  public RewrittenPrototypeDescription getPrototypeChanges() {
    return prototypeChanges;
  }

  public static class Builder extends MemberLookupResult.Builder<DexMethod, Builder> {

    @SuppressWarnings("UnusedVariable")
    private final GraphLens lens;

    private RewrittenPrototypeDescription prototypeChanges = RewrittenPrototypeDescription.none();
    private InvokeType type;

    private Builder(GraphLens lens) {
      this.lens = lens;
    }

    public Builder setPrototypeChanges(RewrittenPrototypeDescription prototypeChanges) {
      this.prototypeChanges = prototypeChanges;
      return this;
    }

    public Builder setType(InvokeType type) {
      this.type = type;
      return this;
    }

    public MethodLookupResult build() {
      assert reference != null;
      // TODO(b/168282032): All non-identity graph lenses should set the rebound reference.
      return new MethodLookupResult(reference, reboundReference, type, prototypeChanges);
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
