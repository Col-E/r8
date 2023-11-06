// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class HumanRewritingFlags {

  private final Map<String, String> rewritePrefix;
  private final Set<String> dontRewritePrefix;
  private final Set<String> maintainPrefix;
  private final Map<String, Map<String, String>> rewriteDerivedPrefix;
  private final Map<DexType, HumanEmulatedInterfaceDescriptor> emulatedInterfaces;
  private final Map<DexField, DexField> retargetStaticField;
  private final Map<DexMethod, DexType> covariantRetarget;
  private final Map<DexMethod, DexType> retargetMethodToType;
  private final Map<DexMethod, DexType> retargetMethodEmulatedDispatchToType;
  private final Map<DexMethod, DexMethod> retargetMethodToMethod;
  private final Map<DexMethod, DexMethod> retargetMethodEmulatedDispatchToMethod;
  private final Map<DexMethod, DexMethod[]> apiGenericTypesConversion;
  private final Map<DexType, DexType> legacyBackport;
  private final Map<DexType, DexType> customConversions;
  private final Set<DexType> dontRetarget;
  private final Map<DexType, Set<DexMethod>> wrapperConversions;
  private final Set<DexMethod> neverOutlineApi;
  private final Map<DexMethod, MethodAccessFlags> amendLibraryMethod;
  private final Map<DexField, FieldAccessFlags> amendLibraryField;

  HumanRewritingFlags(
      Map<String, String> rewritePrefix,
      Set<String> dontRewritePrefix,
      Set<String> maintainPrefix,
      Map<String, Map<String, String>> rewriteDerivedPrefix,
      Map<DexType, HumanEmulatedInterfaceDescriptor> emulateLibraryInterface,
      Map<DexField, DexField> retargetStaticField,
      Map<DexMethod, DexType> covariantRetarget,
      Map<DexMethod, DexType> retargetMethodToType,
      Map<DexMethod, DexType> retargetMethodEmulatedDispatchToType,
      Map<DexMethod, DexMethod> retargetMethodToMethod,
      Map<DexMethod, DexMethod> retargetMethodEmulatedDispatchToMethod,
      Map<DexMethod, DexMethod[]> apiGenericTypesConversion,
      Map<DexType, DexType> legacyBackport,
      Map<DexType, DexType> customConversion,
      Set<DexType> dontRetarget,
      Map<DexType, Set<DexMethod>> wrapperConversion,
      Set<DexMethod> neverOutlineApi,
      Map<DexMethod, MethodAccessFlags> amendLibraryMethod,
      Map<DexField, FieldAccessFlags> amendLibraryField) {
    this.rewritePrefix = rewritePrefix;
    this.dontRewritePrefix = dontRewritePrefix;
    this.maintainPrefix = maintainPrefix;
    this.rewriteDerivedPrefix = rewriteDerivedPrefix;
    this.emulatedInterfaces = emulateLibraryInterface;
    this.retargetStaticField = retargetStaticField;
    this.covariantRetarget = covariantRetarget;
    this.retargetMethodToType = retargetMethodToType;
    this.retargetMethodEmulatedDispatchToType = retargetMethodEmulatedDispatchToType;
    this.retargetMethodToMethod = retargetMethodToMethod;
    this.retargetMethodEmulatedDispatchToMethod = retargetMethodEmulatedDispatchToMethod;
    this.apiGenericTypesConversion = apiGenericTypesConversion;
    this.legacyBackport = legacyBackport;
    this.customConversions = customConversion;
    this.dontRetarget = dontRetarget;
    this.wrapperConversions = wrapperConversion;
    this.neverOutlineApi = neverOutlineApi;
    this.amendLibraryMethod = amendLibraryMethod;
    this.amendLibraryField = amendLibraryField;
  }

  public static HumanRewritingFlags empty() {
    return new HumanRewritingFlags(
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableSet.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableMap.of(),
        ImmutableSet.of(),
        ImmutableMap.of(),
        ImmutableMap.of());
  }

  public static class HumanEmulatedInterfaceDescriptor {
    private final DexType rewrittenType;
    private final Set<DexMethod> emulatedMethods;

    public HumanEmulatedInterfaceDescriptor(DexType rewrittenType, Set<DexMethod> emulatedMethods) {
      this.rewrittenType = rewrittenType;
      this.emulatedMethods = emulatedMethods;
    }

    public DexType getRewrittenType() {
      return rewrittenType;
    }

    public Set<DexMethod> getEmulatedMethods() {
      return emulatedMethods;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof HumanEmulatedInterfaceDescriptor)) {
        return false;
      }
      HumanEmulatedInterfaceDescriptor other = (HumanEmulatedInterfaceDescriptor) obj;
      return rewrittenType.isIdenticalTo(other.getRewrittenType())
          && getEmulatedMethods().equals(other.getEmulatedMethods());
    }

    @Override
    public int hashCode() {
      return 7 * rewrittenType.hashCode() + getEmulatedMethods().hashCode();
    }
  }

  public static Builder builder(Reporter reporter, Origin origin) {
    return new Builder(reporter, origin);
  }

  public Builder newBuilder(Reporter reporter, Origin origin) {
    return new Builder(
        reporter,
        origin,
        rewritePrefix,
        dontRewritePrefix,
        maintainPrefix,
        rewriteDerivedPrefix,
        emulatedInterfaces,
        retargetStaticField,
        covariantRetarget,
        retargetMethodToType,
        retargetMethodEmulatedDispatchToType,
        retargetMethodToMethod,
        retargetMethodEmulatedDispatchToMethod,
        apiGenericTypesConversion,
        legacyBackport,
        customConversions,
        dontRetarget,
        wrapperConversions,
        neverOutlineApi,
        amendLibraryMethod,
        amendLibraryField);
  }

  public Map<String, String> getRewritePrefix() {
    return rewritePrefix;
  }

  public Set<String> getDontRewritePrefix() {
    return dontRewritePrefix;
  }

  public Set<String> getMaintainPrefix() {
    return maintainPrefix;
  }

  public Map<String, Map<String, String>> getRewriteDerivedPrefix() {
    return rewriteDerivedPrefix;
  }

  public Map<DexType, HumanEmulatedInterfaceDescriptor> getEmulatedInterfaces() {
    return emulatedInterfaces;
  }

  public Map<DexField, DexField> getRetargetStaticField() {
    return retargetStaticField;
  }

  public Map<DexMethod, DexType> getCovariantRetarget() {
    return covariantRetarget;
  }

  public Map<DexMethod, DexType> getRetargetMethodToType() {
    return retargetMethodToType;
  }

  public Map<DexMethod, DexType> getRetargetMethodEmulatedDispatchToType() {
    return retargetMethodEmulatedDispatchToType;
  }

  public Map<DexMethod, DexMethod> getRetargetMethodToMethod() {
    return retargetMethodToMethod;
  }

  public Map<DexMethod, DexMethod> getRetargetMethodEmulatedDispatchToMethod() {
    return retargetMethodEmulatedDispatchToMethod;
  }

  public Set<DexMethod> getNeverOutlineApi() {
    return neverOutlineApi;
  }

  public Map<DexMethod, DexMethod[]> getApiGenericConversion() {
    return apiGenericTypesConversion;
  }

  public Map<DexType, DexType> getLegacyBackport() {
    return legacyBackport;
  }

  public Map<DexType, DexType> getCustomConversions() {
    return customConversions;
  }

  public Set<DexType> getDontRetarget() {
    return dontRetarget;
  }

  public Map<DexType, Set<DexMethod>> getWrapperConversions() {
    return wrapperConversions;
  }

  public Map<DexMethod, MethodAccessFlags> getAmendLibraryMethod() {
    return amendLibraryMethod;
  }

  public Map<DexField, FieldAccessFlags> getAmendLibraryField() {
    return amendLibraryField;
  }

  public boolean isEmpty() {
    return rewritePrefix.isEmpty()
        && rewriteDerivedPrefix.isEmpty()
        && maintainPrefix.isEmpty()
        && emulatedInterfaces.isEmpty()
        && covariantRetarget.isEmpty()
        && retargetMethodToType.isEmpty()
        && retargetMethodEmulatedDispatchToType.isEmpty()
        && retargetStaticField.isEmpty();
  }

  public static class Builder {

    private final Reporter reporter;
    private final Origin origin;

    private final Map<String, String> rewritePrefix;
    private final Set<String> dontRewritePrefix;
    private final Set<String> maintainPrefix;
    private final Map<String, Map<String, String>> rewriteDerivedPrefix;
    private final Map<DexType, HumanEmulatedInterfaceDescriptor> emulatedInterfaces;
    private final Map<DexField, DexField> retargetStaticField;
    private final Map<DexMethod, DexType> covariantRetarget;
    private final Map<DexMethod, DexType> retargetMethodToType;
    private final Map<DexMethod, DexType> retargetMethodEmulatedDispatchToType;
    private final Map<DexMethod, DexMethod> retargetMethodToMethod;
    private final Map<DexMethod, DexMethod> retargetMethodEmulatedDispatchToMethod;
    private final Map<DexMethod, DexMethod[]> apiGenericTypesConversion;
    private final Map<DexType, DexType> legacyBackport;
    private final Map<DexType, DexType> customConversions;
    private final Set<DexType> dontRetarget;
    private final Map<DexType, Set<DexMethod>> wrapperConversions;
    private final Set<DexMethod> neverOutlineApi;
    private final Map<DexMethod, MethodAccessFlags> amendLibraryMethod;
    private final Map<DexField, FieldAccessFlags> amendLibraryField;

    Builder(Reporter reporter, Origin origin) {
      this(
          reporter,
          origin,
          new HashMap<>(),
          Sets.newIdentityHashSet(),
          Sets.newIdentityHashSet(),
          new HashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          Sets.newIdentityHashSet(),
          new IdentityHashMap<>(),
          Sets.newIdentityHashSet(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>());
    }

    Builder(
        Reporter reporter,
        Origin origin,
        Map<String, String> rewritePrefix,
        Set<String> dontRewritePrefix,
        Set<String> maintainPrefix,
        Map<String, Map<String, String>> rewriteDerivedPrefix,
        Map<DexType, HumanEmulatedInterfaceDescriptor> emulateLibraryInterface,
        Map<DexField, DexField> retargetStaticField,
        Map<DexMethod, DexType> covariantRetarget,
        Map<DexMethod, DexType> retargetMethodToType,
        Map<DexMethod, DexType> retargetMethodEmulatedDispatchToType,
        Map<DexMethod, DexMethod> retargetMethodToMethod,
        Map<DexMethod, DexMethod> retargetMethodEmulatedDispatchToMethod,
        Map<DexMethod, DexMethod[]> apiConversionCollection,
        Map<DexType, DexType> backportCoreLibraryMember,
        Map<DexType, DexType> customConversions,
        Set<DexType> dontRetargetLibMember,
        Map<DexType, Set<DexMethod>> wrapperConversions,
        Set<DexMethod> neverOutlineApi,
        Map<DexMethod, MethodAccessFlags> amendLibraryMethod,
        Map<DexField, FieldAccessFlags> amendLibraryField) {
      this.reporter = reporter;
      this.origin = origin;
      this.rewritePrefix = new HashMap<>(rewritePrefix);
      this.dontRewritePrefix = Sets.newHashSet(dontRewritePrefix);
      this.maintainPrefix = Sets.newHashSet(maintainPrefix);
      this.rewriteDerivedPrefix = new HashMap<>(rewriteDerivedPrefix);
      this.emulatedInterfaces = new IdentityHashMap<>(emulateLibraryInterface);
      this.retargetStaticField = new IdentityHashMap<>(retargetStaticField);
      this.covariantRetarget = new IdentityHashMap<>(covariantRetarget);
      this.retargetMethodToType = new IdentityHashMap<>(retargetMethodToType);
      this.retargetMethodEmulatedDispatchToType =
          new IdentityHashMap<>(retargetMethodEmulatedDispatchToType);
      this.retargetMethodToMethod = new IdentityHashMap<>(retargetMethodToMethod);
      this.retargetMethodEmulatedDispatchToMethod =
          new IdentityHashMap<>(retargetMethodEmulatedDispatchToMethod);
      this.apiGenericTypesConversion = new IdentityHashMap<>(apiConversionCollection);
      this.legacyBackport = new IdentityHashMap<>(backportCoreLibraryMember);
      this.customConversions = new IdentityHashMap<>(customConversions);
      this.dontRetarget = Sets.newIdentityHashSet();
      this.dontRetarget.addAll(dontRetargetLibMember);
      this.wrapperConversions = new IdentityHashMap<>(wrapperConversions);
      this.neverOutlineApi = Sets.newIdentityHashSet();
      this.neverOutlineApi.addAll(neverOutlineApi);
      this.amendLibraryMethod = new IdentityHashMap<>(amendLibraryMethod);
      this.amendLibraryField = new IdentityHashMap<>(amendLibraryField);
    }

    // Utility to set values.
    private <K, V> void put(Map<K, V> map, K key, V value, String desc) {
      if (map.containsKey(key) && !map.get(key).equals(value)) {
        throw reporter.fatalError(
            new StringDiagnostic(
                "Invalid desugared library configuration. "
                    + " Duplicate assignment of key: '"
                    + key
                    + "' in sections for '"
                    + desc
                    + "'",
                origin));
      }
      map.put(key, value);
    }

    public Builder putRewritePrefix(String prefix, String rewrittenPrefix) {
      put(
          rewritePrefix,
          prefix,
          rewrittenPrefix,
          HumanDesugaredLibrarySpecificationParser.REWRITE_PREFIX_KEY);
      return this;
    }

    public Builder putDontRewritePrefix(String prefix) {
      dontRewritePrefix.add(prefix);
      return this;
    }

    public Builder putMaintainPrefix(String prefix) {
      maintainPrefix.add(prefix);
      return this;
    }

    public Builder putRewriteDerivedPrefix(
        String prefixToMatch, String prefixToRewrite, String rewrittenPrefix) {
      Map<String, String> map =
          rewriteDerivedPrefix.computeIfAbsent(prefixToMatch, k -> new HashMap<>());
      put(
          map,
          prefixToRewrite,
          rewrittenPrefix,
          HumanDesugaredLibrarySpecificationParser.REWRITE_DERIVED_PREFIX_KEY);
      return this;
    }

    public Builder putEmulatedInterface(
        DexType interfaceType, HumanEmulatedInterfaceDescriptor descriptor) {
      put(
          emulatedInterfaces,
          interfaceType,
          descriptor,
          HumanDesugaredLibrarySpecificationParser.EMULATE_INTERFACE_KEY);
      return this;
    }

    public Builder putCustomConversion(DexType dexType, DexType conversionType) {
      put(
          customConversions,
          dexType,
          conversionType,
          HumanDesugaredLibrarySpecificationParser.CUSTOM_CONVERSION_KEY);
      return this;
    }

    public Builder addWrapperConversion(DexType dexType) {
      return addWrapperConversion(dexType, Collections.emptySet());
    }

    public Builder addWrapperConversion(DexType dexType, Set<DexMethod> excludedMethods) {
      wrapperConversions.put(dexType, excludedMethods);
      return this;
    }

    public Builder retargetMethodToType(DexMethod key, DexType rewrittenType) {
      put(
          retargetMethodToType,
          key,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_KEY);
      return this;
    }

    public Builder retargetMethodEmulatedDispatchToType(DexMethod key, DexType rewrittenType) {
      put(
          retargetMethodEmulatedDispatchToType,
          key,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_EMULATED_DISPATCH_KEY);
      return this;
    }

    public Builder retargetMethodToMethod(DexMethod key, DexMethod retarget) {
      put(
          retargetMethodToMethod,
          key,
          retarget,
          HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_KEY);
      return this;
    }

    public Builder retargetMethodEmulatedDispatchToMethod(DexMethod key, DexMethod retarget) {
      put(
          retargetMethodEmulatedDispatchToMethod,
          key,
          retarget,
          HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_EMULATED_DISPATCH_KEY);
      return this;
    }

    public Builder covariantRetargetMethod(DexMethod key, DexType rewrittenType) {
      put(
          covariantRetarget,
          key,
          rewrittenType,
          HumanDesugaredLibrarySpecificationParser.COVARIANT_RETARGET_METHOD_KEY);
      return this;
    }

    public Builder retargetStaticField(DexField key, DexField value) {
      put(
          retargetStaticField,
          key,
          value,
          HumanDesugaredLibrarySpecificationParser.RETARGET_STATIC_FIELD_KEY);
      return this;
    }

    public void addApiGenericTypesConversion(DexMethod method, int index, DexMethod conversion) {
      DexMethod[] types =
          apiGenericTypesConversion.computeIfAbsent(
              method, k -> new DexMethod[method.getArity() + 1]);
      int actualIndex = index == -1 ? method.getArity() : index;
      assert types[actualIndex] == null;
      types[actualIndex] = conversion;
    }

    public Builder putLegacyBackport(DexType backportType, DexType rewrittenBackportType) {
      put(
          legacyBackport,
          backportType,
          rewrittenBackportType,
          HumanDesugaredLibrarySpecificationParser.BACKPORT_KEY);
      return this;
    }

    public Builder addDontRetargetLibMember(DexType dontRetargetLibMember) {
      dontRetarget.add(dontRetargetLibMember);
      return this;
    }

    public Builder amendLibraryMethod(DexMethod member, MethodAccessFlags flags) {
      amendLibraryMethod.put(member, flags);
      return this;
    }

    public Builder neverOutlineApi(DexMethod method) {
      neverOutlineApi.add(method);
      return this;
    }

    public Builder amendLibraryField(DexField member, FieldAccessFlags flags) {
      amendLibraryField.put(member, flags);
      return this;
    }

    public HumanRewritingFlags build() {
      return new HumanRewritingFlags(
          ImmutableMap.copyOf(rewritePrefix),
          ImmutableSet.copyOf(dontRewritePrefix),
          ImmutableSet.copyOf(maintainPrefix),
          ImmutableMap.copyOf(rewriteDerivedPrefix),
          ImmutableMap.copyOf(emulatedInterfaces),
          ImmutableMap.copyOf(retargetStaticField),
          ImmutableMap.copyOf(covariantRetarget),
          ImmutableMap.copyOf(retargetMethodToType),
          ImmutableMap.copyOf(retargetMethodEmulatedDispatchToType),
          ImmutableMap.copyOf(retargetMethodToMethod),
          ImmutableMap.copyOf(retargetMethodEmulatedDispatchToMethod),
          ImmutableMap.copyOf(apiGenericTypesConversion),
          ImmutableMap.copyOf(legacyBackport),
          ImmutableMap.copyOf(customConversions),
          ImmutableSet.copyOf(dontRetarget),
          ImmutableMap.copyOf(wrapperConversions),
          ImmutableSet.copyOf(neverOutlineApi),
          ImmutableMap.copyOf(amendLibraryMethod),
          ImmutableMap.copyOf(amendLibraryField));
    }
  }
}
