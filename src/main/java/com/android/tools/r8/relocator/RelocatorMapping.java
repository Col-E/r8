// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.relocator;

import static com.android.tools.r8.utils.DescriptorUtils.isClassDescriptor;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.NamingLens.NonIdentityNamingLens;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class RelocatorMapping {

  private final ImmutableMap<PackageReference, PackageReference> packageMappings;
  private final ImmutableMap<ClassReference, ClassReference> classMappings;

  private final ConcurrentHashMap<String, PackageReference> stringToPackageReferenceCache =
      new ConcurrentHashMap<>();

  private RelocatorMapping(
      ImmutableMap<PackageReference, PackageReference> packageMappings,
      ImmutableMap<ClassReference, ClassReference> classMappings) {
    this.packageMappings = packageMappings;
    this.classMappings = classMappings;
  }

  public static RelocatorMapping create(
      ImmutableMap<PackageReference, PackageReference> packageMappings,
      ImmutableMap<ClassReference, ClassReference> classMappings) {
    return new RelocatorMapping(packageMappings, classMappings);
  }

  public Map<PackageReference, PackageReference> getPackageMappings() {
    return packageMappings;
  }

  public NamingLens compute(AppView<?> appView) throws ExecutionException {
    // Prefetch all code objects to ensure we have seen all types.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.getCode() != null) {
          method.getCode().asCfCode();
        }
      }
    }
    // Map all package mappings for resource rewriting.
    Map<String, String> packageMappingForResourceRewriting = new HashMap<>();
    packageMappings.forEach(
        (source, target) ->
            packageMappingForResourceRewriting.put(
                source.getPackageBinaryName(), target.getPackageBinaryName()));

    Map<DexType, DexString> typeMappings = new ConcurrentHashMap<>();
    DexItemFactory factory = appView.dexItemFactory();
    factory.forAllTypes(
        type ->
            computeTypeMapping(type, factory, typeMappings, packageMappingForResourceRewriting));

    return new RelocatorNamingLens(typeMappings, packageMappingForResourceRewriting, factory);
  }

  private PackageReference getPackageReference(String packageName) {
    return stringToPackageReferenceCache.computeIfAbsent(packageName, Reference::packageFromString);
  }

  private void computeTypeMapping(
      DexType type,
      DexItemFactory factory,
      Map<DexType, DexString> typeMappings,
      Map<String, String> rewritePackageMappings) {
    if (!type.isReferenceType()) {
      return;
    }
    if (type.isArrayType()) {
      computeTypeMapping(type.toBaseType(factory), factory, typeMappings, rewritePackageMappings);
      return;
    }
    assert type.isClassType();
    ClassReference directClassMapping = classMappings.get(type.asClassReference());
    if (directClassMapping != null) {
      typeMappings.put(type, factory.createString(directClassMapping.getDescriptor()));
      return;
    }
    // TODO(b/155618698): For now keep computing packages as prefixes but instead of matching
    //  direct prefixes match parent packages. This will ensure that pattern foo/a will not match
    //  package foo/aa.
    computePackageMapping(
        type, type.getPackageName(), factory, typeMappings, rewritePackageMappings);
  }

  /**
   * Compute a mapping for a type based on package mappings. If no mapping is found for the current
   * package name, find the parent package and try again recursively.
   */
  private void computePackageMapping(
      DexType type,
      String currentPackageName,
      DexItemFactory factory,
      Map<DexType, DexString> typeMappings,
      Map<String, String> rewritePackageMappings) {
    PackageReference currentPackageReference = getPackageReference(currentPackageName);
    PackageReference packageReference = packageMappings.get(currentPackageReference);
    if (packageReference != null) {
      DexString sourceDescriptorPrefix =
          factory.createString("L" + currentPackageReference.getPackageBinaryName());
      DexString targetDescriptor =
          factory.createString("L" + packageReference.getPackageBinaryName());
      DexString relocatedDescriptor =
          type.descriptor.withNewPrefix(sourceDescriptorPrefix, targetDescriptor, factory);
      assert isClassDescriptor(relocatedDescriptor.toString());
      typeMappings.put(type, relocatedDescriptor);
      String packageNameFromDescriptor =
          DescriptorUtils.getPackageBinaryNameFromJavaType(
              DescriptorUtils.descriptorToJavaType(relocatedDescriptor.toString()));
      rewritePackageMappings.putIfAbsent(type.getPackageDescriptor(), packageNameFromDescriptor);
    } else if (!currentPackageName.isEmpty()) {
      int lastIndexOfSeparator = currentPackageName.lastIndexOf('.');
      if (lastIndexOfSeparator == -1) {
        computePackageMapping(type, "", factory, typeMappings, rewritePackageMappings);
      } else {
        computePackageMapping(
            type,
            currentPackageName.substring(0, lastIndexOfSeparator),
            factory,
            typeMappings,
            rewritePackageMappings);
      }
    }
  }

  private static class RelocatorNamingLens extends NonIdentityNamingLens {

    private final Map<DexType, DexString> typeMappings;
    private final Map<String, String> packageMappings;

    private RelocatorNamingLens(
        Map<DexType, DexString> typeMappings,
        Map<String, String> packageMappings,
        DexItemFactory factory) {
      super(factory);
      this.typeMappings = typeMappings;
      this.packageMappings = packageMappings;
    }

    @Override
    public String lookupPackageName(String packageName) {
      return packageMappings.getOrDefault(packageName, packageName);
    }

    @Override
    protected DexString internalLookupClassDescriptor(DexType type) {
      return typeMappings.getOrDefault(type, type.descriptor);
    }

    @Override
    public DexString lookupInnerName(InnerClassAttribute attribute, InternalOptions options) {
      return attribute.getInnerName();
    }

    @Override
    public DexString lookupName(DexMethod method) {
      return method.name;
    }

    @Override
    public DexString lookupName(DexField field) {
      return field.name;
    }

    @Override
    public boolean verifyRenamingConsistentWithResolution(DexMethod item) {
      return true;
    }
  }
}
