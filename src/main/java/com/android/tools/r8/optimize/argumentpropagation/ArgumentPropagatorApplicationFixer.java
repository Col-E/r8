// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Takes as input a mapping from old method signatures to new method signatures (with parameters
 * removed), and rewrites all method definitions in the application to their new method signatures.
 */
public class ArgumentPropagatorApplicationFixer {

  private final AppView<AppInfoWithLiveness> appView;
  private final ArgumentPropagatorGraphLens graphLens;

  public ArgumentPropagatorApplicationFixer(
      AppView<AppInfoWithLiveness> appView, ArgumentPropagatorGraphLens graphLens) {
    this.appView = appView;
    this.graphLens = graphLens;
  }

  public void fixupApplication(ExecutorService executorService) throws ExecutionException {
    // If the graph lens is null, argument propagation did not lead to any parameter removals. In
    // this case there is no needed to fixup the program.
    if (graphLens == null) {
      return;
    }

    // TODO(b/190154391): Do not naively visit all classes, when only few require changes.
    ThreadUtils.processItems(appView.appInfo().classes(), this::fixupClass, executorService);
    appView.setGraphLens(graphLens);
  }

  private void fixupClass(DexProgramClass clazz) {
    MethodCollection methodCollection = clazz.getMethodCollection();
    methodCollection.replaceMethods(
        method -> {
          DexMethod methodReferenceBeforeParameterRemoval = method.getReference();
          DexMethod methodReferenceAfterParameterRemoval =
              graphLens.internalGetNextMethodSignature(methodReferenceBeforeParameterRemoval);
          if (methodReferenceAfterParameterRemoval == methodReferenceBeforeParameterRemoval) {
            return method;
          }

          return method.toTypeSubstitutedMethod(
              methodReferenceAfterParameterRemoval,
              builder -> {
                // TODO(b/190154391): fixup parameter annotations, if any.
              });
        });
  }
}
