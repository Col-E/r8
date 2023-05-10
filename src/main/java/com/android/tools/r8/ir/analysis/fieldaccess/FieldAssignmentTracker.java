// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerUtils;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteArrayTypeFieldState;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcreteClassTypeFieldState;
import com.android.tools.r8.ir.analysis.fieldaccess.state.ConcretePrimitiveTypeFieldState;
import com.android.tools.r8.ir.analysis.fieldaccess.state.FieldState;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.BottomValue;
import com.android.tools.r8.ir.analysis.value.NonConstantNumberValue;
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
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FieldAssignmentTracker {

  private final AbstractValueFactory abstractValueFactory;
  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;

  // A field access graph with edges from methods to the fields that they access. Edges are removed
  // from the graph as we process methods, such that we can conclude that all field writes have been
  // processed when a field no longer has any incoming edges.
  private final FieldAccessGraph fieldAccessGraph;

  // An object allocation graph with edges from methods to the classes they instantiate. Edges are
  // removed from the graph as we process methods, such that we can conclude that all allocation
  // sites have been seen when a class no longer has any incoming edges.
  private final ObjectAllocationGraph objectAllocationGraph;

  // Information about the fields in the program. If a field is not a key in the map then no writes
  // has been seen to the field.
  private final Map<DexEncodedField, FieldState> fieldStates = new ConcurrentHashMap<>();

  private final Map<DexProgramClass, Map<DexEncodedField, AbstractValue>>
      abstractInstanceFieldValues = new ConcurrentHashMap<>();

  FieldAssignmentTracker(AppView<AppInfoWithLiveness> appView) {
    this.abstractValueFactory = appView.abstractValueFactory();
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
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
            FieldAccessInfo fieldAccessInfo = fieldAccessInfos.get(field.getReference());
            if (fieldAccessInfo != null && !fieldAccessInfo.hasReflectiveAccess()) {
              abstractInstanceFieldValuesForClass.put(field, BottomValue.getInstance());
            }
          }
          abstractInstanceFieldValues.put(clazz, abstractInstanceFieldValuesForClass);
        });
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramField(
          field -> {
            FieldAccessInfo accessInfo = fieldAccessInfos.get(field.getReference());
            KeepFieldInfo keepInfo = appView.getKeepInfo(field);
            if (keepInfo.isPinned(appView.options())
                || (accessInfo != null && accessInfo.isWrittenFromMethodHandle())) {
              fieldStates.put(field.getDefinition(), FieldState.unknown());
            }
          });
    }
  }

  void acceptClassInitializerDefaultsResult(
      ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    classInitializerDefaultsResult.forEachOptimizedField(
        (field, value) -> {
          DexType fieldType = field.getType();
          if (value.isDefault(field.getType())) {
            return;
          }
          assert fieldType.isClassType() || fieldType.isPrimitiveType();
          fieldStates.compute(
              field,
              (f, fieldState) -> {
                if (fieldState == null) {
                  AbstractValue abstractValue = value.toAbstractValue(abstractValueFactory);
                  if (fieldType.isClassType()) {
                    assert abstractValue.isSingleStringValue()
                        || abstractValue.isSingleDexItemBasedStringValue();
                    if (fieldType == dexItemFactory.stringType) {
                      return ConcreteClassTypeFieldState.create(
                          abstractValue, DynamicType.definitelyNotNull());
                    } else {
                      ClassTypeElement nonNullableStringType =
                          dexItemFactory
                              .stringType
                              .toTypeElement(appView, definitelyNotNull())
                              .asClassType();
                      return ConcreteClassTypeFieldState.create(
                          abstractValue, DynamicType.createExact(nonNullableStringType));
                    }
                  } else {
                    assert fieldType.isPrimitiveType();
                    return ConcretePrimitiveTypeFieldState.create(abstractValue);
                  }
                }
                // If the field is already assigned outside the class initializer then just give up.
                return FieldState.unknown();
              });
        });
  }

  void recordFieldAccess(FieldInstruction instruction, ProgramField field, ProgramMethod context) {
    if (instruction.isFieldPut()) {
      recordFieldPut(field, instruction.value(), context);
    }
  }

  private void recordFieldPut(ProgramField field, Value value, ProgramMethod context) {
    // For now only attempt to prove that fields are definitely null. In order to prove a single
    // value for fields that are not definitely null, we need to prove that the given field is never
    // read before it is written.
    AbstractValue abstractValue =
        value.isZero() ? abstractValueFactory.createZeroValue() : AbstractValue.unknown();
    fieldStates.compute(
        field.getDefinition(),
        (f, fieldState) -> {
          if (fieldState == null || fieldState.isBottom()) {
            DexType fieldType = field.getType();
            if (fieldType.isArrayType()) {
              return ConcreteArrayTypeFieldState.create(abstractValue);
            }
            if (fieldType.isPrimitiveType()) {
              return ConcretePrimitiveTypeFieldState.create(abstractValue);
            }
            assert fieldType.isClassType();
            DynamicType dynamicType =
                WideningUtils.widenDynamicNonReceiverType(
                    appView,
                    value.getDynamicType(appView).withNullability(Nullability.maybeNull()),
                    field.getType());
            return ConcreteClassTypeFieldState.create(abstractValue, dynamicType);
          }

          if (fieldState.isUnknown()) {
            return fieldState;
          }

          assert fieldState.isConcrete();

          if (fieldState.isArray()) {
            ConcreteArrayTypeFieldState arrayFieldState = fieldState.asArray();
            return arrayFieldState.mutableJoin(appView, abstractValue);
          }

          if (fieldState.isPrimitive()) {
            ConcretePrimitiveTypeFieldState primitiveFieldState = fieldState.asPrimitive();
            return primitiveFieldState.mutableJoin(abstractValue, abstractValueFactory);
          }

          assert fieldState.isClass();

          ConcreteClassTypeFieldState classFieldState = fieldState.asClass();
          return classFieldState.mutableJoin(
              appView, abstractValue, value.getDynamicType(appView), field);
        });
  }

  void recordAllocationSite(NewInstance instruction, DexProgramClass clazz, ProgramMethod context) {
    Map<DexEncodedField, AbstractValue> abstractInstanceFieldValuesForClass =
        abstractInstanceFieldValues.get(clazz);
    if (abstractInstanceFieldValuesForClass == null) {
      // We are not tracking the value of any of clazz' instance fields.
      return;
    }

    InvokeDirect invoke = instruction.getUniqueConstructorInvoke(dexItemFactory);
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
            .getInstanceInitializerInfo(invoke)
            .fieldInitializationInfos();

    // Synchronize on the lattice element (abstractInstanceFieldValuesForClass) in case we process
    // another allocation site of `clazz` concurrently.
    synchronized (abstractInstanceFieldValuesForClass) {
      Iterator<Map.Entry<DexEncodedField, AbstractValue>> iterator =
          abstractInstanceFieldValuesForClass.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<DexEncodedField, AbstractValue> entry = iterator.next();
        DexEncodedField field = entry.getKey();
        AbstractValue abstractValue = entry.getValue();

        // The power set lattice is an expensive abstraction, so use it with caution.
        boolean isClassIdField = HorizontalClassMergerUtils.isClassIdField(appView, field);

        InstanceFieldInitializationInfo initializationInfo =
            initializationInfoCollection.get(field);
        if (initializationInfo.isArgumentInitializationInfo()) {
          InstanceFieldArgumentInitializationInfo argumentInitializationInfo =
              initializationInfo.asArgumentInitializationInfo();
          Value argument = invoke.arguments().get(argumentInitializationInfo.getArgumentIndex());
          AbstractValue argumentAbstractValue = argument.getAbstractValue(appView, context);
          abstractValue =
              abstractValue.join(
                  argumentAbstractValue,
                  appView.abstractValueFactory(),
                  field.getType().isReferenceType(),
                  isClassIdField);
          assert !abstractValue.isBottom();
        } else if (initializationInfo.isSingleValue()) {
          SingleValue singleValueInitializationInfo = initializationInfo.asSingleValue();
          abstractValue =
              abstractValue.join(
                  singleValueInitializationInfo,
                  appView.abstractValueFactory(),
                  field.getType().isReferenceType(),
                  isClassIdField);
        } else if (initializationInfo.isTypeInitializationInfo()) {
          // TODO(b/149732532): Not handled, for now.
          abstractValue = UnknownValue.getInstance();
        } else {
          assert initializationInfo.isUnknown();
          abstractValue = UnknownValue.getInstance();
        }

        assert !abstractValue.isBottom();

        // When approximating the possible values for the $r8$classId fields from horizontal class
        // merging, give up if the set of possible values equals the size of the merge group. In
        // this case, the information is useless.
        if (isClassIdField && abstractValue.isNonConstantNumberValue()) {
          NonConstantNumberValue initialAbstractValue =
              field.getOptimizationInfo().getAbstractValue().asNonConstantNumberValue();
          if (initialAbstractValue != null) {
            if (abstractValue.asNonConstantNumberValue().getAbstractionSize()
                >= initialAbstractValue.getAbstractionSize()) {
              abstractValue = UnknownValue.getInstance();
            }
          }
        }

        if (!abstractValue.isUnknown()) {
          entry.setValue(abstractValue);
          continue;
        }

        // We just lost track for this field.
        iterator.remove();
      }
    }
  }

  private void recordAllFieldPutsProcessed(
      ProgramField field, ProgramMethod context, OptimizationFeedbackDelayed feedback) {
    FieldState fieldState = fieldStates.getOrDefault(field.getDefinition(), FieldState.bottom());
    AbstractValue abstractValue = fieldState.getAbstractValue(appView.abstractValueFactory());
    if (abstractValue.isNonTrivial()) {
      feedback.recordFieldHasAbstractValue(field.getDefinition(), appView, abstractValue);
    }

    if (fieldState.isClass() && field.getOptimizationInfo().getDynamicType().isUnknown()) {
      ConcreteClassTypeFieldState classFieldState = fieldState.asClass();
      DynamicType dynamicType = classFieldState.getDynamicType();
      if (!dynamicType.isUnknown()) {
        assert WideningUtils.widenDynamicNonReceiverType(appView, dynamicType, field.getType())
            == dynamicType;
        if (dynamicType.isNotNullType()) {
          feedback.markFieldHasDynamicType(field, dynamicType);
        } else {
          DynamicTypeWithUpperBound staticType = field.getType().toDynamicType(appView);
          if (dynamicType.asDynamicTypeWithUpperBound().strictlyLessThan(staticType, appView)) {
            feedback.markFieldHasDynamicType(field, dynamicType);
          }
        }
      }
    }

    if (!field.getAccessFlags().isStatic()) {
      recordAllInstanceFieldPutsProcessed(field, feedback);
    }
  }

  private void recordAllInstanceFieldPutsProcessed(
      ProgramField field, OptimizationFeedbackDelayed feedback) {
    if (appView.appInfo().isInstanceFieldWrittenOnlyInInstanceInitializers(field)) {
      AbstractValue abstractValue = BottomValue.getInstance();
      DexProgramClass clazz = field.getHolder();
      for (DexEncodedMethod method : clazz.directMethods(DexEncodedMethod::isInstanceInitializer)) {
        InstanceFieldInitializationInfo fieldInitializationInfo =
            method
                .getOptimizationInfo()
                .getContextInsensitiveInstanceInitializerInfo()
                .fieldInitializationInfos()
                .get(field);
        if (fieldInitializationInfo.isSingleValue()) {
          abstractValue =
              abstractValue.join(
                  fieldInitializationInfo.asSingleValue(),
                  appView.abstractValueFactory(),
                  field.getType());
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
        feedback.recordFieldHasAbstractValue(field.getDefinition(), appView, abstractValue);
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
    private final Map<DexEncodedMethod, List<ProgramField>> fieldWrites = new IdentityHashMap<>();

    // The number of writes that have not yet been processed per field.
    private final Reference2IntMap<DexEncodedField> pendingFieldWrites =
        new Reference2IntOpenHashMap<>();

    FieldAccessGraph() {}

    public void initialize(AppView<AppInfoWithLiveness> appView) {
      FieldAccessInfoCollection<?> fieldAccessInfoCollection =
          appView.appInfo().getFieldAccessInfoCollection();
      fieldAccessInfoCollection.forEach(
          info -> {
            ProgramField field =
                appView.appInfo().resolveField(info.getField()).getSingleProgramField();
            if (field == null) {
              return;
            }
            if (!info.hasReflectiveAccess() && !info.isWrittenFromMethodHandle()) {
              info.forEachWriteContext(
                  context ->
                      fieldWrites
                          .computeIfAbsent(context.getDefinition(), ignore -> new ArrayList<>())
                          .add(field));
              pendingFieldWrites.put(field.getDefinition(), info.getNumberOfWriteContexts());
            }
          });
    }

    void markProcessed(ProgramMethod method, Consumer<ProgramField> allWritesSeenConsumer) {
      List<ProgramField> fieldWritesInMethod = fieldWrites.get(method.getDefinition());
      if (fieldWritesInMethod != null) {
        for (ProgramField field : fieldWritesInMethod) {
          int numberOfPendingFieldWrites = pendingFieldWrites.removeInt(field.getDefinition()) - 1;
          if (numberOfPendingFieldWrites > 0) {
            pendingFieldWrites.put(field.getDefinition(), numberOfPendingFieldWrites);
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
