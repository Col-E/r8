// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class MachineRewritingFlags {

  public static Builder builder() {
    return new Builder();
  }

  MachineRewritingFlags(
      Map<DexMethod, DexMethod> staticRetarget,
      Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget,
      Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget) {
    this.staticRetarget = staticRetarget;
    this.nonEmulatedVirtualRetarget = nonEmulatedVirtualRetarget;
    this.emulatedVirtualRetarget = emulatedVirtualRetarget;
  }

  // Static methods to retarget, duplicated to library boundaries.
  private final Map<DexMethod, DexMethod> staticRetarget;

  // Virtual methods to retarget, which are guaranteed not to require emulated dispatch.
  // A method does not require emulated dispatch if two conditions are met:
  // (1) the method does not override any other library method;
  // (2) the method is final or installed in a final class.
  // Any invoke resolving into the method will be rewritten into an invoke-static to the desugared
  // code.
  private final Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget;

  // Virtual methods to retarget through emulated dispatch.
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget;

  public Map<DexMethod, DexMethod> getStaticRetarget() {
    return staticRetarget;
  }

  public Map<DexMethod, DexMethod> getNonEmulatedVirtualRetarget() {
    return nonEmulatedVirtualRetarget;
  }

  public Map<DexMethod, EmulatedDispatchMethodDescriptor> getEmulatedVirtualRetarget() {
    return emulatedVirtualRetarget;
  }

  public static class Builder {

    Builder() {}

    private final ImmutableMap.Builder<DexMethod, DexMethod> staticRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod> nonEmulatedVirtualRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, EmulatedDispatchMethodDescriptor>
        emulatedVirtualRetarget = ImmutableMap.builder();

    public void putStaticRetarget(DexMethod src, DexMethod dest) {
      staticRetarget.put(src, dest);
    }

    public void putNonEmulatedVirtualRetarget(DexMethod src, DexMethod dest) {
      nonEmulatedVirtualRetarget.put(src, dest);
    }

    public void putEmulatedVirtualRetarget(DexMethod src, EmulatedDispatchMethodDescriptor dest) {
      emulatedVirtualRetarget.put(src, dest);
    }

    public MachineRewritingFlags build() {
      return new MachineRewritingFlags(
          staticRetarget.build(),
          nonEmulatedVirtualRetarget.build(),
          emulatedVirtualRetarget.build());
    }
  }
}
