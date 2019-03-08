// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ClassNameMinifier.ClassNamingStrategy;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.ClassNameMinifier.Namespace;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MemberNameMinifier.MemberNamingStrategy;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.naming.Minifier.MinificationPackageNamingStrategy;
import com.android.tools.r8.naming.NamingState.InternalState;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProguardMapMinifier {

  private final AppView<? extends AppInfoWithLiveness> appView;
  private final AppInfoWithLiveness appInfo;
  private final RootSet rootSet;
  private final SeedMapper seedMapper;
  private final Set<DexCallSite> desugaredCallSites;
  private final DexItemFactory factory;

  public ProguardMapMinifier(
      AppView<? extends AppInfoWithLiveness> appView,
      RootSet rootSet,
      SeedMapper seedMapper,
      Set<DexCallSite> desugaredCallSites) {
    this.appView = appView;
    this.appInfo = appView.appInfo();
    this.factory = appInfo.dexItemFactory;
    this.rootSet = rootSet;
    this.seedMapper = seedMapper;
    this.desugaredCallSites = desugaredCallSites;
  }

  public NamingLens run(Timing timing) {
    timing.begin("mapping classes");

    // A "fixed" obfuscation is given in the applymapping file.
    // TODO(mkroghj) Refactor into strategy.
    rootSet.noObfuscation.clear();

    Map<DexType, DexString> mappedNames = new IdentityHashMap<>();
    List<DexClass> mappedClasses = new ArrayList<>();
    Map<DexReference, DexString> memberNames = new IdentityHashMap<>();
    for (String key : seedMapper.getKeyset()) {
      DexType type = factory.lookupType(factory.createString(key));
      if (type == null) {
        // The map contains additional mapping of classes compared to what we have seen. This should
        // have no effect.
        continue;
      }
      DexClass dexClass = appInfo.definitionFor(type);
      if (dexClass == null) {
        continue;
      }
      ClassNamingForMapApplier classNaming = seedMapper.getClassNaming(type);
      mappedNames.put(type, factory.createString(classNaming.renamedName));
      mappedClasses.add(dexClass);
      classNaming.forAllMethodNaming(
          memberNaming -> {
            DexMethod originalMethod =
                ((MethodSignature) memberNaming.getOriginalSignature()).toDexMethod(factory, type);
            assert !memberNames.containsKey(originalMethod);
            memberNames.put(
                originalMethod, factory.createString(memberNaming.getRenamedName()));
          });
      classNaming.forAllFieldNaming(
          memberNaming -> {
            DexField originalField =
                ((FieldSignature) memberNaming.getOriginalSignature()).toDexField(factory, type);
            assert !memberNames.containsKey(originalField);
            memberNames.put(
                originalField, factory.createString(memberNaming.getRenamedName()));
          });
    }

    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            rootSet,
            new ApplyMappingClassNamingStrategy(mappedNames),
            // The package naming strategy will actually not be used since all classes and methods
            // will be output with identity name if not found in mapping. However, there is a check
            // in the ClassNameMinifier that the strategy should produce a "fresh" name so we just
            // use the existing strategy.
            new MinificationPackageNamingStrategy(),
            mappedClasses);
    ClassRenaming classRenaming = classNameMinifier.computeRenaming(timing);
    timing.end();

    ApplyMappingMemberNamingStrategy nameStrategy =
        new ApplyMappingMemberNamingStrategy(memberNames);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, rootSet, nameStrategy)
            .computeRenaming(desugaredCallSites, timing);
    timing.end();

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, rootSet, nameStrategy).computeRenaming(timing);
    timing.end();

    NamingLens lens =
        new MinifiedRenaming(classRenaming, methodRenaming, fieldRenaming, appView.appInfo());
    return lens;
  }

  static class ApplyMappingClassNamingStrategy implements ClassNamingStrategy {

    private final Map<DexType, DexString> mappings;

    ApplyMappingClassNamingStrategy(Map<DexType, DexString> mappings) {
      this.mappings = mappings;
    }

    @Override
    public DexString next(Namespace namespace, DexType type, char[] packagePrefix) {
      return mappings.getOrDefault(type, type.descriptor);
    }

    @Override
    public boolean bypassDictionary() {
      return true;
    }
  }

  static class ApplyMappingMemberNamingStrategy implements MemberNamingStrategy {

    private final Map<DexReference, DexString> mappedNames;

    public ApplyMappingMemberNamingStrategy(Map<DexReference, DexString> mappedNames) {
      this.mappedNames = mappedNames;
    }

    @Override
    public DexString next(DexReference reference, InternalState internalState) {
      if (mappedNames.containsKey(reference)) {
        return mappedNames.get(reference);
      } else if (reference.isDexMethod()) {
        return reference.asDexMethod().name;
      } else {
        assert reference.isDexField();
        return reference.asDexField().name;
      }
    }

    @Override
    public boolean bypassDictionary() {
      return true;
    }
  }
}
