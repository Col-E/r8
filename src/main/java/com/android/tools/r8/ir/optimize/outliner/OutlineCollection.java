// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.optimize.outliner.OutlinerImpl.Outline;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// Maps each method to the outline candidates found in the method.
public class OutlineCollection {

  private final Map<Outline, Outline> canonicalization = new ConcurrentHashMap<>();

  private GraphLens appliedGraphLens;
  private Map<DexMethod, List<Outline>> outlines = new ConcurrentHashMap<>();

  public OutlineCollection(GraphLens graphLensForPrimaryOptimizationPass) {
    this.appliedGraphLens = graphLensForPrimaryOptimizationPass;
  }

  public void remove(AppView<AppInfoWithLiveness> appView, ProgramMethod method) {
    assert appView.graphLens() == appliedGraphLens;
    outlines.remove(method.getReference());
  }

  public void set(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, List<Outline> outlinesForMethod) {
    assert appView.graphLens() == appliedGraphLens;
    if (outlinesForMethod.isEmpty()) {
      // If we are reprocessing the method, and found no instructions sequences eligible for
      // outlining, then clear the outline candidates for the given method.
      outlines.remove(method.getReference());
    } else {
      outlines.put(method.getReference(), canonicalize(outlinesForMethod));
    }
  }

  public void rewriteWithLens(GraphLens currentGraphLens) {
    if (currentGraphLens == appliedGraphLens) {
      return;
    }

    Map<DexMethod, List<Outline>> rewrittenOutlines = new ConcurrentHashMap<>(outlines.size());
    outlines.forEach(
        (method, outlinesForMethod) -> {
          DexMethod rewrittenMethod =
              currentGraphLens.getRenamedMethodSignature(method, appliedGraphLens);
          assert !rewrittenOutlines.containsKey(rewrittenMethod);
          List<Outline> rewrittenOutlinesForMethod =
              rewriteOutlinesWithLens(outlinesForMethod, currentGraphLens);
          if (!rewrittenOutlinesForMethod.isEmpty()) {
            rewrittenOutlines.put(rewrittenMethod, rewrittenOutlinesForMethod);
          }
        });
    outlines = rewrittenOutlines;

    // Record that this collection is now rewritten up until the point of the given graph lens.
    appliedGraphLens = currentGraphLens;
  }

  private List<Outline> rewriteOutlinesWithLens(
      List<Outline> outlines, GraphLens currentGraphLens) {
    assert currentGraphLens != appliedGraphLens;
    return ListUtils.mapOrElse(outlines, outline -> outline.rewrittenWithLens(currentGraphLens));
  }

  public ProgramMethodSet computeMethodsSubjectToOutlining(AppView<AppInfoWithLiveness> appView) {
    ProgramMethodSet result = ProgramMethodSet.create();
    Map<Outline, List<ProgramMethod>> methodsPerOutline = computeMethodsPerOutline(appView);
    for (List<ProgramMethod> methodsWithSameOutline : methodsPerOutline.values()) {
      if (methodsWithSameOutline.size() >= appView.options().outline.threshold) {
        result.addAll(methodsWithSameOutline);
      }
    }
    return result;
  }

  private Map<Outline, List<ProgramMethod>> computeMethodsPerOutline(
      AppView<AppInfoWithLiveness> appView) {
    Map<Outline, List<ProgramMethod>> methodsPerOutline = new HashMap<>();
    outlines.forEach(
        (reference, outlinesForMethod) -> {
          DexMethod rewrittenReference =
              appView.graphLens().getRenamedMethodSignature(reference, appliedGraphLens);
          DexProgramClass holder =
              DexProgramClass.asProgramClassOrNull(
                  appView.definitionFor(rewrittenReference.getHolderType()));
          ProgramMethod method = rewrittenReference.lookupOnProgramClass(holder);
          if (method == null) {
            assert false;
            return;
          }
          assert !method.getOptimizationInfo().hasBeenInlinedIntoSingleCallSite();
          for (Outline outline : outlinesForMethod) {
            methodsPerOutline.computeIfAbsent(outline, ignoreKey(ArrayList::new)).add(method);
          }
        });
    return methodsPerOutline;
  }

  private List<Outline> canonicalize(List<Outline> outlines) {
    List<Outline> canonicalizedOutlines = new ArrayList<>(outlines.size());
    for (Outline outline : outlines) {
      canonicalizedOutlines.add(canonicalize(outline));
    }
    return canonicalizedOutlines;
  }

  private Outline canonicalize(Outline outline) {
    return canonicalization.computeIfAbsent(outline, Function.identity());
  }
}
