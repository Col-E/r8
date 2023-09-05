// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class MachineRewritingFlags {

  public static Builder builder() {
    return new Builder();
  }

  MachineRewritingFlags(
      Map<DexType, DexType> rewriteType,
      Set<DexType> maintainType,
      Map<DexType, DexType> rewriteDerivedTypeOnly,
      Map<DexField, DexField> staticFieldRetarget,
      Map<DexMethod, DexMethod> covariantRetarget,
      Map<DexMethod, DexMethod> staticRetarget,
      Map<DexMethod, DexMethod> nonEmulatedVirtualRetarget,
      Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedVirtualRetarget,
      Map<DexMethod, DexMethod> emulatedVirtualRetargetThroughEmulatedInterface,
      Map<DexMethod, DexMethod[]> apiGenericTypesConversion,
      Map<DexType, EmulatedInterfaceDescriptor> emulatedInterfaces,
      LinkedHashMap<DexType, WrapperDescriptor> wrappers,
      Map<DexType, DexType> legacyBackport,
      Set<DexType> dontRetarget,
      Map<DexType, CustomConversionDescriptor> customConversions,
      Set<DexMethod> neverOutlineApi,
      Map<DexMethod, MethodAccessFlags> amendLibraryMethods,
      Map<DexField, FieldAccessFlags> amendLibraryFields) {
    this.rewriteType = rewriteType;
    this.maintainType = maintainType;
    this.rewriteDerivedTypeOnly = rewriteDerivedTypeOnly;
    this.staticFieldRetarget = staticFieldRetarget;
    this.covariantRetarget = covariantRetarget;
    this.staticRetarget = staticRetarget;
    this.nonEmulatedVirtualRetarget = nonEmulatedVirtualRetarget;
    this.emulatedVirtualRetarget = emulatedVirtualRetarget;
    this.emulatedVirtualRetargetThroughEmulatedInterface =
        emulatedVirtualRetargetThroughEmulatedInterface;
    this.apiGenericTypesConversion = apiGenericTypesConversion;
    this.emulatedInterfaces = emulatedInterfaces;
    this.wrappers = wrappers;
    this.legacyBackport = legacyBackport;
    this.dontRetarget = dontRetarget;
    this.customConversions = customConversions;
    this.neverOutlineApi = neverOutlineApi;
    this.amendLibraryMethod = amendLibraryMethods;
    this.amendLibraryField = amendLibraryFields;
  }

  // Rewrites all the references to the keys as well as synthetic types derived from any key.
  private final Map<DexType, DexType> rewriteType;
  // Maintains the references in the desugared library dex file.
  private final Set<DexType> maintainType;
  // Rewrites only synthetic types derived from any key.
  private final Map<DexType, DexType> rewriteDerivedTypeOnly;

  // Fields to retarget.
  private final Map<DexField, DexField> staticFieldRetarget;

  // Methods with covariant return type to retarget.
  private final Map<DexMethod, DexMethod> covariantRetarget;

  // Static methods to retarget.
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
  // Virtual methods to retarget through emulated dispatch but handled through emulated interface
  // dispatch. The method has to override an emulated interface method.
  private final Map<DexMethod, DexMethod> emulatedVirtualRetargetThroughEmulatedInterface;

  // Encodes weither specific parameter collections need to be wrapped differently.
  private final Map<DexMethod, DexMethod[]> apiGenericTypesConversion;

  // Emulated interface descriptors.
  private final Map<DexType, EmulatedInterfaceDescriptor> emulatedInterfaces;

  // Wrapper descriptors.
  private final LinkedHashMap<DexType, WrapperDescriptor> wrappers;

  private final Map<DexType, DexType> legacyBackport;
  private final Set<DexType> dontRetarget;
  private final Map<DexType, CustomConversionDescriptor> customConversions;
  private final Set<DexMethod> neverOutlineApi;
  private final Map<DexMethod, MethodAccessFlags> amendLibraryMethod;
  private final Map<DexField, FieldAccessFlags> amendLibraryField;

  public Map<DexType, DexType> getRewriteType() {
    return rewriteType;
  }

  public Set<DexType> getMaintainType() {
    return maintainType;
  }

  public Map<DexType, DexType> getRewriteDerivedTypeOnly() {
    return rewriteDerivedTypeOnly;
  }

  public Map<DexField, DexField> getStaticFieldRetarget() {
    return staticFieldRetarget;
  }

  public Map<DexMethod, DexMethod> getCovariantRetarget() {
    return covariantRetarget;
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

  public Map<DexMethod, DexMethod> getEmulatedVirtualRetargetThroughEmulatedInterface() {
    return emulatedVirtualRetargetThroughEmulatedInterface;
  }

  public Map<DexMethod, DexMethod[]> getApiGenericConversion() {
    return apiGenericTypesConversion;
  }

  public void forEachRetargetMethod(Consumer<DexMethod> consumer) {
    staticRetarget.keySet().forEach(consumer);
    nonEmulatedVirtualRetarget.keySet().forEach(consumer);
    emulatedVirtualRetarget.keySet().forEach(consumer);
  }

  public Map<DexType, EmulatedInterfaceDescriptor> getEmulatedInterfaces() {
    return emulatedInterfaces;
  }

  public LinkedHashMap<DexType, WrapperDescriptor> getWrappers() {
    return wrappers;
  }

  public Map<DexType, DexType> getLegacyBackport() {
    return legacyBackport;
  }

  public Set<DexType> getDontRetarget() {
    return dontRetarget;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isCustomConversionRewrittenType(DexType type) {
    return Iterables.any(
        customConversions.values(),
        descriptor ->
            descriptor.getFrom().getHolderType() == type
                || descriptor.getTo().getHolderType() == type);
  }

  public Map<DexType, CustomConversionDescriptor> getCustomConversions() {
    return customConversions;
  }

  public Set<DexMethod> getNeverOutlineApi() {
    return neverOutlineApi;
  }

  public Map<DexMethod, MethodAccessFlags> getAmendLibraryMethod() {
    return amendLibraryMethod;
  }

  public Map<DexField, FieldAccessFlags> getAmendLibraryField() {
    return amendLibraryField;
  }

  public boolean hasRetargeting() {
    return !covariantRetarget.isEmpty()
        || !staticRetarget.isEmpty()
        || !nonEmulatedVirtualRetarget.isEmpty()
        || !emulatedVirtualRetarget.isEmpty()
        || !staticFieldRetarget.isEmpty();
  }

  @SuppressWarnings("ReferenceEquality")
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

  public boolean isEmpty() {
    return rewriteType.isEmpty()
        && maintainType.isEmpty()
        && rewriteDerivedTypeOnly.isEmpty()
        && !hasRetargeting()
        && emulatedInterfaces.isEmpty()
        && legacyBackport.isEmpty();
  }

  public MachineRewritingFlags withPostPrefix(
      DexItemFactory factory, String oldPrefix, String newPrefix) {
    return new MachineRewritingFlags(
        typeMapWithPostPrefix(rewriteType, factory, oldPrefix, newPrefix),
        maintainType,
        typeMapWithPostPrefix(rewriteDerivedTypeOnly, factory, oldPrefix, newPrefix),
        staticFieldRetarget,
        covariantRetarget,
        staticRetarget,
        nonEmulatedVirtualRetarget,
        emulatedVirtualRetarget,
        emulatedVirtualRetargetThroughEmulatedInterface,
        apiGenericTypesConversion,
        emulatedInterfacesWithPostPrefix(factory, oldPrefix, newPrefix),
        wrappers,
        legacyBackport,
        dontRetarget,
        customConversionsWithPostPrefix(factory, oldPrefix, newPrefix),
        neverOutlineApi,
        amendLibraryMethod,
        amendLibraryField);
  }

  private Map<DexType, CustomConversionDescriptor> customConversionsWithPostPrefix(
      DexItemFactory factory, String oldPrefix, String newPrefix) {
    ImmutableMap.Builder<DexType, CustomConversionDescriptor> builder = ImmutableMap.builder();
    customConversions.forEach(
        (k, v) ->
            builder.put(
                k,
                new CustomConversionDescriptor(
                    methodWithPostPrefix(v.getTo(), factory, oldPrefix, newPrefix),
                    methodWithPostPrefix(v.getFrom(), factory, oldPrefix, newPrefix))));
    return builder.build();
  }

  private Map<DexType, EmulatedInterfaceDescriptor> emulatedInterfacesWithPostPrefix(
      DexItemFactory factory, String oldPrefix, String newPrefix) {
    ImmutableMap.Builder<DexType, EmulatedInterfaceDescriptor> builder = ImmutableMap.builder();
    emulatedInterfaces.forEach(
        (k, v) -> builder.put(k, descriptorWithPostPrefix(v, factory, oldPrefix, newPrefix)));
    return builder.build();
  }

  @SuppressWarnings("ReferenceEquality")
  private EmulatedInterfaceDescriptor descriptorWithPostPrefix(
      EmulatedInterfaceDescriptor descriptor,
      DexItemFactory factory,
      String oldPrefix,
      String newPrefix) {
    DexType rewritten =
        typeWithPostPrefix(descriptor.getRewrittenType(), factory, oldPrefix, newPrefix);
    Map<DexMethod, EmulatedDispatchMethodDescriptor> newDescriptors = new IdentityHashMap<>();
    descriptor
        .getEmulatedMethods()
        .forEach(
            (method, descr) -> {
              assert descr.getInterfaceMethod().getMethod().getHolderType()
                  == descriptor.getRewrittenType();
              newDescriptors.put(
                  method,
                  new EmulatedDispatchMethodDescriptor(
                      new DerivedMethod(
                          descr.getInterfaceMethod().getMethod().withHolder(rewritten, factory),
                          descr.getInterfaceMethod().getMachineHolderKind()),
                      descr.getEmulatedDispatchMethod(),
                      descr.getForwardingMethod(),
                      descr.getDispatchCases()));
            });
    return new EmulatedInterfaceDescriptor(rewritten, newDescriptors);
  }

  private Map<DexType, DexType> typeMapWithPostPrefix(
      Map<DexType, DexType> map, DexItemFactory factory, String oldPrefix, String newPrefix) {
    ImmutableMap.Builder<DexType, DexType> builder = ImmutableMap.builder();
    map.forEach((k, v) -> builder.put(k, typeWithPostPrefix(v, factory, oldPrefix, newPrefix)));
    return builder.build();
  }

  private DexMethod methodWithPostPrefix(
      DexMethod method, DexItemFactory factory, String oldPrefix, String newPrefix) {
    return factory.createMethod(
        method.getHolderType(),
        protoWithPostPrefix(method.getProto(), factory, oldPrefix, newPrefix),
        method.getName());
  }

  private DexProto protoWithPostPrefix(
      DexProto proto, DexItemFactory factory, String oldPrefix, String newPrefix) {
    DexType[] values = proto.getParameters().values;
    DexType[] newValues = new DexType[values.length];
    for (int i = 0; i < values.length; i++) {
      newValues[i] = typeWithPostPrefix(values[i], factory, oldPrefix, newPrefix);
    }
    return factory.createProto(
        typeWithPostPrefix(proto.getReturnType(), factory, oldPrefix, newPrefix),
        DexTypeList.create(newValues));
  }

  private DexType typeWithPostPrefix(
      DexType type, DexItemFactory factory, String oldPrefix, String newPrefix) {
    return factory.createType(type.toDescriptorString().replace(oldPrefix, newPrefix));
  }

  public static class Builder {

    Builder() {}

    private final Map<DexType, DexType> rewriteType = new IdentityHashMap<>();
    private final ImmutableSet.Builder<DexType> maintainType = ImmutableSet.builder();
    private final Map<DexType, DexType> rewriteDerivedTypeOnly = new IdentityHashMap<>();
    private final ImmutableMap.Builder<DexField, DexField> staticFieldRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod> covariantRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod> staticRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod> nonEmulatedVirtualRetarget =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, EmulatedDispatchMethodDescriptor>
        emulatedVirtualRetarget = ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod>
        emulatedVirtualRetargetThroughEmulatedInterface = ImmutableMap.builder();
    private final ImmutableMap.Builder<DexMethod, DexMethod[]> apiGenericTypesConversion =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexType, EmulatedInterfaceDescriptor> emulatedInterfaces =
        ImmutableMap.builder();
    private final LinkedHashMap<DexType, WrapperDescriptor> wrappers = new LinkedHashMap<>();
    private final ImmutableMap.Builder<DexType, DexType> legacyBackport = ImmutableMap.builder();
    private final ImmutableSet.Builder<DexType> dontRetarget = ImmutableSet.builder();
    private final ImmutableMap.Builder<DexType, CustomConversionDescriptor> customConversions =
        ImmutableMap.builder();
    private final ImmutableSet.Builder<DexMethod> neverOutlineApi = ImmutableSet.builder();
    private final ImmutableMap.Builder<DexMethod, MethodAccessFlags> amendLibraryMethod =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<DexField, FieldAccessFlags> amendLibraryField =
        ImmutableMap.builder();

    @SuppressWarnings("ReferenceEquality")
    public void rewriteType(DexType src, DexType target) {
      assert src != null;
      assert target != null;
      assert src != target;
      assert !rewriteType.containsKey(src) || rewriteType.get(src) == target;
      rewriteType.put(src, target);
    }

    public void maintainType(DexType type) {
      assert type != null;
      maintainType.add(type);
    }

    public void rewriteDerivedTypeOnly(DexType src, DexType target) {
      rewriteDerivedTypeOnly.put(src, target);
    }

    public void putStaticFieldRetarget(DexField src, DexField dest) {
      staticFieldRetarget.put(src, dest);
    }

    public void putCovariantRetarget(DexMethod src, DexMethod dest) {
      covariantRetarget.put(src, dest);
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

    public void putEmulatedVirtualRetargetThroughEmulatedInterface(DexMethod src, DexMethod dest) {
      emulatedVirtualRetargetThroughEmulatedInterface.put(src, dest);
    }

    public void addApiGenericTypesConversion(DexMethod method, DexMethod[] conversions) {
      apiGenericTypesConversion.put(method, conversions);
    }

    public void addWrapper(DexType type, WrapperDescriptor descriptor) {
      this.wrappers.put(type, descriptor);
    }

    public void neverOutlineApi(DexMethod method) {
      neverOutlineApi.add(method);
    }

    public void putLegacyBackport(DexType src, DexType target) {
      legacyBackport.put(src, target);
    }

    public void addDontRetarget(DexType type) {
      dontRetarget.add(type);
    }

    public void putCustomConversion(DexType src, CustomConversionDescriptor descriptor) {
      customConversions.put(src, descriptor);
    }

    public void amendLibraryMethod(DexMethod missingReference, MethodAccessFlags flags) {
      amendLibraryMethod.put(missingReference, flags);
    }

    public void amendLibraryField(DexField missingReference, FieldAccessFlags flags) {
      amendLibraryField.put(missingReference, flags);
    }

    public DexType getRewrittenType(DexType type) {
      return rewriteType.get(type);
    }

    private void validate(Set<DexType> maintainTypeBuilt) {
      ArrayList<DexType> warnings = new ArrayList<>();
      for (DexType toRewrite : rewriteType.keySet()) {
        if (maintainTypeBuilt.contains(toRewrite)) {
          warnings.add(toRewrite);
        }
      }
      if (!warnings.isEmpty()) {
        throw new CompilationError(
            "The compilation cannot proceed because the desugared library specification contains"
                + " ambiguous flags that the compiler cannot interpret: The following types are"
                + " both rewritten and maintained "
                + warnings);
      }
    }

    public MachineRewritingFlags build() {
      Set<DexType> maintainTypeBuilt = maintainType.build();
      validate(maintainTypeBuilt);
      return new MachineRewritingFlags(
          rewriteType,
          maintainTypeBuilt,
          rewriteDerivedTypeOnly,
          staticFieldRetarget.build(),
          covariantRetarget.build(),
          staticRetarget.build(),
          nonEmulatedVirtualRetarget.build(),
          emulatedVirtualRetarget.build(),
          emulatedVirtualRetargetThroughEmulatedInterface.build(),
          apiGenericTypesConversion.build(),
          emulatedInterfaces.build(),
          wrappers,
          legacyBackport.build(),
          dontRetarget.build(),
          customConversions.build(),
          neverOutlineApi.build(),
          amendLibraryMethod.build(),
          amendLibraryField.build());
    }
  }
}
