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
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.naming.ClassNameMinifier.ClassNamingStrategy;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.ClassNameMinifier.Namespace;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.naming.Minifier.MinificationPackageNamingStrategy;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ProguardMapMinifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final SeedMapper seedMapper;
  private final Set<DexCallSite> desugaredCallSites;

  public ProguardMapMinifier(
      AppView<AppInfoWithLiveness> appView,
      SeedMapper seedMapper,
      Set<DexCallSite> desugaredCallSites) {
    this.appView = appView;
    this.seedMapper = seedMapper;
    this.desugaredCallSites = desugaredCallSites;
  }

  public NamingLens run(Timing timing) {
    timing.begin("mapping classes");

    Map<DexType, DexString> mappedNames = new IdentityHashMap<>();
    List<DexClass> mappedClasses = new ArrayList<>();
    Map<DexReference, MemberNaming> memberNames = new IdentityHashMap<>();
    Map<DexType, DexString> syntheticCompanionClasses = new IdentityHashMap<>();
    Map<DexMethod, DexString> defaultInterfaceMethodImplementationNames = new IdentityHashMap<>();
    for (String key : seedMapper.getKeyset()) {
      ClassNamingForMapApplier classNaming = seedMapper.getMapping(key);
      DexType type =
          appView.dexItemFactory().lookupType(appView.dexItemFactory().createString(key));
      if (type == null) {
        // The map contains additional mapping of classes compared to what we have seen. This should
        // have no effect.
        continue;
      }
      DexClass dexClass = appView.definitionFor(type);
      if (dexClass == null) {
        computeDefaultInterfaceMethodMappings(
            type,
            classNaming,
            syntheticCompanionClasses,
            defaultInterfaceMethodImplementationNames);
        continue;
      }
      DexString mappedName = appView.dexItemFactory().createString(classNaming.renamedName);
      DexType mappedType = appView.dexItemFactory().lookupType(mappedName);
      // The mappedType has to be available:
      // - If it is null we have not seen it.
      // - If the mapped type is itself the name is already reserved (by itself).
      // - If the there is no definition for the mapped type we will not get a naming clash.
      // Otherwise, there will be a naming conflict.
      if (mappedType != null && type != mappedType && appView.definitionFor(mappedType) != null) {
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
            DexMethod originalMethod =
                ((MethodSignature) signature).toDexMethod(appView.dexItemFactory(), type);
            assert !memberNames.containsKey(originalMethod);
            memberNames.put(originalMethod, memberNaming);
          });
      classNaming.forAllFieldNaming(
          memberNaming -> {
            Signature signature = memberNaming.getOriginalSignature();
            assert !signature.isQualified();
            DexField originalField =
                ((FieldSignature) signature).toDexField(appView.dexItemFactory(), type);
            assert !memberNames.containsKey(originalField);
            memberNames.put(originalField, memberNaming);
          });
    }

    appView.options().reporter.failIfPendingErrors();

    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            new ApplyMappingClassNamingStrategy(mappedNames),
            // The package naming strategy will actually not be used since all classes and methods
            // will be output with identity name if not found in mapping. However, there is a check
            // in the ClassNameMinifier that the strategy should produce a "fresh" name so we just
            // use the existing strategy.
            new MinificationPackageNamingStrategy(),
            mappedClasses);
    ClassRenaming classRenaming =
        classNameMinifier.computeRenaming(timing, syntheticCompanionClasses);
    timing.end();

    Set<DexClass> interfaces = new TreeSet<>((a, b) -> a.type.slowCompareTo(b.type));
    interfaces.addAll(appView.appInfo().computeReachableInterfaces(desugaredCallSites));

    ApplyMappingMemberNamingStrategy nameStrategy =
        new ApplyMappingMemberNamingStrategy(
            memberNames, appView.dexItemFactory(), appView.options().reporter);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, nameStrategy)
            .computeRenaming(interfaces, desugaredCallSites, timing);
    // Amend the method renamings with the default interface methods.
    methodRenaming.renaming.putAll(defaultInterfaceMethodImplementationNames);
    timing.end();

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, nameStrategy).computeRenaming(interfaces, timing);
    timing.end();

    appView.options().reporter.failIfPendingErrors();

    NamingLens lens = new MinifiedRenaming(appView, classRenaming, methodRenaming, fieldRenaming);

    timing.begin("MinifyIdentifiers");
    new IdentifierMinifier(appView, lens).run();
    timing.end();

    return lens;
  }

  private void computeDefaultInterfaceMethodMappings(
      DexType type,
      ClassNamingForMapApplier classNaming,
      Map<DexType, DexString> syntheticCompanionClasses,
      Map<DexMethod, DexString> defaultInterfaceMethodImplementationNames) {
    // If the class does not resolve, then check if it is a companion class for an interface on
    // the class path.
    if (!InterfaceMethodRewriter.isCompanionClassType(type)) {
      return;
    }
    DexClass interfaceType =
        appView.definitionFor(
            InterfaceMethodRewriter.getInterfaceClassType(type, appView.dexItemFactory()));
    if (interfaceType == null || !interfaceType.isClasspathClass()) {
      return;
    }
    syntheticCompanionClasses.put(
        type, appView.dexItemFactory().createString(classNaming.renamedName));
    for (List<MemberNaming> namings : classNaming.getQualifiedMethodMembers().values()) {
      // If the qualified name has been mapped to multiple names we can't compute a mapping (and it
      // should not be possible that this is a default interface method in that case.)
      if (namings.size() != 1) {
        continue;
      }
      MemberNaming naming = namings.get(0);
      MethodSignature signature = (MethodSignature) naming.getOriginalSignature();
      if (signature.name.startsWith(interfaceType.type.toSourceString())) {
        DexMethod defaultMethod =
            InterfaceMethodRewriter.defaultAsMethodOfCompanionClass(
                signature.toUnqualified().toDexMethod(appView.dexItemFactory(), interfaceType.type),
                appView.dexItemFactory());
        assert defaultMethod.holder == type;
        defaultInterfaceMethodImplementationNames.put(
            defaultMethod, appView.dexItemFactory().createString(naming.getRenamedName()));
      }
    }
  }

  static class ApplyMappingClassNamingStrategy implements ClassNamingStrategy {

    private final Map<DexType, DexString> mappings;
    // We have an explicit mapping from the proguard map thus everything might have to be renamed.
    private final Set<DexReference> noObfuscation = new HashSet<>();

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

    @Override
    public Set<DexReference> noObfuscation() {
      return noObfuscation;
    }
  }

  static class ApplyMappingMemberNamingStrategy implements MemberNamingStrategy {

    private final Map<DexReference, MemberNaming> mappedNames;
    private final DexItemFactory factory;
    private final Reporter reporter;
    // We have an explicit mapping from the proguard map thus everything might have to be renamed.
    private final Set<DexReference> noObfuscation = new HashSet<>();

    public ApplyMappingMemberNamingStrategy(
        Map<DexReference, MemberNaming> mappedNames, DexItemFactory factory, Reporter reporter) {
      this.mappedNames = mappedNames;
      this.factory = factory;
      this.reporter = reporter;
    }

    @Override
    public DexString next(DexMethod method, MethodNamingState.InternalState internalState) {
      return next(method);
    }

    @Override
    public DexString next(DexField field, FieldNamingState.InternalState internalState) {
      return next(field);
    }

    private DexString next(DexReference reference) {
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
      // If we renamed a member to a name that exists in a subtype we should warn that potentially
      // a member lookup may no longer visit its parent.
      MemberNaming memberNaming = mappedNames.get(source);
      assert source.isDexMethod() || source.isDexField();
      ApplyMappingError applyMappingError = ApplyMappingError.mapToExistingMember(
          source.toSourceString(),
          name.toString(),
          memberNaming == null ? Position.UNKNOWN : memberNaming.position);
      if (source.isDexMethod()) {
        reporter.error(applyMappingError);
      } else {
        // TODO(b/128868424) Check if we can remove this warning for fields.
        reporter.warning(applyMappingError);
      }
      return true;
    }

    @Override
    public Set<DexReference> noObfuscation() {
      return noObfuscation;
    }
  }
}
