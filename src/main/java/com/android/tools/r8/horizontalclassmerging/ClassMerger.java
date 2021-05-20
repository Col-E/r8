// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.google.common.base.Predicates.not;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.code.ClassInitializerSynthesizedCode;
import com.android.tools.r8.ir.analysis.value.NumberFromIntervalValue;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The class merger is responsible for moving methods from the sources in {@link ClassMerger#group}
 * into the target of {@link ClassMerger#group}. While performing merging, this class tracks which
 * methods have been moved, as well as which fields have been remapped in the {@link
 * ClassMerger#lensBuilder}.
 */
public class ClassMerger {

  public static final String CLASS_ID_FIELD_NAME = "$r8$classId";

  private static final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Mode mode;
  private final MergeGroup group;
  private final DexItemFactory dexItemFactory;
  private final ClassInitializerSynthesizedCode classInitializerSynthesizedCode;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;

  private final ClassMethodsBuilder classMethodsBuilder = new ClassMethodsBuilder();
  private final Reference2IntMap<DexType> classIdentifiers = new Reference2IntOpenHashMap<>();
  private final ClassStaticFieldsMerger classStaticFieldsMerger;
  private final ClassInstanceFieldsMerger classInstanceFieldsMerger;
  private final Collection<VirtualMethodMerger> virtualMethodMergers;
  private final Collection<ConstructorMerger> constructorMergers;

  private ClassMerger(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Mode mode,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      MergeGroup group,
      Collection<VirtualMethodMerger> virtualMethodMergers,
      Collection<ConstructorMerger> constructorMergers,
      ClassInitializerSynthesizedCode classInitializerSynthesizedCode) {
    this.appView = appView;
    this.mode = mode;
    this.lensBuilder = lensBuilder;
    this.group = group;
    this.virtualMethodMergers = virtualMethodMergers;
    this.constructorMergers = constructorMergers;

    this.dexItemFactory = appView.dexItemFactory();
    this.classInitializerSynthesizedCode = classInitializerSynthesizedCode;
    this.classStaticFieldsMerger = new ClassStaticFieldsMerger(appView, lensBuilder, group);
    this.classInstanceFieldsMerger = new ClassInstanceFieldsMerger(appView, lensBuilder, group);

    buildClassIdentifierMap();
  }

  void buildClassIdentifierMap() {
    classIdentifiers.put(group.getTarget().getType(), 0);
    group.forEachSource(clazz -> classIdentifiers.put(clazz.getType(), classIdentifiers.size()));
  }

  void mergeDirectMethods(SyntheticArgumentClass syntheticArgumentClass) {
    mergeStaticClassInitializers();
    mergeDirectMethods(group.getTarget());
    group.forEachSource(this::mergeDirectMethods);
    mergeConstructors(syntheticArgumentClass);
  }

  void mergeStaticClassInitializers() {
    if (classInitializerSynthesizedCode.isEmpty()) {
      return;
    }

    DexMethod newClinit = dexItemFactory.createClassInitializer(group.getTarget().getType());
    Code code = classInitializerSynthesizedCode.getOrCreateCode(group.getTarget().getType());
    if (!group.getTarget().hasClassInitializer()) {
      classMethodsBuilder.addDirectMethod(
          new DexEncodedMethod(
              newClinit,
              MethodAccessFlags.fromSharedAccessFlags(
                  Constants.ACC_SYNTHETIC | Constants.ACC_STATIC, true),
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              code,
              true,
              classInitializerSynthesizedCode.getCfVersion()));
    } else {
      DexEncodedMethod clinit = group.getTarget().getClassInitializer();
      clinit.setCode(code, appView);
      if (code.isCfCode()) {
        CfVersion cfVersion = classInitializerSynthesizedCode.getCfVersion();
        if (cfVersion != null) {
          clinit.upgradeClassFileVersion(cfVersion);
        }
      }
      classMethodsBuilder.addDirectMethod(clinit);
    }
  }

  void mergeDirectMethods(DexProgramClass toMerge) {
    toMerge.forEachProgramDirectMethod(
        method -> {
          DexEncodedMethod definition = method.getDefinition();
          if (definition.isClassInitializer()) {
            lensBuilder.moveMethod(
                method.getReference(),
                dexItemFactory.createClassInitializer(group.getTarget().getType()));
          } else if (!definition.isInstanceInitializer()) {
            DexMethod newMethod =
                method.getReference().withHolder(group.getTarget().getType(), dexItemFactory);
            if (!classMethodsBuilder.isFresh(newMethod)) {
              newMethod = renameDirectMethod(method);
            }
            classMethodsBuilder.addDirectMethod(definition.toTypeSubstitutedMethod(newMethod));
            if (definition.getReference() != newMethod) {
              lensBuilder.moveMethod(definition.getReference(), newMethod);
            }
          }
        });
    // Clear the members of the class to be merged since they have now been moved to the target.
    toMerge.getMethodCollection().clearDirectMethods();
  }

  /**
   * Find a new name for the method.
   *
   * @param method The class the method originally belonged to.
   */
  DexMethod renameDirectMethod(ProgramMethod method) {
    assert method.getDefinition().belongsToDirectPool();
    return dexItemFactory.createFreshMethodNameWithoutHolder(
        method.getName().toSourceString(),
        method.getProto(),
        group.getTarget().getType(),
        classMethodsBuilder::isFresh);
  }

  void mergeConstructors(SyntheticArgumentClass syntheticArgumentClass) {
    constructorMergers.forEach(
        merger ->
            merger.merge(
                classMethodsBuilder,
                lensBuilder,
                classIdentifiers,
                syntheticArgumentClass));
  }

  void mergeVirtualMethods() {
    virtualMethodMergers.forEach(
        merger -> merger.merge(classMethodsBuilder, lensBuilder, classIdentifiers));
    group.forEachSource(clazz -> clazz.getMethodCollection().clearVirtualMethods());
  }

  void appendClassIdField() {
    assert appView.hasLiveness();
    assert mode.isInitial();

    boolean deprecated = false;
    boolean d8R8Synthesized = true;
    DexEncodedField classIdField =
        new DexEncodedField(
            group.getClassIdField(),
            FieldAccessFlags.createPublicFinalSynthetic(),
            FieldTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            null,
            deprecated,
            d8R8Synthesized);

    // For the $r8$classId synthesized fields, we try to over-approximate the set of values it may
    // have. For example, for a merge group of size 4, we may compute the set {0, 2, 3}, if the
    // instances with $r8$classId == 1 ends up dead as a result of optimizations). If no instances
    // end up being dead, we would compute the set {0, 1, 2, 3}. The latter information does not
    // provide any value, and therefore we should not save it in the optimization info. In order to
    // be able to recognize that {0, 1, 2, 3} is useless, we record that the value of the field is
    // known to be in [0; 3] here.
    NumberFromIntervalValue abstractValue = new NumberFromIntervalValue(0, group.size() - 1);
    feedback.recordFieldHasAbstractValue(classIdField, appView.withLiveness(), abstractValue);

    classInstanceFieldsMerger.setClassIdField(classIdField);
  }

  void mergeStaticFields() {
    group.forEachSource(classStaticFieldsMerger::addFields);
    classStaticFieldsMerger.merge(group.getTarget());
    group.forEachSource(clazz -> clazz.setStaticFields(null));
  }

  void fixAccessFlags() {
    if (Iterables.any(group.getSources(), not(DexProgramClass::isAbstract))) {
      group.getTarget().getAccessFlags().demoteFromAbstract();
    }
    if (Iterables.any(group.getSources(), not(DexProgramClass::isFinal))) {
      group.getTarget().getAccessFlags().demoteFromFinal();
    }
  }

  void fixNestMemberAttributes() {
    if (group.getTarget().isInANest() && !group.getTarget().hasNestMemberAttributes()) {
      for (DexProgramClass clazz : group.getSources()) {
        if (clazz.hasNestMemberAttributes()) {
          // The nest host has been merged into a nest member.
          group.getTarget().clearNestHost();
          group.getTarget().setNestMemberAttributes(clazz.getNestMembersClassAttributes());
          group
              .getTarget()
              .removeNestMemberAttributes(
                  nestMemberAttribute ->
                      nestMemberAttribute.getNestMember() == group.getTarget().getType());
          break;
        }
      }
    }
  }

  private void mergeAnnotations() {
    assert group.getClasses().stream().filter(DexDefinition::hasAnnotations).count() <= 1;
    for (DexProgramClass clazz : group.getSources()) {
      if (clazz.hasAnnotations()) {
        group.getTarget().setAnnotations(clazz.annotations());
        break;
      }
    }
  }

  private void mergeInterfaces() {
    DexTypeList previousInterfaces = group.getTarget().getInterfaces();
    Set<DexType> interfaces = Sets.newLinkedHashSet(previousInterfaces);
    if (group.isInterfaceGroup()) {
      // Add all implemented interfaces from the merge group to the target class, ignoring
      // implemented interfaces that are part of the merge group.
      Set<DexType> groupTypes =
          SetUtils.newImmutableSet(
              builder -> group.forEach(clazz -> builder.accept(clazz.getType())));
      group.forEachSource(
          clazz -> {
            for (DexType itf : clazz.getInterfaces()) {
              if (!groupTypes.contains(itf)) {
                interfaces.add(itf);
              }
            }
          });
    } else {
      // Add all implemented interfaces from the merge group to the target class.
      group.forEachSource(clazz -> Iterables.addAll(interfaces, clazz.getInterfaces()));
    }
    group.getTarget().setInterfaces(DexTypeList.create(interfaces));
  }

  void mergeInstanceFields() {
    group.forEachSource(
        clazz -> {
          classInstanceFieldsMerger.addFields(clazz);
          clazz.clearInstanceFields();
        });
    group.getTarget().setInstanceFields(classInstanceFieldsMerger.merge());
  }

  public void mergeGroup(SyntheticArgumentClass syntheticArgumentClass) {
    fixAccessFlags();
    fixNestMemberAttributes();

    if (group.hasClassIdField()) {
      appendClassIdField();
    }

    mergeAnnotations();
    mergeInterfaces();

    mergeVirtualMethods();
    mergeDirectMethods(syntheticArgumentClass);
    classMethodsBuilder.setClassMethods(group.getTarget());

    mergeStaticFields();
    mergeInstanceFields();
  }

  public static class Builder {
    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private Mode mode;
    private final MergeGroup group;

    public Builder(AppView<? extends AppInfoWithClassHierarchy> appView, MergeGroup group) {
      this.appView = appView;
      this.group = group;
    }

    Builder setMode(Mode mode) {
      this.mode = mode;
      return this;
    }

    private void selectTarget() {
      Iterable<DexProgramClass> candidates = Iterables.filter(group, DexClass::isPublic);
      if (IterableUtils.isEmpty(candidates)) {
        candidates = group;
      }
      Iterator<DexProgramClass> candidateIterator = candidates.iterator();
      DexProgramClass target = IterableUtils.first(candidates);
      while (candidateIterator.hasNext()) {
        DexProgramClass current = candidateIterator.next();
        KeepClassInfo keepClassInfo = appView.getKeepInfo().getClassInfo(current);
        if (keepClassInfo.isMinificationAllowed(appView.options())) {
          target = current;
          break;
        }
        // Select the target with the shortest name.
        if (current.getType().getDescriptor().size() < target.getType().getDescriptor().size) {
          target = current;
        }
      }
      group.setTarget(appView.testing().horizontalClassMergingTarget.apply(candidates, target));
    }

    private ClassInitializerSynthesizedCode createClassInitializerMerger() {
      ClassInitializerSynthesizedCode.Builder builder =
          new ClassInitializerSynthesizedCode.Builder();
      group.forEach(
          clazz -> {
            if (clazz.hasClassInitializer()) {
              builder.add(clazz.getClassInitializer());
            }
          });
      return builder.build();
    }

    private List<ConstructorMerger> createInstanceInitializerMergers() {
      List<ConstructorMerger> constructorMergers = new ArrayList<>();
      if (appView.options().horizontalClassMergerOptions().isConstructorMergingEnabled()) {
        Map<DexProto, ConstructorMerger.Builder> buildersByProto = new LinkedHashMap<>();
        group.forEach(
            clazz ->
                clazz.forEachProgramDirectMethodMatching(
                    DexEncodedMethod::isInstanceInitializer,
                    method ->
                        buildersByProto
                            .computeIfAbsent(
                                method.getDefinition().getProto(),
                                ignore -> new ConstructorMerger.Builder(appView))
                            .add(method.getDefinition())));
        for (ConstructorMerger.Builder builder : buildersByProto.values()) {
          constructorMergers.addAll(builder.build(group));
        }
      } else {
        group.forEach(
            clazz ->
                clazz.forEachProgramDirectMethodMatching(
                    DexEncodedMethod::isInstanceInitializer,
                    method ->
                        constructorMergers.addAll(
                            new ConstructorMerger.Builder(appView)
                                .add(method.getDefinition())
                                .build(group))));
      }

      // Try and merge the constructors with the most arguments first, to avoid using synthetic
      // arguments if possible.
      constructorMergers.sort(Comparator.comparing(ConstructorMerger::getArity).reversed());
      return constructorMergers;
    }

    private List<VirtualMethodMerger> createVirtualMethodMergers() {
      Map<DexMethodSignature, VirtualMethodMerger.Builder> virtualMethodMergerBuilders =
          new LinkedHashMap<>();
      group.forEach(
          clazz ->
              clazz.forEachProgramVirtualMethod(
                  virtualMethod ->
                      virtualMethodMergerBuilders
                          .computeIfAbsent(
                              virtualMethod.getReference().getSignature(),
                              ignore -> new VirtualMethodMerger.Builder())
                          .add(virtualMethod)));
      List<VirtualMethodMerger> virtualMethodMergers =
          new ArrayList<>(virtualMethodMergerBuilders.size());
      for (VirtualMethodMerger.Builder builder : virtualMethodMergerBuilders.values()) {
        virtualMethodMergers.add(builder.build(appView, group));
      }
      return virtualMethodMergers;
    }

    private void createClassIdField() {
      // TODO(b/165498187): ensure the name for the field is fresh
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      group.setClassIdField(
          dexItemFactory.createField(
              group.getTarget().getType(), dexItemFactory.intType, CLASS_ID_FIELD_NAME));
    }

    public ClassMerger build(
        HorizontalClassMergerGraphLens.Builder lensBuilder) {
      selectTarget();

      List<VirtualMethodMerger> virtualMethodMergers = createVirtualMethodMergers();

      boolean requiresClassIdField =
          virtualMethodMergers.stream()
              .anyMatch(virtualMethodMerger -> !virtualMethodMerger.isNopOrTrivial());
      if (requiresClassIdField) {
        createClassIdField();
      }

      return new ClassMerger(
          appView,
          mode,
          lensBuilder,
          group,
          virtualMethodMergers,
          createInstanceInitializerMergers(),
          createClassInitializerMerger());
    }
  }
}
