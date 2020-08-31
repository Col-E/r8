// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ProgramPackage;
import com.android.tools.r8.graph.ProgramPackageCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import java.util.IdentityHashMap;
import java.util.Map;
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
  private final DexItemFactory dexItemFactory;
  private final ProguardConfiguration proguardConfiguration;

  public Repackaging(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.proguardConfiguration = appView.options().getProguardConfiguration();
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

    // For each package, find the set of classes that can be repackaged, and move them to the
    // desired namespace.
    Map<DexType, DexType> mappings = new IdentityHashMap<>();
    for (ProgramPackage pkg : ProgramPackageCollection.create(appView)) {
      Iterable<DexProgramClass> classesToRepackage =
          computeClassesToRepackage(pkg, executorService);
      String newPackageDescriptor = getNewPackageDescriptor(pkg);
      for (DexProgramClass classToRepackage : classesToRepackage) {
        // TODO(b/165783399): Handle class collisions when different packages are repackaged into
        //  the same package.
        DexType newType =
            classToRepackage.getType().replacePackage(newPackageDescriptor, dexItemFactory);
        mappings.put(classToRepackage.getType(), newType);
      }
      // TODO(b/165783399): Investigate if repackaging can lead to different dynamic dispatch. See,
      //  for example, CrossPackageInvokeSuperToPackagePrivateMethodTest.
    }
    if (mappings.isEmpty()) {
      return null;
    }
    return new RepackagingTreeFixer(appBuilder, appView, mappings).run();
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

  private String getNewPackageDescriptor(ProgramPackage pkg) {
    String newPackageDescriptor =
        StringUtils.replaceAll(proguardConfiguration.getPackagePrefix(), ".", "/");
    if (proguardConfiguration.getPackageObfuscationMode().isFlattenPackageHierarchy()) {
      // TODO(b/165783399): Handle collisions among package names.
      newPackageDescriptor += "/" + pkg.getLastPackageName();
    }
    return newPackageDescriptor;
  }
}
