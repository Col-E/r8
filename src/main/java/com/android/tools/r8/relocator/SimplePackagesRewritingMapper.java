// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

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
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.Map;

class SimplePackagesRewritingMapper {

  private final AppView<?> appView;
  private final Map<DexType, DexString> typeMappings = new IdentityHashMap<>();

  public SimplePackagesRewritingMapper(AppView<?> appView) {
    this.appView = appView;
  }

  public NamingLens compute(Map<PackageReference, PackageReference> mapping) {
    // Prefetch all code objects to ensure we have seen all types.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.getCode() != null) {
          method.getCode().asCfCode();
        }
      }
    }
    ImmutableMap.Builder<String, String> packingMappings = ImmutableMap.builder();
    for (PackageReference key : mapping.keySet()) {
      String source = key.getPackageName();
      String target = mapping.get(key).getPackageName();
      if (source.equals(target)) {
        // No need for relocating identities.
        continue;
      }
      if (source.isEmpty()) {
        assert !target.isEmpty();
        target = target + DescriptorUtils.JAVA_PACKAGE_SEPARATOR;
      }
      String sourceBinary = DescriptorUtils.getBinaryNameFromJavaType(source);
      String targetBinary = DescriptorUtils.getBinaryNameFromJavaType(target);
      packingMappings.put(sourceBinary, targetBinary);
      DexString sourceDescriptor = appView.dexItemFactory().createString("L" + sourceBinary);
      DexString targetDescriptor = appView.dexItemFactory().createString("L" + targetBinary);
      // TODO(b/129925954): Change to a lazy implementation in the naming lens.
      appView
          .dexItemFactory()
          .forAllTypes(
              type -> {
                DexString descriptor = type.descriptor;
                // Check if descriptor can be a prefix.
                if (descriptor.size <= sourceDescriptor.size) {
                  return;
                }
                // Check if it is either the empty prefix or a fully qualified package.
                if (sourceDescriptor.size != 1
                    && descriptor.content[sourceDescriptor.size]
                        != DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR) {
                  return;
                }
                // Do a char-by-char comparison of the prefix.
                if (!descriptor.startsWith(sourceDescriptor)) {
                  return;
                }
                // This type should be mapped.
                if (typeMappings.containsKey(type)) {
                  appView.options().reporter.error(RelocatorDiagnostic.typeRelocateAmbiguous(type));
                  appView.options().reporter.failIfPendingErrors();
                }
                DexString relocatedDescriptor =
                    type.descriptor.withNewPrefix(
                        sourceDescriptor, targetDescriptor, appView.dexItemFactory());
                typeMappings.put(type, relocatedDescriptor);
              });
    }

    return new RelocatorNamingLens(typeMappings, packingMappings.build(), appView.dexItemFactory());
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
