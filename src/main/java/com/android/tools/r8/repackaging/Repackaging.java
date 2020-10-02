// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.INNER_CLASS_SEPARATOR;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.ProgramPackage;
import com.android.tools.r8.graph.ProgramPackageCollection;
import com.android.tools.r8.graph.SortedProgramPackageCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.HashSet;
import java.util.Iterator;
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

  public RepackagingLens run(
      DirectMappedDexApplication.Builder appBuilder, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Repackage classes");
    RepackagingLens lens = run(appBuilder, executorService);
    timing.end();
    return lens;
  }

  private RepackagingLens run(
      DirectMappedDexApplication.Builder appBuilder, ExecutorService executorService)
      throws ExecutionException {
    if (proguardConfiguration.getPackageObfuscationMode().isNone()) {
      return null;
    }

    BiMap<DexType, DexType> mappings = HashBiMap.create();
    Set<String> seenPackageDescriptors = new HashSet<>();
    ProgramPackageCollection packages =
        SortedProgramPackageCollection.createWithAllProgramClasses(appView);
    processPackagesInDesiredLocation(packages, mappings, seenPackageDescriptors);
    processRemainingPackages(packages, mappings, seenPackageDescriptors, executorService);
    mappings.entrySet().removeIf(entry -> entry.getKey() == entry.getValue());
    if (mappings.isEmpty()) {
      return null;
    }
    return new RepackagingTreeFixer(appBuilder, appView, mappings).run();
  }

  private void processPackagesInDesiredLocation(
      ProgramPackageCollection packages,
      BiMap<DexType, DexType> mappings,
      Set<String> seenPackageDescriptors) {
    // For each package that is already in the desired location, record all the classes from the
    // package in the mapping for collision detection.
    Iterator<ProgramPackage> iterator = packages.iterator();
    while (iterator.hasNext()) {
      ProgramPackage pkg = iterator.next();
      String newPackageDescriptor =
          repackagingConfiguration.getNewPackageDescriptor(pkg, seenPackageDescriptors);
      if (pkg.getPackageDescriptor().equals(newPackageDescriptor)) {
        for (DexProgramClass alreadyRepackagedClass : pkg) {
          if (!appView.appInfo().isRepackagingAllowed(alreadyRepackagedClass)) {
            mappings.put(alreadyRepackagedClass.getType(), alreadyRepackagedClass.getType());
          }
        }
        for (DexProgramClass alreadyRepackagedClass : pkg) {
          processClass(alreadyRepackagedClass, pkg, newPackageDescriptor, mappings);
        }
        seenPackageDescriptors.add(newPackageDescriptor);
        iterator.remove();
      }
    }
  }

  private void processRemainingPackages(
      ProgramPackageCollection packages,
      BiMap<DexType, DexType> mappings,
      Set<String> seenPackageDescriptors,
      ExecutorService executorService)
      throws ExecutionException {
    // For each package, find the set of classes that can be repackaged, and move them to the
    // desired package.
    for (ProgramPackage pkg : packages) {
      // Already processed packages should have been removed.
      String newPackageDescriptor =
          repackagingConfiguration.getNewPackageDescriptor(pkg, seenPackageDescriptors);
      assert !pkg.getPackageDescriptor().equals(newPackageDescriptor);

      Iterable<DexProgramClass> classesToRepackage =
          computeClassesToRepackage(pkg, executorService);
      for (DexProgramClass classToRepackage : classesToRepackage) {
        processClass(classToRepackage, pkg, newPackageDescriptor, mappings);
      }

      seenPackageDescriptors.add(newPackageDescriptor);
      // TODO(b/165783399): Investigate if repackaging can lead to different dynamic dispatch. See,
      //  for example, CrossPackageInvokeSuperToPackagePrivateMethodTest.
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
    InnerClassAttribute innerClassAttribute = classToRepackage.getInnerClassAttributeForThisClass();
    DexProgramClass outerClass = null;
    if (innerClassAttribute != null && innerClassAttribute.getOuter() != null) {
      outerClass = asProgramClassOrNull(appView.definitionFor(innerClassAttribute.getOuter()));
      if (outerClass != null) {
        if (pkg.contains(outerClass)) {
          processClass(outerClass, pkg, newPackageDescriptor, mappings);
        } else {
          outerClass = null;
        }
      }
    }
    mappings.put(
        classToRepackage.getType(),
        repackagingConfiguration.getRepackagedType(
            classToRepackage, outerClass, newPackageDescriptor, mappings));
  }

  private Iterable<DexProgramClass> computeClassesToRepackage(
      ProgramPackage pkg, ExecutorService executorService) throws ExecutionException {
    RepackagingConstraintGraph constraintGraph = new RepackagingConstraintGraph(appView, pkg);
    boolean canRepackageAllClasses = constraintGraph.initializeGraph();
    if (canRepackageAllClasses) {
      return pkg;
    }
    constraintGraph.populateConstraints(executorService);
    return constraintGraph.computeClassesToRepackage();
  }

  public interface RepackagingConfiguration {

    String getNewPackageDescriptor(ProgramPackage pkg, Set<String> seenPackageDescriptors);

    DexType getRepackagedType(
        DexProgramClass clazz,
        DexProgramClass outerClass,
        String newPackageDescriptor,
        BiMap<DexType, DexType> mappings);
  }

  public static class DefaultRepackagingConfiguration implements RepackagingConfiguration {

    private final DexItemFactory dexItemFactory;
    private final ProguardConfiguration proguardConfiguration;

    public DefaultRepackagingConfiguration(
        DexItemFactory dexItemFactory, ProguardConfiguration proguardConfiguration) {
      this.dexItemFactory = dexItemFactory;
      this.proguardConfiguration = proguardConfiguration;
    }

    @Override
    public String getNewPackageDescriptor(ProgramPackage pkg, Set<String> seenPackageDescriptors) {
      String newPackageDescriptor =
          DescriptorUtils.getBinaryNameFromJavaType(proguardConfiguration.getPackagePrefix());
      if (proguardConfiguration.getPackageObfuscationMode().isRepackageClasses()) {
        return newPackageDescriptor;
      }
      if (!newPackageDescriptor.isEmpty()) {
        newPackageDescriptor += DESCRIPTOR_PACKAGE_SEPARATOR;
      }
      newPackageDescriptor += pkg.getLastPackageName();
      String finalPackageDescriptor = newPackageDescriptor;
      for (int i = 1; seenPackageDescriptors.contains(finalPackageDescriptor); i++) {
        finalPackageDescriptor = newPackageDescriptor + INNER_CLASS_SEPARATOR + i;
      }
      return finalPackageDescriptor;
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
          assert false
              : "Unexpected name for inner class: "
                  + clazz.getType().toSourceString()
                  + " (outer class: "
                  + outerClass.getType().toSourceString()
                  + ")";
        }
      }
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
