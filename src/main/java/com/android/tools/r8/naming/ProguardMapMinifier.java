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
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.naming.Minifier.MinificationPackageNamingStrategy;
import com.android.tools.r8.naming.NamingState.InternalState;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.Reporter;
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
    Map<DexReference, MemberNaming> memberNames = new IdentityHashMap<>();
    for (String key : seedMapper.getKeyset()) {
      ClassNamingForMapApplier classNaming = seedMapper.getMapping(key);
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
      DexString mappedName = factory.createString(classNaming.renamedName);
      DexType mappedType = factory.lookupType(mappedName);
      // The mappedType has to be available:
      // - If it is null we have not seen it.
      // - If the mapped type is itself the name is already reserved (by itself).
      // - If the there is no definition for the mapped type we will not get a naming clash.
      // Otherwise, there will be a naming conflict.
      if (mappedType != null && type != mappedType && appInfo.definitionFor(mappedType) != null) {
        appView
            .options()
            .reporter
            .error(
                ApplyMappingError.mapToExistingClass(
                    type.toString(), mappedType.toString(), classNaming.position));
      }
      mappedNames.put(type, mappedName);
      mappedClasses.add(dexClass);
      classNaming.forAllMethodNaming(
          memberNaming -> {
            Signature signature = memberNaming.getOriginalSignature();
            assert !signature.isQualified();
            DexMethod originalMethod = ((MethodSignature) signature).toDexMethod(factory, type);
            assert !memberNames.containsKey(originalMethod);
            memberNames.put(originalMethod, memberNaming);
          });
      classNaming.forAllFieldNaming(
          memberNaming -> {
            Signature signature = memberNaming.getOriginalSignature();
            assert !signature.isQualified();
            DexField originalField = ((FieldSignature) signature).toDexField(factory, type);
            assert !memberNames.containsKey(originalField);
            memberNames.put(originalField, memberNaming);
          });
    }

    appView.options().reporter.failIfPendingErrors();

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
        new ApplyMappingMemberNamingStrategy(
            memberNames, appInfo.dexItemFactory, appView.options().reporter);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, rootSet, nameStrategy)
            .computeRenaming(desugaredCallSites, timing);
    timing.end();

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, rootSet, nameStrategy).computeRenaming(timing);
    timing.end();

    appView.options().reporter.failIfPendingErrors();

    NamingLens lens =
        new MinifiedRenaming(classRenaming, methodRenaming, fieldRenaming, appView.appInfo());

    timing.begin("MinifyIdentifiers");
    new IdentifierMinifier(
            appInfo, appView.options().getProguardConfiguration().getAdaptClassStrings(), lens)
        .run();
    timing.end();

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

    private final Map<DexReference, MemberNaming> mappedNames;
    private final DexItemFactory factory;
    private final Reporter reporter;

    public ApplyMappingMemberNamingStrategy(
        Map<DexReference, MemberNaming> mappedNames, DexItemFactory factory, Reporter reporter) {
      this.mappedNames = mappedNames;
      this.factory = factory;
      this.reporter = reporter;
    }

    @Override
    public DexString next(DexReference reference, InternalState internalState) {
      if (mappedNames.containsKey(reference)) {
        return factory.createString(mappedNames.get(reference).getRenamedName());
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

    @Override
    public boolean breakOnNotAvailable(DexReference source, DexString name) {
      // This is an error where we have renamed a member to an name that exists in a subtype or
      // renamed a field to something that exists in a subclass.
      MemberNaming memberNaming = mappedNames.get(source);
      assert source.isDexMethod() || source.isDexField();
      reporter.error(
          ApplyMappingError.mapToExistingMember(
              source.toSourceString(),
              name.toString(),
              memberNaming == null ? Position.UNKNOWN : memberNaming.position));
      return true;
    }

    @Override
    public DexString getExistingNamingIfPossible(DexReference source) {
      MemberNaming memberNaming = mappedNames.get(source);
      if (memberNaming == null) {
        return null;
      }
      return factory.createString(memberNaming.getRenamedName());
    }
  }
}
