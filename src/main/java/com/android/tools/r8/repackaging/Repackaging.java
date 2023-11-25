// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.INNER_CLASS_SEPARATOR;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.ProgramPackage;
import com.android.tools.r8.graph.ProgramPackageCollection;
import com.android.tools.r8.graph.SortedProgramPackageCollection;
import com.android.tools.r8.graph.fixup.TreeFixerBase;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.naming.Minifier.MinificationPackageNamingStrategy;
import com.android.tools.r8.repackaging.RepackagingLens.Builder;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Entry-point for supporting the -repackageclasses and -flattenpackagehierarchy directives.
 *
 * <p>This pass moves all classes in the program into a user-specified package. Some classes may not
 * be allowed to be renamed, and thus must remain in the original package.
 *
 * <p>A complication is that there can be (i) references to package-private or protected items that
 * must remain in the package, and (ii) references from methods that must remain in the package to
 * package-private or protected items. To ensure that such references remain valid after
 * repackaging, an analysis is run that finds the minimal set of classes that must remain in the
 * original package due to accessibility constraints.
 */
public class Repackaging {

  private final AppView<AppInfoWithLiveness> appView;
  private final ProguardConfiguration proguardConfiguration;
  private final RepackagingConfiguration repackagingConfiguration;

  public Repackaging(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.proguardConfiguration = appView.options().getProguardConfiguration();
    this.repackagingConfiguration =
        appView.options().testing.repackagingConfigurationFactory.apply(appView);
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Repackage classes");
    DirectMappedDexApplication.Builder appBuilder = appView.appInfo().app().asDirect().builder();
    RepackagingLens lens = repackageClasses(appBuilder, executorService);
    if (lens != null) {
      appView.rewriteWithLensAndApplication(lens, appBuilder.build(), executorService, timing);
      appView.testing().repackagingLensConsumer.accept(appView.dexItemFactory(), lens);
    }
    appView.notifyOptimizationFinishedForTesting();
    timing.end();
  }

  public static boolean verifyIdentityRepackaging(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    // Running the tree fixer with an identity mapping helps ensure that the fixup of items is
    // complete as the rewrite replaces all items regardless of repackaging.
    // The identity mapping should result in no move callbacks being called.
    Collection<DexProgramClass> newProgramClasses =
        new TreeFixerBase(appView) {
          @Override
          public DexType mapClassType(DexType type) {
            return type;
          }

          @Override
          public void recordFieldChange(DexField from, DexField to) {
            assert false;
          }

          @Override
          public void recordMethodChange(DexMethod from, DexMethod to) {
            assert false;
          }

          @Override
          public void recordClassChange(DexType from, DexType to) {
            assert false;
          }
        }.fixupClasses(appView.appInfo().classesWithDeterministicOrder());
    NestedGraphLens emptyRepackagingLens =
        new NestedGraphLens(appView) {
          @Override
          protected boolean isLegitimateToHaveEmptyMappings() {
            return true;
          }

          @Override
          public <T extends DexReference> boolean isSimpleRenaming(T from, T to) {
            return getPrevious().isSimpleRenaming(from, to);
          }
        };
    DirectMappedDexApplication newApplication =
        appView
            .appInfo()
            .app()
            .asDirect()
            .builder()
            .replaceProgramClasses(new ArrayList<>(newProgramClasses))
            .build();
    appView.rewriteWithLensAndApplication(
        emptyRepackagingLens, newApplication, executorService, Timing.empty());
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private RepackagingLens repackageClasses(
      DirectMappedDexApplication.Builder appBuilder, ExecutorService executorService)
      throws ExecutionException {
    if (proguardConfiguration.getPackageObfuscationMode().isNone()) {
      return null;
    }

    BiMap<DexType, DexType> mappings = HashBiMap.create();
    Map<String, String> packageMappings = new HashMap<>();
    Set<String> seenPackageDescriptors = new HashSet<>();
    ProgramPackageCollection packages =
        SortedProgramPackageCollection.createWithAllProgramClasses(appView);
    processPackagesInDesiredLocation(packages, mappings, packageMappings, seenPackageDescriptors);
    processRemainingPackages(
        packages, mappings, packageMappings, seenPackageDescriptors, executorService);
    mappings.entrySet().removeIf(entry -> entry.getKey() == entry.getValue());
    if (mappings.isEmpty()) {
      return null;
    }
    RepackagingLens.Builder lensBuilder = new RepackagingLens.Builder();
    RepackagingTreeFixer repackagingTreeFixer =
        new RepackagingTreeFixer(appView, mappings, lensBuilder);
    List<DexProgramClass> newProgramClasses =
        new ArrayList<>(
            repackagingTreeFixer.fixupClasses(appView.appInfo().classesWithDeterministicOrder()));
    appBuilder.replaceProgramClasses(newProgramClasses);
    RepackagingLens lens = lensBuilder.build(appView, packageMappings);
    new AnnotationFixer(lens, appView.graphLens()).run(appBuilder.getProgramClasses());
    return lens;
  }

  private static class RepackagingTreeFixer extends TreeFixerBase {

    private final BiMap<DexType, DexType> mappings;

    @SuppressWarnings("BadImport")
    private final Builder lensBuilder;

    @SuppressWarnings("BadImport")
    public RepackagingTreeFixer(
        AppView<AppInfoWithLiveness> appView,
        BiMap<DexType, DexType> mappings,
        Builder lensBuilder) {
      super(appView);
      assert mappings != null;
      this.mappings = mappings;
      this.lensBuilder = lensBuilder;
      recordFailedResolutionChanges();
    }

    @Override
    public DexType mapClassType(DexType type) {
      return mappings.getOrDefault(type, type);
    }

    @Override
    public void recordFieldChange(DexField from, DexField to) {
      lensBuilder.recordMove(from, to);
    }

    @Override
    public void recordMethodChange(DexMethod from, DexMethod to) {
      lensBuilder.recordMove(from, to);
    }

    @Override
    public void recordClassChange(DexType from, DexType to) {
      lensBuilder.recordMove(from, to);
    }
  }

  private void processPackagesInDesiredLocation(
      ProgramPackageCollection packages,
      BiMap<DexType, DexType> mappings,
      Map<String, String> packageMappings,
      Set<String> seenPackageDescriptors) {
    // For each package that is already in the desired location, record all the classes from the
    // package in the mapping for collision detection.
    Iterator<ProgramPackage> iterator = packages.iterator();
    while (iterator.hasNext()) {
      ProgramPackage pkg = iterator.next();
      if (repackagingConfiguration.isPackageInTargetLocation(pkg)) {
        for (DexProgramClass alreadyRepackagedClass : pkg) {
          if (!appView.appInfo().isRepackagingAllowed(alreadyRepackagedClass, appView)) {
            mappings.put(alreadyRepackagedClass.getType(), alreadyRepackagedClass.getType());
          }
        }
        for (DexProgramClass alreadyRepackagedClass : pkg) {
          processClass(alreadyRepackagedClass, pkg, pkg.getPackageDescriptor(), mappings);
        }
        packageMappings.put(pkg.getPackageDescriptor(), pkg.getPackageDescriptor());
        seenPackageDescriptors.add(pkg.getPackageDescriptor());
        iterator.remove();
      }
    }
  }

  private void processRemainingPackages(
      ProgramPackageCollection packages,
      BiMap<DexType, DexType> mappings,
      Map<String, String> packageMappings,
      Set<String> seenPackageDescriptors,
      ExecutorService executorService)
      throws ExecutionException {
    // For each package, find the set of classes that can be repackaged, and move them to the
    // desired package. We iterate all packages first to see if any classes are pinned and cannot
    // be moved, to properly reserve their package.
    Map<ProgramPackage, Collection<DexProgramClass>> packagesWithClassesToRepackage =
        new IdentityHashMap<>();
    for (ProgramPackage pkg : packages) {
      Collection<DexProgramClass> classesToRepackage =
          computeClassesToRepackage(pkg, executorService);
      packagesWithClassesToRepackage.put(pkg, classesToRepackage);
      // Reserve the package name to ensure that we are not renaming to a package we cannot move.
      if (classesToRepackage.size() != pkg.classesInPackage().size()) {
        seenPackageDescriptors.add(pkg.getPackageDescriptor());
      }
    }
    for (ProgramPackage pkg : packages) {
      Collection<DexProgramClass> classesToRepackage = packagesWithClassesToRepackage.get(pkg);
      if (classesToRepackage.isEmpty()) {
        continue;
      }
      // Already processed packages should have been removed.
      assert !repackagingConfiguration.isPackageInTargetLocation(pkg);
      String newPackageDescriptor =
          repackagingConfiguration.getNewPackageDescriptor(pkg, seenPackageDescriptors);
      for (DexProgramClass classToRepackage : classesToRepackage) {
        processClass(classToRepackage, pkg, newPackageDescriptor, mappings);
      }
      seenPackageDescriptors.add(newPackageDescriptor);
      // Package remapping is used for adapting resources. If we cannot repackage all classes in
      // a package then we put in the original descriptor to ensure that resources are not
      // rewritten.
      packageMappings.put(
          pkg.getPackageDescriptor(),
          classesToRepackage.size() == pkg.classesInPackage().size()
              ? newPackageDescriptor
              : pkg.getPackageDescriptor());
    }
  }

  private void processClass(
      DexProgramClass classToRepackage,
      ProgramPackage pkg,
      String newPackageDescriptor,
      BiMap<DexType, DexType> mappings) {
    // Check if the class has already been processed.
    if (mappings.containsKey(classToRepackage.getType())) {
      return;
    }

    // Always repackage outer classes first, if any.
    DexProgramClass outerClass = null;
    if (classToRepackage.hasEnclosingMethodAttribute()) {
      DexType enclosingClass = classToRepackage.getEnclosingMethodAttribute().getEnclosingClass();
      if (enclosingClass != null) {
        outerClass = asProgramClassOrNull(appView.definitionFor(enclosingClass));
      }
    }
    if (outerClass == null) {
      InnerClassAttribute innerClassAttribute =
          classToRepackage.getInnerClassAttributeForThisClass();
      if (innerClassAttribute != null && innerClassAttribute.getOuter() != null) {
        outerClass = asProgramClassOrNull(appView.definitionFor(innerClassAttribute.getOuter()));
      }
    }
    if (outerClass != null) {
      if (pkg.contains(outerClass)) {
        processClass(outerClass, pkg, newPackageDescriptor, mappings);
      } else {
        outerClass = null;
      }
    }
    mappings.put(
        classToRepackage.getType(),
        repackagingConfiguration.getRepackagedType(
            classToRepackage, outerClass, newPackageDescriptor, mappings));
  }

  private Collection<DexProgramClass> computeClassesToRepackage(
      ProgramPackage pkg, ExecutorService executorService) throws ExecutionException {
    RepackagingConstraintGraph constraintGraph = new RepackagingConstraintGraph(appView, pkg);
    boolean canRepackageAllClasses = constraintGraph.initializeGraph();
    if (canRepackageAllClasses) {
      return pkg.classesInPackage();
    }
    constraintGraph.populateConstraints(executorService);
    return constraintGraph.computeClassesToRepackage();
  }

  public interface RepackagingConfiguration {

    String getNewPackageDescriptor(ProgramPackage pkg, Set<String> seenPackageDescriptors);

    boolean isPackageInTargetLocation(ProgramPackage pkg);

    DexType getRepackagedType(
        DexProgramClass clazz,
        DexProgramClass outerClass,
        String newPackageDescriptor,
        BiMap<DexType, DexType> mappings);
  }

  public static class DefaultRepackagingConfiguration implements RepackagingConfiguration {

    private final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory dexItemFactory;
    private final InternalOptions options;
    private final ProguardConfiguration proguardConfiguration;
    private final MinificationPackageNamingStrategy packageMinificationStrategy;

    public DefaultRepackagingConfiguration(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
      this.dexItemFactory = appView.dexItemFactory();
      this.options = appView.options();
      this.proguardConfiguration = appView.options().getProguardConfiguration();
      this.packageMinificationStrategy = new MinificationPackageNamingStrategy(appView);
    }

    @Override
    public String getNewPackageDescriptor(ProgramPackage pkg, Set<String> seenPackageDescriptors) {
      String newPackageDescriptor =
          DescriptorUtils.getBinaryNameFromJavaType(proguardConfiguration.getPackagePrefix());
      PackageObfuscationMode packageObfuscationMode =
          proguardConfiguration.getPackageObfuscationMode();
      if (!appView.options().isMinifying()) {
        // Preserve full package name under destination package when not minifying
        // (no matter which package obfuscation mode is used).
        if (newPackageDescriptor.isEmpty()
            || mayHavePinnedPackagePrivateOrProtectedItem(pkg)) {
          return pkg.getPackageDescriptor();
        }
        return newPackageDescriptor + DESCRIPTOR_PACKAGE_SEPARATOR + pkg.getPackageDescriptor();
      } else if (packageObfuscationMode.isRepackageClasses()) {
        return newPackageDescriptor;
      } else if (packageObfuscationMode.isMinification()) {
        // Always keep top-level classes since their packages can never be minified.
        if (pkg.getPackageDescriptor().isEmpty()
            || mayHavePinnedPackagePrivateOrProtectedItem(pkg)) {
          return pkg.getPackageDescriptor();
        }
        // Plain minification do not support using a specified package prefix.
        newPackageDescriptor = "";
      } else {
        assert packageObfuscationMode.isFlattenPackageHierarchy();
        if (!newPackageDescriptor.isEmpty()) {
          newPackageDescriptor += DESCRIPTOR_PACKAGE_SEPARATOR;
        }
      }
      return packageMinificationStrategy.next(
          newPackageDescriptor, seenPackageDescriptors::contains);
    }

    @Override
    public boolean isPackageInTargetLocation(ProgramPackage pkg) {
      String newPackageDescriptor =
          DescriptorUtils.getBinaryNameFromJavaType(proguardConfiguration.getPackagePrefix());
      PackageObfuscationMode packageObfuscationMode =
          proguardConfiguration.getPackageObfuscationMode();
      if (packageObfuscationMode.isRepackageClasses()) {
        return pkg.getPackageDescriptor().equals(newPackageDescriptor);
      } else if (packageObfuscationMode.isMinification()) {
        // Always keep top-level classes since their packages can never be minified.
        return pkg.getPackageDescriptor().isEmpty()
            || mayHavePinnedPackagePrivateOrProtectedItem(pkg);
      } else {
        assert packageObfuscationMode.isFlattenPackageHierarchy();
        // For flatten we will move the package into the package specified by the prefix so we can
        // always minify the last part.
        return false;
      }
    }

    private boolean mayHavePinnedPackagePrivateOrProtectedItem(ProgramPackage pkg) {
      // Go through all package classes and members to see if there is a pinned package-private
      // item, in which case we cannot move it because there could be an access to it from outside
      // the program, which would be rewritten with -applymapping.
      for (DexProgramClass clazz : pkg.classesInPackage()) {
        if (clazz.getAccessFlags().isPackagePrivateOrProtected()
            && !appView.getKeepInfo().getClassInfo(clazz).isShrinkingAllowed(options)) {
          return true;
        }
        for (DexEncodedMember<?, ?> member : clazz.members()) {
          // Skip the class initializer. Even if it is kept, it cannot be invoked, and thus we don't
          // need any special handling to make sure it is always accessible to callers from tests.
          if (member.isDexEncodedMethod() && member.asDexEncodedMethod().isClassInitializer()) {
            continue;
          }
          if (member.getAccessFlags().isPackagePrivateOrProtected()
              && !appView.getKeepInfo().getMemberInfo(member, clazz).isShrinkingAllowed(options)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public DexType getRepackagedType(
        DexProgramClass clazz,
        DexProgramClass outerClass,
        String newPackageDescriptor,
        BiMap<DexType, DexType> mappings) {
      DexType repackagedDexType =
          clazz.getType().replacePackage(newPackageDescriptor, dexItemFactory);
      // Rename the class consistently with its outer class.
      if (outerClass != null) {
        String simpleName = clazz.getType().getSimpleName();
        String outerClassSimpleName = outerClass.getType().getSimpleName();
        if (simpleName.startsWith(outerClassSimpleName + INNER_CLASS_SEPARATOR)) {
          String newSimpleName =
              mappings.get(outerClass.getType()).getSimpleName()
                  + simpleName.substring(outerClassSimpleName.length());
          repackagedDexType = repackagedDexType.withSimpleName(newSimpleName, dexItemFactory);
        } else {
          assert options.disableInnerClassSeparatorValidationWhenRepackaging
              : "Unexpected name for inner class: "
                  + clazz.getType().toSourceString()
                  + " (outer class: "
                  + outerClass.getType().toSourceString()
                  + ")";
        }
      }
      // Ensure that the generated name is unique.
      DexType finalRepackagedDexType = repackagedDexType;
      for (int i = 1; isRepackageTypeUsed(finalRepackagedDexType, mappings); i++) {
        finalRepackagedDexType = repackagedDexType.addSuffix(i + "", dexItemFactory);
      }
      return finalRepackagedDexType;
    }

    private boolean isRepackageTypeUsed(DexType type, BiMap<DexType, DexType> mappings) {
      if (mappings.inverse().containsKey(type)) {
        return true;
      }
      return appView.appInfo().wasPruned(type)
          || appView.appInfo().getMissingClasses().contains(type);
    }
  }

  /** Testing only. */
  public static class SuffixRenamingRepackagingConfiguration implements RepackagingConfiguration {

    private final String classNameSuffix;
    private final DexItemFactory dexItemFactory;

    public SuffixRenamingRepackagingConfiguration(
        String classNameSuffix, DexItemFactory dexItemFactory) {
      this.classNameSuffix = classNameSuffix;
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public String getNewPackageDescriptor(ProgramPackage pkg, Set<String> seenPackageDescriptors) {
      // Don't change the package of classes.
      return pkg.getPackageDescriptor();
    }

    @Override
    public boolean isPackageInTargetLocation(ProgramPackage pkg) {
      return true;
    }

    @Override
    public DexType getRepackagedType(
        DexProgramClass clazz,
        DexProgramClass outerClass,
        String newPackageDescriptor,
        BiMap<DexType, DexType> mappings) {
      DexType repackagedDexType = clazz.getType();
      // Rename the class consistently with its outer class.
      if (outerClass != null) {
        String simpleName = clazz.getType().getSimpleName();
        String outerClassSimpleName = outerClass.getType().getSimpleName();
        if (simpleName.startsWith(outerClassSimpleName + INNER_CLASS_SEPARATOR)) {
          String newSimpleName =
              mappings.get(outerClass.getType()).getSimpleName()
                  + simpleName.substring(outerClassSimpleName.length());
          repackagedDexType = repackagedDexType.withSimpleName(newSimpleName, dexItemFactory);
        }
      }
      // Append the class name suffix to all classes.
      repackagedDexType = repackagedDexType.addSuffix(classNameSuffix, dexItemFactory);
      // Ensure that the generated name is unique.
      DexType finalRepackagedDexType = repackagedDexType;
      for (int i = 1; mappings.inverse().containsKey(finalRepackagedDexType); i++) {
        finalRepackagedDexType =
            repackagedDexType.addSuffix(
                Character.toString(INNER_CLASS_SEPARATOR) + i, dexItemFactory);
      }
      return finalRepackagedDexType;
    }
  }
}
