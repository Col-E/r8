// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FieldAssignmentTracker {

  private final AppView<AppInfoWithLiveness> appView;

  // A field access graph with edges from methods to the fields that they access. Edges are removed
  // from the graph as we process methods, such that we can conclude that all field writes have been
  // processed when a field no longer has any incoming edges.
  private final FieldAccessGraph fieldAccessGraph;

  // The set of fields that may store a non-zero value.
  private final Set<DexEncodedField> nonZeroFields = Sets.newConcurrentHashSet();

  FieldAssignmentTracker(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.fieldAccessGraph = new FieldAccessGraph(appView);
  }

  private boolean isAlwaysZero(DexEncodedField field) {
    return !appView.appInfo().isPinned(field.field) && !nonZeroFields.contains(field);
  }

  void acceptClassInitializerDefaultsResult(
      ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    classInitializerDefaultsResult.forEachOptimizedField(
        (field, value) -> {
          if (!value.isDefault(field.field.type)) {
            nonZeroFields.add(field);
          }
        });
  }

  void recordFieldAccess(
      FieldInstruction instruction, DexEncodedField field, DexEncodedMethod context) {
    if (instruction.isFieldPut()) {
      recordFieldPut(field, instruction.value(), context);
    }
  }

  private void recordFieldPut(DexEncodedField field, Value value, DexEncodedMethod context) {
    assert verifyValueIsConsistentWithFieldOptimizationInfo(
        value, field.getOptimizationInfo(), context);
    if (!value.isZero()) {
      nonZeroFields.add(field);
    }
  }

  private void recordAllFieldPutsProcessed(DexEncodedField field, OptimizationFeedback feedback) {
    if (isAlwaysZero(field)) {
      feedback.recordFieldHasAbstractValue(
          field, appView, appView.abstractValueFactory().createSingleNumberValue(0));
    }
  }

  public void waveDone(Collection<DexEncodedMethod> wave, OptimizationFeedback feedback) {
    for (DexEncodedMethod method : wave) {
      fieldAccessGraph.markProcessed(method, field -> recordAllFieldPutsProcessed(field, feedback));
    }
  }

  private boolean verifyValueIsConsistentWithFieldOptimizationInfo(
      Value value, FieldOptimizationInfo optimizationInfo, DexEncodedMethod context) {
    AbstractValue abstractValue = optimizationInfo.getAbstractValue();
    if (abstractValue.isUnknown()) {
      return true;
    }
    assert abstractValue == value.getAbstractValue(appView, context.method.holder);
    return true;
  }

  static class FieldAccessGraph {

    // The fields written by each method.
    private final Map<DexEncodedMethod, List<DexEncodedField>> fieldWrites =
        new IdentityHashMap<>();

    // The number of writes that have not yet been processed per field.
    private final Reference2IntMap<DexEncodedField> pendingFieldWrites =
        new Reference2IntOpenHashMap<>();

    FieldAccessGraph(AppView<AppInfoWithLiveness> appView) {
      FieldAccessInfoCollection<?> fieldAccessInfoCollection =
          appView.appInfo().getFieldAccessInfoCollection();
      fieldAccessInfoCollection.flattenAccessContexts();
      fieldAccessInfoCollection.forEach(
          info -> {
            DexEncodedField field = appView.appInfo().resolveField(info.getField());
            if (field == null) {
              assert false;
              return;
            }
            if (!info.hasReflectiveAccess()) {
              info.forEachWriteContext(
                  context ->
                      fieldWrites.computeIfAbsent(context, ignore -> new ArrayList<>()).add(field));
              pendingFieldWrites.put(field, info.getNumberOfWriteContexts());
            }
          });
    }

    void markProcessed(DexEncodedMethod method, Consumer<DexEncodedField> allWritesSeenConsumer) {
      List<DexEncodedField> fieldWritesInMethod = fieldWrites.get(method);
      if (fieldWritesInMethod != null) {
        for (DexEncodedField field : fieldWritesInMethod) {
          int numberOfPendingFieldWrites = pendingFieldWrites.removeInt(field) - 1;
          if (numberOfPendingFieldWrites > 0) {
            pendingFieldWrites.put(field, numberOfPendingFieldWrites);
          } else {
            allWritesSeenConsumer.accept(field);
          }
        }
      }
    }
  }
}
