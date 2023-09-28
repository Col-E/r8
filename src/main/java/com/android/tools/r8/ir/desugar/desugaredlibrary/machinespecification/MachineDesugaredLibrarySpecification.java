// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.LibraryValidator;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MachineDesugaredLibrarySpecification implements DesugaredLibrarySpecification {

  private final boolean libraryCompilation;
  private final MachineTopLevelFlags topLevelFlags;
  private final MachineRewritingFlags rewritingFlags;

  private int leadingVersionNumberCache = -1;

  public static MachineDesugaredLibrarySpecification empty() {
    return new MachineDesugaredLibrarySpecification(
        false, MachineTopLevelFlags.empty(), MachineRewritingFlags.builder().build()) {
      @Override
      public boolean isSupported(DexReference reference) {
        return false;
      }
    };
  }

  public static MachineDesugaredLibrarySpecification withOnlyRewriteTypeForTesting(
      Map<DexType, DexType> rewriteTypeForTesting) {
    MachineRewritingFlags.Builder builder = MachineRewritingFlags.builder();
    rewriteTypeForTesting.forEach(builder::rewriteType);
    return new MachineDesugaredLibrarySpecification(
        true, MachineTopLevelFlags.empty(), builder.build());
  }

  public MachineDesugaredLibrarySpecification(
      boolean libraryCompilation,
      MachineTopLevelFlags topLevelFlags,
      MachineRewritingFlags rewritingFlags) {
    this.libraryCompilation = libraryCompilation;
    this.topLevelFlags = topLevelFlags;
    this.rewritingFlags = rewritingFlags;
  }

  @Override
  public boolean isEmpty() {
    return rewritingFlags.isEmpty();
  }

  @Override
  public boolean isLibraryCompilation() {
    return libraryCompilation;
  }

  public MachineTopLevelFlags getTopLevelFlags() {
    return topLevelFlags;
  }

  public MachineRewritingFlags getRewritingFlags() {
    return rewritingFlags;
  }

  @Override
  public AndroidApiLevel getRequiredCompilationApiLevel() {
    return topLevelFlags.getRequiredCompilationApiLevel();
  }

  @Override
  public String getSynthesizedLibraryClassesPackagePrefix() {
    return topLevelFlags.getSynthesizedLibraryClassesPackagePrefix();
  }

  @Override
  public String getIdentifier() {
    return topLevelFlags.getIdentifier();
  }

  @Override
  public String getJsonSource() {
    return topLevelFlags.getJsonSource();
  }

  public boolean supportAllCallbacksFromLibrary() {
    return topLevelFlags.supportAllCallbacksFromLibrary();
  }

  @Override
  public List<String> getExtraKeepRules() {
    return topLevelFlags.getExtraKeepRules();
  }

  @Override
  public Set<String> getMaintainTypeOrPrefixForTesting() {
    return rewritingFlags.getMaintainType().stream()
        .map(DexType::toString)
        .collect(Collectors.toSet());
  }

  public Map<DexType, DexType> getRewriteType() {
    return rewritingFlags.getRewriteType();
  }

  public Set<DexType> getMaintainType() {
    return rewritingFlags.getMaintainType();
  }

  public Map<DexType, DexType> getRewriteDerivedTypeOnly() {
    return rewritingFlags.getRewriteDerivedTypeOnly();
  }

  public Map<DexField, DexField> getStaticFieldRetarget() {
    return rewritingFlags.getStaticFieldRetarget();
  }

  public Map<DexMethod, DexMethod> getCovariantRetarget() {
    return rewritingFlags.getCovariantRetarget();
  }

  public Map<DexMethod, DexMethod> getStaticRetarget() {
    return rewritingFlags.getStaticRetarget();
  }

  public Map<DexMethod, DexMethod> getNonEmulatedVirtualRetarget() {
    return rewritingFlags.getNonEmulatedVirtualRetarget();
  }

  public Map<DexMethod, EmulatedDispatchMethodDescriptor> getEmulatedVirtualRetarget() {
    return rewritingFlags.getEmulatedVirtualRetarget();
  }

  public Map<DexMethod, DexMethod> getEmulatedVirtualRetargetThroughEmulatedInterface() {
    return rewritingFlags.getEmulatedVirtualRetargetThroughEmulatedInterface();
  }

  public Map<DexMethod, DexMethod[]> getApiGenericConversion() {
    return rewritingFlags.getApiGenericConversion();
  }

  public void forEachRetargetMethod(Consumer<DexMethod> consumer) {
    rewritingFlags.forEachRetargetMethod(consumer);
  }

  public Map<DexType, EmulatedInterfaceDescriptor> getEmulatedInterfaces() {
    return rewritingFlags.getEmulatedInterfaces();
  }

  public EmulatedDispatchMethodDescriptor getEmulatedInterfaceEmulatedDispatchMethodDescriptor(
      DexMethod method) {
    return rewritingFlags.getEmulatedInterfaceEmulatedDispatchMethodDescriptor(method);
  }

  public boolean isCustomConversionRewrittenType(DexType type) {
    return rewritingFlags.isCustomConversionRewrittenType(type);
  }

  public boolean isEmulatedInterfaceRewrittenType(DexType type) {
    return rewritingFlags.isEmulatedInterfaceRewrittenType(type);
  }

  public Map<DexType, WrapperDescriptor> getWrappers() {
    return rewritingFlags.getWrappers();
  }

  public Map<DexType, DexType> getLegacyBackport() {
    return rewritingFlags.getLegacyBackport();
  }

  public Set<DexType> getDontRetarget() {
    return rewritingFlags.getDontRetarget();
  }

  public Map<DexType, CustomConversionDescriptor> getCustomConversions() {
    return rewritingFlags.getCustomConversions();
  }

  public Set<DexMethod> getNeverOutlineApi() {
    return rewritingFlags.getNeverOutlineApi();
  }

  public Map<DexMethod, MethodAccessFlags> getAmendLibraryMethods() {
    return rewritingFlags.getAmendLibraryMethod();
  }

  public Map<DexField, FieldAccessFlags> getAmendLibraryFields() {
    return rewritingFlags.getAmendLibraryField();
  }

  public boolean hasRetargeting() {
    return rewritingFlags.hasRetargeting();
  }

  public boolean hasEmulatedInterfaces() {
    return rewritingFlags.hasEmulatedInterfaces();
  }

  public boolean isSupported(DexReference reference) {
    if (isContextTypeMaintainedOrRewritten(reference)) {
      return true;
    }
    if (!reference.isDexMethod()) {
      return false;
    }
    // Support through retargeting.
    DexMethod dexMethod = reference.asDexMethod();
    if (getStaticRetarget().containsKey(dexMethod)
        || getNonEmulatedVirtualRetarget().containsKey(dexMethod)
        || getEmulatedVirtualRetarget().containsKey(dexMethod)) {
      return true;
    }
    // Support through emulated interface.
    for (EmulatedInterfaceDescriptor descriptor : getEmulatedInterfaces().values()) {
      if (descriptor.getEmulatedMethods().containsKey(dexMethod)) {
        return true;
      }
    }
    return false;
  }

  public boolean isContextTypeMaintainedOrRewritten(DexReference reference) {
    // Support through type rewriting.
    return getRewriteType().containsKey(reference.getContextType())
        || getMaintainType().contains(reference.getContextType());
  }

  @Override
  public MachineDesugaredLibrarySpecification toMachineSpecification(
      DexApplication app, Timing timing) {
    LibraryValidator.validate(app, libraryCompilation, getRequiredCompilationApiLevel());
    return this;
  }

  public boolean requiresTypeRewriting() {
    return !getRewriteType().isEmpty() || !getRewriteDerivedTypeOnly().isEmpty();
  }

  private int getLeadingVersionNumber() {
    if (leadingVersionNumberCache != -1) {
      return leadingVersionNumberCache;
    }
    List<String> split = StringUtils.split(topLevelFlags.getIdentifier(), ':');
    String last = ListUtils.last(split);
    return leadingVersionNumberCache = SemanticVersion.parse(last).getMajor();
  }

  public boolean includesJDK11Methods() {
    return getLeadingVersionNumber() >= 2;
  }

  public MachineDesugaredLibrarySpecification withPostPrefix(
      DexItemFactory factory, String postPrefix) {
    String oldPrefix = topLevelFlags.getSynthesizedLibraryClassesPackagePrefix();
    String newPrefix = oldPrefix + DescriptorUtils.getPackageBinaryNameFromJavaType(postPrefix);
    return new MachineDesugaredLibrarySpecification(
        libraryCompilation,
        topLevelFlags.withPostPrefix(postPrefix),
        rewritingFlags.withPostPrefix(factory, oldPrefix, newPrefix));
  }
}
