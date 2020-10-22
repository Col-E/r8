// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.BottomValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldArgumentInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
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
    this.fieldAccessGraph = new FieldAccessGraph();
    this.objectAllocationGraph = new ObjectAllocationGraph();
  }

  public void initialize() {
    fieldAccessGraph.initialize(appView);
    objectAllocationGraph.initialize(appView);
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
    FieldAccessInfoCollection<?> fieldAccessInfos =
        appView.appInfo().getFieldAccessInfoCollection();
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
            FieldAccessInfo fieldAccessInfo = fieldAccessInfos.get(field.field);
            if (fieldAccessInfo != null && !fieldAccessInfo.hasReflectiveAccess()) {
              abstractInstanceFieldValuesForClass.put(field, BottomValue.getInstance());
            }
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
      FieldInstruction instruction, DexEncodedField field, ProgramMethod context) {
    if (instruction.isFieldPut()) {
      recordFieldPut(field, instruction.value(), context);
    }
  }

  private void recordFieldPut(DexEncodedField field, Value value, ProgramMethod context) {
    if (!value.isZero()) {
      nonZeroFields.add(field);
    }
  }

  void recordAllocationSite(NewInstance instruction, DexProgramClass clazz, ProgramMethod context) {
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

    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
    if (singleTarget == null) {
      // We just lost track.
      abstractInstanceFieldValues.remove(clazz);
      return;
    }

    InstanceFieldInitializationInfoCollection initializationInfoCollection =
        singleTarget
            .getDefinition()
            .getOptimizationInfo()
            .getInstanceInitializerInfo()
            .fieldInitializationInfos();

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
              entry.getValue().join(argument.getAbstractValue(appView, context));
          assert !abstractValue.isBottom();
          if (!abstractValue.isUnknown()) {
            entry.setValue(abstractValue);
            continue;
          }
        } else if (initializationInfo.isSingleValue()) {
          SingleValue singleValueInitializationInfo = initializationInfo.asSingleValue();
          AbstractValue abstractValue = entry.getValue().join(singleValueInitializationInfo);
          assert !abstractValue.isBottom();
          if (!abstractValue.isUnknown()) {
            entry.setValue(abstractValue);
            continue;
          }
        } else if (initializationInfo.isTypeInitializationInfo()) {
          // TODO(b/149732532): Not handled, for now.
        } else {
          assert initializationInfo.isUnknown();
        }

        // We just lost track for this field.
        iterator.remove();
      }
    }
  }

  private void recordAllFieldPutsProcessed(
      DexEncodedField field, ProgramMethod context, OptimizationFeedbackDelayed feedback) {
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionForHolder(field, context));
    if (clazz == null) {
      assert false;
      return;
    }

    if (isAlwaysZero(field)) {
      feedback.recordFieldHasAbstractValue(
          field, appView, appView.abstractValueFactory().createSingleNumberValue(0));
    }

    if (!field.isStatic()) {
      recordAllInstanceFieldPutsProcessed(clazz, field, feedback);
    }
  }

  private void recordAllInstanceFieldPutsProcessed(
      DexProgramClass clazz, DexEncodedField field, OptimizationFeedbackDelayed feedback) {
    if (appView.appInfo().isInstanceFieldWrittenOnlyInInstanceInitializers(field)) {
      AbstractValue abstractValue = BottomValue.getInstance();
      for (DexEncodedMethod method : clazz.directMethods(DexEncodedMethod::isInstanceInitializer)) {
        InstanceFieldInitializationInfo fieldInitializationInfo =
            method
                .getOptimizationInfo()
                .getInstanceInitializerInfo()
                .fieldInitializationInfos()
                .get(field);
        if (fieldInitializationInfo.isSingleValue()) {
          abstractValue = abstractValue.join(fieldInitializationInfo.asSingleValue());
          if (abstractValue.isUnknown()) {
            break;
          }
        } else if (fieldInitializationInfo.isTypeInitializationInfo()) {
          // TODO(b/149732532): Not handled, for now.
          abstractValue = UnknownValue.getInstance();
          break;
        } else {
          assert fieldInitializationInfo.isArgumentInitializationInfo()
              || fieldInitializationInfo.isUnknown();
          abstractValue = UnknownValue.getInstance();
          break;
        }
      }

      assert !abstractValue.isBottom();

      if (!abstractValue.isUnknown()) {
        feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
      }
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
        feedback.modifyAppInfoWithLiveness(modifier -> modifier.removeInstantiatedType(clazz));
        break;
      }
      if (abstractValue.isUnknown()) {
        continue;
      }
      feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
    }
  }

  public void waveDone(ProgramMethodSet wave, OptimizationFeedbackDelayed feedback) {
    // This relies on the instance initializer info in the method optimization feedback. It is
    // therefore important that the optimization info has been flushed in advance.
    assert feedback.noUpdatesLeft();
    for (ProgramMethod method : wave) {
      fieldAccessGraph.markProcessed(
          method, field -> recordAllFieldPutsProcessed(field, method, feedback));
      objectAllocationGraph.markProcessed(
          method, clazz -> recordAllAllocationsSitesProcessed(clazz, feedback));
    }
    feedback.refineAppInfoWithLiveness(appView.appInfo().withLiveness());
    feedback.updateVisibleOptimizationInfo();
  }

  static class FieldAccessGraph {

    // The fields written by each method.
    private final Map<DexEncodedMethod, List<DexEncodedField>> fieldWrites =
        new IdentityHashMap<>();

    // The number of writes that have not yet been processed per field.
    private final Reference2IntMap<DexEncodedField> pendingFieldWrites =
        new Reference2IntOpenHashMap<>();

    FieldAccessGraph() {}

    public void initialize(AppView<AppInfoWithLiveness> appView) {
      FieldAccessInfoCollection<?> fieldAccessInfoCollection =
          appView.appInfo().getFieldAccessInfoCollection();
      fieldAccessInfoCollection.forEach(
          info -> {
            DexEncodedField field =
                appView.appInfo().resolveField(info.getField()).getResolvedField();
            if (field == null) {
              return;
            }
            if (!info.hasReflectiveAccess() && !info.isWrittenFromMethodHandle()) {
              info.forEachWriteContext(
                  context ->
                      fieldWrites
                          .computeIfAbsent(context.getDefinition(), ignore -> new ArrayList<>())
                          .add(field));
              pendingFieldWrites.put(field, info.getNumberOfWriteContexts());
            }
          });
    }

    void markProcessed(ProgramMethod method, Consumer<DexEncodedField> allWritesSeenConsumer) {
      List<DexEncodedField> fieldWritesInMethod = fieldWrites.get(method.getDefinition());
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

    ObjectAllocationGraph() {}

    public void initialize(AppView<AppInfoWithLiveness> appView) {
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
        ProgramMethod method, Consumer<DexProgramClass> allAllocationsSitesSeenConsumer) {
      List<DexProgramClass> allocationSitesInMethod = objectAllocations.get(method.getDefinition());
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
