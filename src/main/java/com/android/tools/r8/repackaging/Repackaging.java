// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramPackage;
import com.android.tools.r8.graph.ProgramPackageCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.Timing;
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

  public Repackaging(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.proguardConfiguration = appView.options().getProguardConfiguration();
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Repackage classes");
    run(executorService);
    timing.end();
  }

  private void run(ExecutorService executorService) throws ExecutionException {
    if (proguardConfiguration.getPackageObfuscationMode().isNone()) {
      return;
    }

    // For each package, find the set of classes that can be repackaged, and move them to the
    // desired namespace.
    ProgramPackageCollection packages = ProgramPackageCollection.create(appView);
    for (ProgramPackage pkg : packages) {
      Iterable<DexProgramClass> classesToRepackage =
          computeClassesToRepackage(pkg, executorService);
      // TODO(b/165783399): Move each class in `classesToRepackage`.
      // TODO(b/165783399): Investigate if repackaging can lead to different dynamic dispatch. See,
      //  for example, CrossPackageInvokeSuperToPackagePrivateMethodTest.
    }
  }

  private Iterable<DexProgramClass> computeClassesToRepackage(
      ProgramPackage pkg, ExecutorService executorService) throws ExecutionException {
    // If all classes can be minified, then we are free to move all the classes to a new package.
    if (isMinificationAllowedForAllClasses(pkg)) {
      return pkg;
    }

    // Otherwise, only a subset of classes in the package can be repackaged, and we need to ensure
    // that package-private accesses continue to work after the split.
    RepackagingConstraintGraph constraintGraph = new RepackagingConstraintGraph(appView, pkg);
    boolean hasPackagePrivateOrProtectedItem = constraintGraph.initializeGraph();
    if (!hasPackagePrivateOrProtectedItem) {
      return pkg;
    }
    constraintGraph.populateConstraints(executorService);
    return constraintGraph.computeClassesToRepackage();
  }

  private boolean isMinificationAllowedForAllClasses(ProgramPackage pkg) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    for (DexProgramClass clazz : pkg) {
      if (!appInfo.isMinificationAllowed(clazz.getType())) {
        return false;
      }
    }
    return true;
  }
}
