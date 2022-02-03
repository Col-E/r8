// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class MachineRewritingFlags {

  public static Builder builder() {
    return new Builder();
  }

  MachineRewritingFlags(
      Map<DexType, DexType> rewriteType,
      Map<DexType, DexType> rewriteDerivedTypeOnly,
      Map<DexMethod, DexMethod> staticRetarget,
      Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget,
      Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget,
      Map<DexType, EmulatedInterfaceDescriptor> emulatedInterfaces,
      Map<DexType, List<DexMethod>> wrappers,
      Map<DexType, DexType> legacyBackport,
      Set<DexType> dontRetarget,
      Map<DexType, Pair<DexType, DexString>> customConversions) {
    this.rewriteType = rewriteType;
    this.rewriteDerivedTypeOnly = rewriteDerivedTypeOnly;
    this.staticRetarget = staticRetarget;
    this.nonEmulatedVirtualRetarget = nonEmulatedVirtualRetarget;
    this.emulatedVirtualRetarget = emulatedVirtualRetarget;
    this.emulatedInterfaces = emulatedInterfaces;
    this.wrappers = wrappers;
    this.legacyBackport = legacyBackport;
    this.dontRetarget = dontRetarget;
    this.customConversions = customConversions;
  }

  // Rewrites all the references to the keys as well as synthetic types derived from any key.
  private final Map<DexType, DexType> rewriteType;
  // Rewrites only synthetic types derived from any key.
  private final Map<DexType, DexType> rewriteDerivedTypeOnly;

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

  // Emulated interface descriptors.
  private final Map<DexType, EmulatedInterfaceDescriptor> emulatedInterfaces;

  // Wrappers and the list of methods they implement.
  private final Map<DexType, List<DexMethod>> wrappers;

  private final Map<DexType, DexType> legacyBackport;
  private final Set<DexType> dontRetarget;
  private final Map<DexType, Pair<DexType, DexString>> customConversions;

  public Map<DexType, DexType> getRewriteType() {
    return rewriteType;
  }

  public Map<DexType, DexType> getRewriteDerivedTypeOnly() {
    return rewriteDerivedTypeOnly;
  }

  public Map<DexMethod, DexMethod> getStaticRetarget() {
    return staticRetarget;
  }

  public Map<DexMethod, DexMethod> getNonEmulatedVirtualRetarget() {
    return nonEmulatedVirtualRetarget;
  }

  public Map<DexMethod, EmulatedDispatchMethodDescriptor> getEmulatedVirtualRetarget() {
    return emulatedVirtualRetarget;
  }

  public void forEachRetargetHolder(Consumer<DexType> consumer) {
    staticRetarget.keySet().forEach(m -> consumer.accept(m.getHolderType()));
    nonEmulatedVirtualRetarget.keySet().forEach(m -> consumer.accept(m.getHolderType()));
    emulatedVirtualRetarget.keySet().forEach(m -> consumer.accept(m.getHolderType()));
  }

  public Map<DexType, EmulatedInterfaceDescriptor> getEmulatedInterfaces() {
    return emulatedInterfaces;
  }

  public Map<DexType, List<DexMethod>> getWrappers() {
    return wrappers;
  }

  public Map<DexType, DexType> getLegacyBackport() {
    return legacyBackport;
  }

  public Set<DexType> getDontRetarget() {
    return dontRetarget;
  }

  public boolean isCustomConversionRewrittenType(DexType type) {
    return Iterables.any(customConversions.values(), pair -> pair.getFirst() == type);
  }

  public Map<DexType, Pair<DexType, DexString>> getCustomConversions() {
    return customConversions;
  }

  public boolean hasRetargeting() {
    return !staticRetarget.isEmpty()
        || !nonEmulatedVirtualRetarget.isEmpty()
        || !emulatedVirtualRetarget.isEmpty();
  }

  public boolean isEmulatedInterfaceRewrittenType(DexType type) {
    return Iterables.any(
        emulatedInterfaces.values(), descriptor -> descriptor.getRewrittenType() == type);
  }

  public boolean hasEmulatedInterfaces() {
    return !emulatedInterfaces.isEmpty();
  }

  EmulatedDispatchMethodDescriptor getEmulatedInterfaceEmulatedDispatchMethodDescriptor(
      DexMethod method) {
    if (!emulatedInterfaces.containsKey(method.getHolderType())) {
      return null;
    }
    return emulatedInterfaces.get(method.getHolderType()).getEmulatedMethods().get(method);
  }

  public static class Builder {

    Builder() {}

    private final Map<DexType, DexType> rewriteType = new IdentityHashMap<>();
    private final Map<DexType, DexType> rewriteDerivedTypeOnly = new IdentityHashMap<>();
    private final ImmutableMap.Builder<DexMethod, DexMethod> staticRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod> nonEmulatedVirtualRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, EmulatedDispatchMethodDescriptor>
        emulatedVirtualRetarget = ImmutableMap.builder();
    private final ImmutableMap.Builder<DexType, EmulatedInterfaceDescriptor> emulatedInterfaces =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexType, List<DexMethod>> wrappers = ImmutableMap.builder();
    private final ImmutableMap.Builder<DexType, DexType> legacyBackport = ImmutableMap.builder();
    private final ImmutableSet.Builder<DexType> dontRetarget = ImmutableSet.builder();
    private final ImmutableMap.Builder<DexType, Pair<DexType, DexString>> customConversions =
        ImmutableMap.builder();

    public void rewriteType(DexType src, DexType target) {
      assert src != null;
      assert target != null;
      assert src != target;
      rewriteType.put(src, target);
    }

    public void rewriteDerivedTypeOnly(DexType src, DexType target) {
      rewriteDerivedTypeOnly.put(src, target);
    }

    public void putStaticRetarget(DexMethod src, DexMethod dest) {
      staticRetarget.put(src, dest);
    }

    public void putNonEmulatedVirtualRetarget(DexMethod src, DexMethod dest) {
      nonEmulatedVirtualRetarget.put(src, dest);
    }

    public void putEmulatedInterface(DexType src, EmulatedInterfaceDescriptor descriptor) {
      emulatedInterfaces.put(src, descriptor);
    }

    public void putEmulatedVirtualRetarget(DexMethod src, EmulatedDispatchMethodDescriptor dest) {
      emulatedVirtualRetarget.put(src, dest);
    }

    public void addWrapper(DexType wrapperConversion, List<DexMethod> methods) {
      wrappers.put(wrapperConversion, ImmutableList.copyOf(methods));
    }

    public void putLegacyBackport(DexType src, DexType target) {
      legacyBackport.put(src, target);
    }

    public void addDontRetarget(DexType type) {
      dontRetarget.add(type);
    }

    public void putCustomConversion(DexType src, DexType conversionType, DexString conversionName) {
      customConversions.put(src, new Pair<>(conversionType, conversionName));
    }

    public MachineRewritingFlags build() {
      return new MachineRewritingFlags(
          rewriteType,
          rewriteDerivedTypeOnly,
          staticRetarget.build(),
          nonEmulatedVirtualRetarget.build(),
          emulatedVirtualRetarget.build(),
          emulatedInterfaces.build(),
          wrappers.build(),
          legacyBackport.build(),
          dontRetarget.build(),
          customConversions.build());
    }
  }
}
