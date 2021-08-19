// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAPIConverter.DESCRIPTOR_VIVIFIED_PREFIX;
import static com.android.tools.r8.utils.collections.IdentityHashSetFromMap.newProgramDerivedContextSet;

import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.diagnostic.internal.MissingClassInfoImpl;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionsDiagnosticImpl;
import com.android.tools.r8.errors.dontwarn.DontWarnConfiguration;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDerivedContext;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.synthesis.SyntheticItems.SynthesizingContextOracle;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MissingClasses {

  private final Set<DexType> missingClasses;

  private MissingClasses(Set<DexType> missingClasses) {
    this.missingClasses = missingClasses;
  }

  public Builder builder() {
    return new Builder(missingClasses);
  }

  public static MissingClasses empty() {
    return new MissingClasses(Sets.newIdentityHashSet());
  }

  public MissingClasses commitSyntheticItems(CommittedItems committedItems) {
    return builder()
        // TODO(b/175542052): Synthetic types should not be reported as missing in the first place.
        .removeAlreadyMissingClasses(committedItems.getLegacySyntheticTypes())
        .build();
  }

  public void forEach(Consumer<DexType> missingClassConsumer) {
    missingClasses.forEach(missingClassConsumer);
  }

  public boolean contains(DexType type) {
    return missingClasses.contains(type);
  }

  public static class Builder {

    private final Set<DexType> alreadyMissingClasses;
    private final Map<DexType, Set<ProgramDerivedContext>> newMissingClasses =
        new IdentityHashMap<>();

    // Set of missing types that are not to be reported as missing. This does not hide reports
    // if the same type is in newMissingClasses in which case it is reported regardless.
    private final Set<DexType> newIgnoredMissingClasses = Sets.newIdentityHashSet();

    private Builder(Set<DexType> alreadyMissingClasses) {
      this.alreadyMissingClasses = alreadyMissingClasses;
    }

    public void addNewMissingClass(DexType type, ProgramDerivedContext context) {
      assert context != null;
      assert context.getContext().getContextType() != type;
      if (!alreadyMissingClasses.contains(type)) {
        newMissingClasses
            .computeIfAbsent(type, ignore -> newProgramDerivedContextSet())
            .add(context);
      }
    }

    public void legacyAddNewMissingClass(DexType type) {
      if (!alreadyMissingClasses.contains(type)) {
        // The legacy reporting is context insensitive, so we just use an empty set of contexts.
        newMissingClasses.computeIfAbsent(type, ignore -> newProgramDerivedContextSet());
      }
    }

    @Deprecated
    public Builder legacyAddNewMissingClasses(Collection<DexType> types) {
      types.forEach(this::legacyAddNewMissingClass);
      return this;
    }

    public void ignoreNewMissingClass(DexType type) {
      newIgnoredMissingClasses.add(type);
    }

    public boolean contains(DexType type) {
      return alreadyMissingClasses.contains(type)
          || newMissingClasses.containsKey(type)
          || newIgnoredMissingClasses.contains(type);
    }

    Builder removeAlreadyMissingClasses(Iterable<DexType> types) {
      for (DexType type : types) {
        alreadyMissingClasses.remove(type);
      }
      return this;
    }

    public MissingClasses assertNoMissingClasses(AppView<?> appView) {
      assert getMissingClassesToBeReported(appView, clazz -> ImmutableSet.of(clazz.getType()))
          .isEmpty();
      return build();
    }

    public MissingClasses reportMissingClasses(
        AppView<?> appView, SynthesizingContextOracle synthesizingContextOracle) {
      Map<DexType, Set<ProgramDerivedContext>> missingClassesToBeReported =
          getMissingClassesToBeReported(appView, synthesizingContextOracle);
      if (!missingClassesToBeReported.isEmpty()) {
        MissingDefinitionsDiagnostic diagnostic = createDiagnostic(missingClassesToBeReported);
        if (appView.options().ignoreMissingClasses) {
          appView.reporter().warning(diagnostic);
        } else {
          throw appView.reporter().fatalError(diagnostic);
        }
      }
      return build();
    }

    private MissingDefinitionsDiagnostic createDiagnostic(
        Map<DexType, Set<ProgramDerivedContext>> missingClassesToBeReported) {
      MissingDefinitionsDiagnosticImpl.Builder diagnosticBuilder =
          MissingDefinitionsDiagnosticImpl.builder();
      missingClassesToBeReported.forEach(
          (missingClass, programDerivedContexts) -> {
            MissingClassInfoImpl.Builder missingClassInfoBuilder =
                MissingClassInfoImpl.builder().setClass(missingClass.asClassReference());
            for (ProgramDerivedContext programDerivedContext : programDerivedContexts) {
              missingClassInfoBuilder.addReferencedFromContext(
                  DefinitionContextUtils.create(programDerivedContext));
            }
            diagnosticBuilder.addMissingDefinitionInfo(missingClassInfoBuilder.build());
          });
      return diagnosticBuilder.build();
    }

    private void rewriteMissingClassContexts(
        AppView<?> appView, SynthesizingContextOracle synthesizingContextOracle) {
      Iterator<Entry<DexType, Set<ProgramDerivedContext>>> newMissingClassesIterator =
          newMissingClasses.entrySet().iterator();
      while (newMissingClassesIterator.hasNext()) {
        Entry<DexType, Set<ProgramDerivedContext>> entry = newMissingClassesIterator.next();
        entry.setValue(
            rewriteMissingClassContextsForSingleMissingClass(
                appView, entry.getValue(), synthesizingContextOracle));
      }
    }

    private static Set<ProgramDerivedContext> rewriteMissingClassContextsForSingleMissingClass(
        AppView<?> appView,
        Set<ProgramDerivedContext> contexts,
        SynthesizingContextOracle synthesizingContextOracle) {
      if (contexts.isEmpty()) {
        // Legacy reporting does not have any contexts.
        return contexts;
      }

      Set<ProgramDerivedContext> rewrittenContexts = newProgramDerivedContextSet();
      for (ProgramDerivedContext context : contexts) {
        if (!context.isProgramContext()) {
          rewrittenContexts.add(context);
          continue;
        }

        DexProgramClass clazz = context.getContext().asProgramDefinition().getContextClass();
        if (!appView.getSyntheticItems().isSyntheticClass(clazz)) {
          rewrittenContexts.add(context);
          continue;
        }

        // Rewrite the synthetic context to its synthesizing contexts.
        Set<DexReference> synthesizingContextReferences =
            appView
                .getSyntheticItems()
                .getSynthesizingContextReferences(clazz, synthesizingContextOracle);
        assert synthesizingContextReferences != null;
        assert !synthesizingContextReferences.isEmpty();
        for (DexReference synthesizingContextReference : synthesizingContextReferences) {
          if (synthesizingContextReference.isDexMethod()) {
            DexProgramClass holder =
                appView
                    .definitionFor(synthesizingContextReference.getContextType())
                    .asProgramClass();
            ProgramMethod synthesizingContext =
                holder.lookupProgramMethod(synthesizingContextReference.asDexMethod());
            assert synthesizingContext != null;
            rewrittenContexts.add(synthesizingContext);
          } else if (synthesizingContextReference.isDexType()) {
            DexProgramClass synthesizingClass =
                appView.definitionFor(synthesizingContextReference.asDexType()).asProgramClass();
            rewrittenContexts.add(synthesizingClass);
          } else {
            assert false
                : "Unexpected synthesizing context: "
                    + synthesizingContextReference.toSourceString();
          }
        }
      }
      return rewrittenContexts;
    }

    private Map<DexType, Set<ProgramDerivedContext>> getMissingClassesToBeReported(
        AppView<?> appView, SynthesizingContextOracle synthesizingContextOracle) {
      // Rewrite synthetic program contexts into their synthesizing contexts, to avoid reporting
      // any synthetic contexts.
      rewriteMissingClassContexts(appView, synthesizingContextOracle);
      Predicate<DexType> allowedMissingClassesPredicate =
          getIsAllowedMissingClassesPredicate(appView);
      Map<DexType, Set<ProgramDerivedContext>> missingClassesToBeReported =
          new IdentityHashMap<>(newMissingClasses.size());
      newMissingClasses.forEach(
          (missingClass, contexts) -> {
            // Don't report "allowed" missing classes (e.g., classes matched by -dontwarn).
            if (allowedMissingClassesPredicate.test(missingClass)) {
              return;
            }

            // Remove all contexts that are matched by a -dontwarn rule (a missing class should not
            // be reported if it os only referenced from contexts that are matched by a -dontwarn).
            contexts.removeIf(
                context ->
                    appView
                        .getDontWarnConfiguration()
                        .matches(context.getContext().getContextType()));

            // If there are any contexts not matched by a -dontwarn rule, then report.
            if (!contexts.isEmpty()) {
              missingClassesToBeReported.put(missingClass, contexts);
            }
          });
      return missingClassesToBeReported;
    }

    private static Predicate<DexType> getIsAllowedMissingClassesPredicate(AppView<?> appView) {
      Set<DexType> allowedMissingClasses = getAllowedMissingClasses(appView.dexItemFactory());
      Predicate<DexType> compilerSynthesizedAllowingMissingClassPredicate =
          getIsCompilerSynthesizedAllowedMissingClassesPredicate(appView);
      DontWarnConfiguration dontWarnConfiguration = appView.getDontWarnConfiguration();
      return type ->
          allowedMissingClasses.contains(type)
              || compilerSynthesizedAllowingMissingClassPredicate.test(type)
              || dontWarnConfiguration.matches(type);
    }

    private static Set<DexType> getAllowedMissingClasses(DexItemFactory dexItemFactory) {
      return ImmutableSet.<DexType>builder()
          .add(
              dexItemFactory.annotationDefault,
              dexItemFactory.annotationMethodParameters,
              dexItemFactory.annotationSourceDebugExtension,
              dexItemFactory.annotationSynthesizedClass,
              dexItemFactory.annotationThrows,
              dexItemFactory.serializedLambdaType,
              dexItemFactory.unsafeType)
          .addAll(dexItemFactory.getJavaConversionTypes())
          .addAll(dexItemFactory.getJ$ConversionTypes())
          .build();
    }

    private static Predicate<DexType> getIsCompilerSynthesizedAllowedMissingClassesPredicate(
        AppView<?> appView) {
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      DexString vivifiedClassNamePrefix = dexItemFactory.createString(DESCRIPTOR_VIVIFIED_PREFIX);
      return type -> {
        DexString descriptor = type.getDescriptor();
        return descriptor.startsWith(vivifiedClassNamePrefix);
      };
    }



    /** Intentionally private, use {@link Builder#reportMissingClasses(AppView)}. */
    private MissingClasses build() {
      // Return the new set of missing classes.
      //
      // We also add newIgnoredMissingClasses to newMissingClasses to be able to assert that we have
      // a closed world after the first round of tree shaking: we should never lookup a class that
      // was not live or missing during the first round of tree shaking.
      // See also AppInfoWithLiveness.definitionFor().
      //
      // Note: At this point, all missing classes in newMissingClasses have already been reported.
      // Thus adding newIgnoredMissingClasses to newMissingClasses will not lead to reports for the
      // classes in newIgnoredMissingClasses.
      return new MissingClasses(
          SetUtils.newIdentityHashSet(
              alreadyMissingClasses, newMissingClasses.keySet(), newIgnoredMissingClasses));
    }

    public boolean wasAlreadyMissing(DexType type) {
      return alreadyMissingClasses.contains(type);
    }
  }
}
