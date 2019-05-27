// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.naming.Minifier.MinificationClassNamingStrategy;
import com.android.tools.r8.naming.Minifier.MinificationPackageNamingStrategy;
import com.android.tools.r8.naming.Minifier.MinifierMemberNamingStrategy;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * The ProguardMapMinifier will assign names to classes and members following the initial naming
 * seed given by the mapping files.
 *
 * <p>First the object hierarchy is traversed maintaining a collection of all program classes and
 * classes that needs to be renamed in {@link #mappedClasses}. For each level we keep track of all
 * renamed members and propagate all non-private items to descendants. This is necessary to ensure
 * that virtual methods are renamed when there are "gaps" in the hierarchy. We keep track of all
 * namings such that future renaming of non-private members will not collide or fail with an error.
 *
 * <p>Second, we compute desugared default interface methods and companion classes to ensure these
 * can be referred to by clients.
 *
 * <p>Third, we traverse all reachable interfaces for class mappings and add them to our tracking
 * maps. Otherwise, the minification follows the ordinary minification.
 */
public class ProguardMapMinifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final SeedMapper seedMapper;
  private final Set<DexCallSite> desugaredCallSites;
  private final BiMap<DexType, DexString> mappedNames = HashBiMap.create();
  private final List<DexClass> mappedClasses = new ArrayList<>();
  private final Map<DexReference, MemberNaming> memberNames = new IdentityHashMap<>();
  private final Map<DexType, DexString> syntheticCompanionClasses = new IdentityHashMap<>();
  private final Map<DexMethod, DexString> defaultInterfaceMethodImplementationNames =
      new IdentityHashMap<>();
  private final Map<DexMethod, DexString> additionalMethodNamings = new IdentityHashMap<>();
  private final Map<DexField, DexString> additionalFieldNamings = new IdentityHashMap<>();

  public ProguardMapMinifier(
      AppView<AppInfoWithLiveness> appView,
      SeedMapper seedMapper,
      Set<DexCallSite> desugaredCallSites) {
    this.appView = appView;
    this.seedMapper = seedMapper;
    this.desugaredCallSites = desugaredCallSites;
  }

  public NamingLens run(Timing timing) {
    timing.begin("MappingClasses");
    computeMapping(appView.dexItemFactory().objectType, new ArrayDeque<>());
    timing.end();

    timing.begin("MappingDefaultInterfaceMethods");
    computeDefaultInterfaceMethodMethods();
    timing.end();

    timing.begin("ComputeInterfaces");
    // We have to compute interfaces
    Set<DexClass> interfaces = new TreeSet<>((a, b) -> a.type.slowCompareTo(b.type));
    for (DexClass dexClass : appView.appInfo().computeReachableInterfaces(desugaredCallSites)) {
      ClassNamingForMapApplier classNaming = seedMapper.getClassNaming(dexClass.type);
      if (classNaming != null) {
        DexString mappedName = appView.dexItemFactory().createString(classNaming.renamedName);
        checkAndAddMappedNames(dexClass.type, mappedName, classNaming.position);
      }
      mappedClasses.add(dexClass);
      interfaces.add(dexClass);
    }
    timing.end();

    appView.options().reporter.failIfPendingErrors();

    // To keep the order deterministic, we sort the classes by their type, which is a unique key.
    mappedClasses.sort((a, b) -> a.type.slowCompareTo(b.type));

    timing.begin("MinifyClasses");
    ClassNameMinifier classNameMinifier =
        new ClassNameMinifier(
            appView,
            new ApplyMappingClassNamingStrategy(appView, mappedNames),
            // The package naming strategy will actually not be used since all classes and methods
            // will be output with identity name if not found in mapping. However, there is a check
            // in the ClassNameMinifier that the strategy should produce a "fresh" name so we just
            // use the existing strategy.
            new MinificationPackageNamingStrategy(appView),
            mappedClasses);
    ClassRenaming classRenaming =
        classNameMinifier.computeRenaming(timing, syntheticCompanionClasses);
    timing.end();

    ApplyMappingMemberNamingStrategy nameStrategy =
        new ApplyMappingMemberNamingStrategy(appView, memberNames);
    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, nameStrategy)
            .computeRenaming(interfaces, desugaredCallSites, timing);
    // Amend the method renamings with the default interface methods.
    methodRenaming.renaming.putAll(defaultInterfaceMethodImplementationNames);
    methodRenaming.renaming.putAll(additionalMethodNamings);
    timing.end();

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming =
        new FieldNameMinifier(appView, nameStrategy).computeRenaming(interfaces, timing);
    fieldRenaming.renaming.putAll(additionalFieldNamings);
    timing.end();

    appView.options().reporter.failIfPendingErrors();

    NamingLens lens = new MinifiedRenaming(appView, classRenaming, methodRenaming, fieldRenaming);

    timing.begin("MinifyIdentifiers");
    new IdentifierMinifier(appView, lens).run();
    timing.end();

    return lens;
  }

  private void computeMapping(DexType type, Deque<Map<DexReference, MemberNaming>> buildUpNames) {
    ClassNamingForMapApplier classNaming = seedMapper.getClassNaming(type);
    DexClass dexClass = appView.definitionFor(type);

    // Keep track of classes that needs to get renamed.
    if (dexClass != null && (classNaming != null || dexClass.isProgramClass())) {
      mappedClasses.add(dexClass);
    }

    Map<DexReference, MemberNaming> nonPrivateMembers = new IdentityHashMap<>();

    if (classNaming != null) {
      // TODO(b/133091438) assert that !dexClass.isLibaryClass();
      DexString mappedName = appView.dexItemFactory().createString(classNaming.renamedName);
      checkAndAddMappedNames(type, mappedName, classNaming.position);

      classNaming.forAllMethodNaming(
          memberNaming -> {
            Signature signature = memberNaming.getOriginalSignature();
            assert !signature.isQualified();
            DexMethod originalMethod =
                ((MethodSignature) signature).toDexMethod(appView.dexItemFactory(), type);
            assert !memberNames.containsKey(originalMethod);
            memberNames.put(originalMethod, memberNaming);
            DexEncodedMethod encodedMethod = appView.definitionFor(originalMethod);
            if (encodedMethod == null || !encodedMethod.accessFlags.isPrivate()) {
              nonPrivateMembers.put(originalMethod, memberNaming);
            }
          });
      classNaming.forAllFieldNaming(
          memberNaming -> {
            Signature signature = memberNaming.getOriginalSignature();
            assert !signature.isQualified();
            DexField originalField =
                ((FieldSignature) signature).toDexField(appView.dexItemFactory(), type);
            assert !memberNames.containsKey(originalField);
            memberNames.put(originalField, memberNaming);
            DexEncodedField encodedField = appView.definitionFor(originalField);
            if (encodedField == null || !encodedField.accessFlags.isPrivate()) {
              nonPrivateMembers.put(originalField, memberNaming);
            }
          });
    } else {
      // We have to ensure we do not rename to an existing member, that cannot be renamed.
      if (dexClass == null || !appView.options().isMinifying()) {
        checkAndAddMappedNames(type, type.descriptor, Position.UNKNOWN);
      } else if (appView.options().isMinifying()
          && appView.rootSet().mayNotBeMinified(type, appView)) {
        checkAndAddMappedNames(type, type.descriptor, Position.UNKNOWN);
      }
    }

    for (Map<DexReference, MemberNaming> parentMembers : buildUpNames) {
      for (DexReference key : parentMembers.keySet()) {
        if (key.isDexMethod()) {
          DexMethod parentReference = key.asDexMethod();
          DexMethod parentReferenceOnCurrentType =
              appView
                  .dexItemFactory()
                  .createMethod(type, parentReference.proto, parentReference.name);
          addMemberNaming(
              key, parentReferenceOnCurrentType, parentMembers, additionalMethodNamings);
        } else {
          DexField parentReference = key.asDexField();
          DexField parentReferenceOnCurrentType =
              appView
                  .dexItemFactory()
                  .createField(type, parentReference.type, parentReference.name);
          addMemberNaming(key, parentReferenceOnCurrentType, parentMembers, additionalFieldNamings);
        }
      }
    }

    if (nonPrivateMembers.size() > 0) {
      buildUpNames.addLast(nonPrivateMembers);
      appView
          .appInfo()
          .forAllExtendsSubtypes(type, subType -> computeMapping(subType, buildUpNames));
      buildUpNames.removeLast();
    } else {
      appView
          .appInfo()
          .forAllExtendsSubtypes(type, subType -> computeMapping(subType, buildUpNames));
    }
  }

  private <T extends DexReference> void addMemberNaming(
      DexReference key,
      T member,
      Map<DexReference, MemberNaming> parentMembers,
      Map<T, DexString> additionalMemberNamings) {
    // We might have overridden a naming in the direct class namings above.
    if (!memberNames.containsKey(member)) {
      DexString renamedName =
          appView.dexItemFactory().createString(parentMembers.get(key).getRenamedName());
      memberNames.put(member, parentMembers.get(key));
      additionalMemberNamings.put(member, renamedName);
    }
  }

  private void checkAndAddMappedNames(DexType type, DexString mappedName, Position position) {
    if (mappedNames.inverse().containsKey(mappedName)) {
      appView
          .options()
          .reporter
          .error(
              ApplyMappingError.mapToExistingClass(
                  type.toString(), mappedName.toString(), position));
    } else {
      mappedNames.put(type, mappedName);
    }
  }

  private void computeDefaultInterfaceMethodMethods() {
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
        computeDefaultInterfaceMethodMappingsForType(
            type,
            classNaming,
            syntheticCompanionClasses,
            defaultInterfaceMethodImplementationNames);
        continue;
      }
    }
  }

  private void computeDefaultInterfaceMethodMappingsForType(
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

  static class ApplyMappingClassNamingStrategy extends MinificationClassNamingStrategy {

    private final Map<DexType, DexString> mappings;
    private final boolean isMinifying;

    ApplyMappingClassNamingStrategy(AppView<?> appView, Map<DexType, DexString> mappings) {
      super(appView);
      this.mappings = mappings;
      this.isMinifying = appView.options().isMinifying();
    }

    @Override
    public DexString next(
        DexType type,
        char[] packagePrefix,
        InternalNamingState state,
        Predicate<DexString> isUsed) {
      DexString nextName = mappings.get(type);
      if (nextName != null) {
        return nextName;
      }
      assert !(isMinifying && noObfuscation(type));
      return isMinifying ? super.next(type, packagePrefix, state, isUsed) : type.descriptor;
    }

    @Override
    public boolean noObfuscation(DexType type) {
      if (mappings.containsKey(type)) {
        return false;
      }
      DexClass dexClass = appView.definitionFor(type);
      if (dexClass == null || dexClass.isNotProgramClass()) {
        return true;
      }
      return super.noObfuscation(type);
    }
  }

  static class ApplyMappingMemberNamingStrategy extends MinifierMemberNamingStrategy {

    private final Map<DexReference, MemberNaming> mappedNames;
    private final DexItemFactory factory;
    private final Reporter reporter;

    public ApplyMappingMemberNamingStrategy(
        AppView<?> appView, Map<DexReference, MemberNaming> mappedNames) {
      super(appView);
      this.mappedNames = mappedNames;
      this.factory = appView.dexItemFactory();
      this.reporter = appView.options().reporter;
    }

    @Override
    public DexString next(
        DexMethod method, InternalNamingState internalState, Predicate<DexString> isUsed) {
      assert !mappedNames.containsKey(method);
      return canMinify(method, method.holder)
          ? super.next(method, internalState, isUsed)
          : method.name;
    }

    @Override
    public DexString next(
        DexField field, InternalNamingState internalState, BiPredicate<DexString, DexType> isUsed) {
      assert !mappedNames.containsKey(field);
      return canMinify(field, field.holder) ? super.next(field, internalState, isUsed) : field.name;
    }

    private boolean canMinify(DexReference reference, DexType type) {
      if (!appView.options().isMinifying()) {
        return false;
      }
      DexClass dexClass = appView.definitionFor(type);
      if (dexClass == null || dexClass.isNotProgramClass()) {
        return false;
      }
      return appView.rootSet().mayBeMinified(reference, appView);
    }

    @Override
    public DexString getReservedNameOrDefault(
        DexEncodedMethod method, DexClass holder, DexString nullValue) {
      if (mappedNames.containsKey(method.method)) {
        return factory.createString(mappedNames.get(method.method).getRenamedName());
      }
      return nullValue;
    }

    @Override
    public DexString getReservedNameOrDefault(
        DexEncodedField field, DexClass holder, DexString nullValue) {
      if (mappedNames.containsKey(field.field)) {
        return factory.createString(mappedNames.get(field.field).getRenamedName());
      }
      return nullValue;
    }

    @Override
    public boolean allowMemberRenaming(DexClass holder) {
      return true;
    }

    @Override
    public void reportReservationError(DexReference source, DexString name) {
      MemberNaming memberNaming = mappedNames.get(source);
      assert source.isDexMethod() || source.isDexField();
      ApplyMappingError applyMappingError =
          ApplyMappingError.mapToExistingMember(
              source.toSourceString(),
              name.toString(),
              memberNaming == null ? Position.UNKNOWN : memberNaming.position);
      reporter.error(applyMappingError);
    }
  }
}
