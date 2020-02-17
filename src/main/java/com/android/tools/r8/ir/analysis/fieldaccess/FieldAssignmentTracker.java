// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.BottomValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldArgumentInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FieldAssignmentTracker {

  private final AppView<AppInfoWithLiveness> appView;

  // A field access graph with edges from methods to the fields that they access. Edges are removed
  // from the graph as we process methods, such that we can conclude that all field writes have been
  // processed when a field no longer has any incoming edges.
  private final FieldAccessGraph fieldAccessGraph;

  // An object allocation graph with edges from methods to the classes they instantiate. Edges are
  // removed from the graph as we process methods, such that we can conclude that all allocation
  // sites have been seen when a class no longer has any incoming edges.
  private final ObjectAllocationGraph objectAllocationGraph;

  // The set of fields that may store a non-zero value.
  private final Set<DexEncodedField> nonZeroFields = Sets.newConcurrentHashSet();

  private final Map<DexProgramClass, Map<DexEncodedField, AbstractValue>>
      abstractInstanceFieldValues = new ConcurrentHashMap<>();

  FieldAssignmentTracker(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.fieldAccessGraph = new FieldAccessGraph(appView);
    this.objectAllocationGraph = new ObjectAllocationGraph(appView);
    initializeAbstractInstanceFieldValues();
  }

  /**
   * For each class with known allocation sites, adds a mapping from clazz -> instance field ->
   * bottom.
   *
   * <p>If an entry (clazz, instance field) is missing in {@link #abstractInstanceFieldValues}, it
   * is interpreted as if we known nothing about the value of the field.
   */
  private void initializeAbstractInstanceFieldValues() {
    ObjectAllocationInfoCollection objectAllocationInfos =
        appView.appInfo().getObjectAllocationInfoCollection();
    objectAllocationInfos.forEachClassWithKnownAllocationSites(
        (clazz, allocationSites) -> {
          if (appView.appInfo().isInstantiatedIndirectly(clazz)) {
            // TODO(b/147652121): Handle classes that are instantiated indirectly.
            return;
          }
          List<DexEncodedField> instanceFields = clazz.instanceFields();
          if (instanceFields.isEmpty()) {
            // No instance fields to track.
            return;
          }
          Map<DexEncodedField, AbstractValue> abstractInstanceFieldValuesForClass =
              new IdentityHashMap<>();
          for (DexEncodedField field : clazz.instanceFields()) {
            abstractInstanceFieldValuesForClass.put(field, BottomValue.getInstance());
          }
          abstractInstanceFieldValues.put(clazz, abstractInstanceFieldValuesForClass);
        });
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

  void recordAllocationSite(
      NewInstance instruction, DexProgramClass clazz, DexEncodedMethod context) {
    Map<DexEncodedField, AbstractValue> abstractInstanceFieldValuesForClass =
        abstractInstanceFieldValues.get(clazz);
    if (abstractInstanceFieldValuesForClass == null) {
      // We are not tracking the value of any of clazz' instance fields.
      return;
    }

    InvokeDirect invoke = instruction.getUniqueConstructorInvoke(appView.dexItemFactory());
    if (invoke == null) {
      // We just lost track.
      abstractInstanceFieldValues.remove(clazz);
      return;
    }

    DexEncodedMethod singleTarget = invoke.lookupSingleTarget(appView, context.method.holder);
    if (singleTarget == null) {
      // We just lost track.
      abstractInstanceFieldValues.remove(clazz);
      return;
    }

    InstanceFieldInitializationInfoCollection initializationInfoCollection =
        singleTarget.getOptimizationInfo().getInstanceInitializerInfo().fieldInitializationInfos();

    // Synchronize on the lattice element (abstractInstanceFieldValuesForClass) in case we process
    // another allocation site of `clazz` concurrently.
    synchronized (abstractInstanceFieldValuesForClass) {
      Iterator<Map.Entry<DexEncodedField, AbstractValue>> iterator =
          abstractInstanceFieldValuesForClass.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<DexEncodedField, AbstractValue> entry = iterator.next();
        DexEncodedField field = entry.getKey();
        InstanceFieldInitializationInfo initializationInfo =
            initializationInfoCollection.get(field);
        if (initializationInfo.isArgumentInitializationInfo()) {
          InstanceFieldArgumentInitializationInfo argumentInitializationInfo =
              initializationInfo.asArgumentInitializationInfo();
          Value argument = invoke.arguments().get(argumentInitializationInfo.getArgumentIndex());
          AbstractValue abstractValue =
              argument.getAbstractValue(appView, context.method.holder).join(entry.getValue());
          assert !abstractValue.isBottom();
          if (!abstractValue.isUnknown()) {
            entry.setValue(abstractValue);
            continue;
          }
        } else {
          assert initializationInfo.isUnknown();
        }

        // We just lost track for this field.
        iterator.remove();
      }
    }
  }

  private void recordAllFieldPutsProcessed(
      DexEncodedField field, OptimizationFeedbackDelayed feedback) {
    if (isAlwaysZero(field)) {
      feedback.recordFieldHasAbstractValue(
          field, appView, appView.abstractValueFactory().createSingleNumberValue(0));
    }
  }

  private void recordAllAllocationsSitesProcessed(
      DexProgramClass clazz, OptimizationFeedbackDelayed feedback) {
    Map<DexEncodedField, AbstractValue> abstractInstanceFieldValuesForClass =
        abstractInstanceFieldValues.get(clazz);
    if (abstractInstanceFieldValuesForClass == null) {
      return;
    }

    for (DexEncodedField field : clazz.instanceFields()) {
      AbstractValue abstractValue =
          abstractInstanceFieldValuesForClass.getOrDefault(field, UnknownValue.getInstance());
      if (abstractValue.isBottom()) {
        // TODO(b/149454532): Record that the type is not instantiated.
        break;
      }
      if (abstractValue.isUnknown()) {
        continue;
      }
      feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
    }
  }

  public void waveDone(Collection<DexEncodedMethod> wave, OptimizationFeedbackDelayed feedback) {
    for (DexEncodedMethod method : wave) {
      fieldAccessGraph.markProcessed(method, field -> recordAllFieldPutsProcessed(field, feedback));
      objectAllocationGraph.markProcessed(
          method, clazz -> recordAllAllocationsSitesProcessed(clazz, feedback));
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

  static class ObjectAllocationGraph {

    // The classes instantiated by each method.
    private final Map<DexEncodedMethod, List<DexProgramClass>> objectAllocations =
        new IdentityHashMap<>();

    // The number of allocation sites that have not yet been processed per class.
    private final Reference2IntMap<DexProgramClass> pendingObjectAllocations =
        new Reference2IntOpenHashMap<>();

    ObjectAllocationGraph(AppView<AppInfoWithLiveness> appView) {
      ObjectAllocationInfoCollection objectAllocationInfos =
          appView.appInfo().getObjectAllocationInfoCollection();
      objectAllocationInfos.forEachClassWithKnownAllocationSites(
          (clazz, contexts) -> {
            for (DexEncodedMethod context : contexts) {
              objectAllocations.computeIfAbsent(context, ignore -> new ArrayList<>()).add(clazz);
            }
            pendingObjectAllocations.put(clazz, contexts.size());
          });
    }

    void markProcessed(
        DexEncodedMethod method, Consumer<DexProgramClass> allAllocationsSitesSeenConsumer) {
      List<DexProgramClass> allocationSitesInMethod = objectAllocations.get(method);
      if (allocationSitesInMethod != null) {
        for (DexProgramClass type : allocationSitesInMethod) {
          int numberOfPendingAllocationSites = pendingObjectAllocations.removeInt(type) - 1;
          if (numberOfPendingAllocationSites > 0) {
            pendingObjectAllocations.put(type, numberOfPendingAllocationSites);
          } else {
            allAllocationsSitesSeenConsumer.accept(type);
          }
        }
      }
    }
  }
}
