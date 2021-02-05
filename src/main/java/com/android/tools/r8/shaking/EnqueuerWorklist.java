// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.GraphReporter.KeepReasonWitness;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class EnqueuerWorklist {

  public abstract static class EnqueuerAction {
    public abstract void run(Enqueuer enqueuer);
  }

  static class MarkReachableDirectAction extends EnqueuerAction {
    private final DexMethod target;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramDefinition context;
    private final KeepReason reason;

    MarkReachableDirectAction(DexMethod target, ProgramDefinition context, KeepReason reason) {
      this.target = target;
      this.context = context;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markNonStaticDirectMethodAsReachable(target, context, reason);
    }
  }

  static class MarkReachableSuperAction extends EnqueuerAction {
    private final DexMethod target;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramMethod context;

    public MarkReachableSuperAction(DexMethod target, ProgramMethod context) {
      this.target = target;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markSuperMethodAsReachable(target, context);
    }
  }

  static class MarkInstanceFieldAsReachableAction extends EnqueuerAction {
    private final ProgramField field;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramDefinition context;
    private final KeepReason reason;

    public MarkInstanceFieldAsReachableAction(
        ProgramField field, ProgramDefinition context, KeepReason reason) {
      this.field = field;
      this.context = context;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markInstanceFieldAsReachable(field, context, reason);
    }
  }

  static class MarkInstantiatedAction extends EnqueuerAction {
    private final DexProgramClass target;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramMethod context;
    private final InstantiationReason instantiationReason;
    private final KeepReason keepReason;

    public MarkInstantiatedAction(
        DexProgramClass target,
        ProgramMethod context,
        InstantiationReason instantiationReason,
        KeepReason keepReason) {
      this.target = target;
      this.context = context;
      this.instantiationReason = instantiationReason;
      this.keepReason = keepReason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.processNewlyInstantiatedClass(target, context, instantiationReason, keepReason);
    }
  }

  static class MarkAnnotationInstantiatedAction extends EnqueuerAction {
    private final DexProgramClass target;
    private final KeepReasonWitness reason;

    public MarkAnnotationInstantiatedAction(DexProgramClass target, KeepReasonWitness reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markAnnotationAsInstantiated(target, reason);
    }
  }

  static class MarkInterfaceInstantiatedAction extends EnqueuerAction {
    private final DexProgramClass target;
    private final KeepReasonWitness reason;

    public MarkInterfaceInstantiatedAction(DexProgramClass target, KeepReasonWitness reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markInterfaceAsInstantiated(target, reason);
    }
  }

  static class MarkMethodLiveAction extends EnqueuerAction {
    private final ProgramMethod method;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramDefinition context;

    public MarkMethodLiveAction(ProgramMethod method, ProgramDefinition context) {
      this.method = method;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markMethodAsLive(method, context);
    }
  }

  static class MarkMethodKeptAction extends EnqueuerAction {
    private final ProgramMethod target;
    private final KeepReason reason;

    public MarkMethodKeptAction(ProgramMethod target, KeepReason reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markMethodAsKept(target, reason);
    }
  }

  static class MarkFieldKeptAction extends EnqueuerAction {
    private final ProgramField field;
    private final KeepReasonWitness witness;

    public MarkFieldKeptAction(ProgramField field, KeepReasonWitness witness) {
      this.field = field;
      this.witness = witness;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markFieldAsKept(field, witness);
    }
  }

  static class TraceCodeAction extends EnqueuerAction {
    private final ProgramMethod method;

    TraceCodeAction(ProgramMethod method) {
      this.method = method;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceCode(method);
    }
  }

  static class TraceConstClassAction extends EnqueuerAction {
    private final DexType type;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramMethod context;

    TraceConstClassAction(DexType type, ProgramMethod context) {
      this.type = type;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceConstClass(type, context, null);
    }
  }

  static class TraceInvokeDirectAction extends EnqueuerAction {
    private final DexMethod invokedMethod;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramMethod context;

    TraceInvokeDirectAction(DexMethod invokedMethod, ProgramMethod context) {
      this.invokedMethod = invokedMethod;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceInvokeDirect(invokedMethod, context);
    }
  }

  static class TraceNewInstanceAction extends EnqueuerAction {
    private final DexType type;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramMethod context;

    TraceNewInstanceAction(DexType type, ProgramMethod context) {
      this.type = type;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceNewInstance(type, context);
    }
  }

  static class TraceStaticFieldReadAction extends EnqueuerAction {
    private final DexField field;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramMethod context;

    TraceStaticFieldReadAction(DexField field, ProgramMethod context) {
      this.field = field;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceStaticFieldRead(field, context);
    }
  }

  // Fields used for back-tracking an entire action path.
  private final Map<EnqueuerAction, EnqueuerAction> predecessorActionsForTesting =
      new IdentityHashMap<>();
  private EnqueuerAction currentActionForTesting = null;

  private final Queue<EnqueuerAction> queue = new ArrayDeque<>();

  private EnqueuerWorklist() {}

  public static EnqueuerWorklist createWorklist() {
    return new EnqueuerWorklist();
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public EnqueuerAction poll() {
    EnqueuerAction action = queue.poll();
    currentActionForTesting = action;
    return action;
  }

  void enqueueMarkReachableDirectAction(
      DexMethod method, ProgramDefinition context, KeepReason reason) {
    addToQueue(new MarkReachableDirectAction(method, context, reason));
  }

  void enqueueMarkReachableSuperAction(DexMethod method, ProgramMethod from) {
    addToQueue(new MarkReachableSuperAction(method, from));
  }

  public void enqueueMarkInstanceFieldAsReachableAction(
      ProgramField field, ProgramDefinition context, KeepReason reason) {
    addToQueue(new MarkInstanceFieldAsReachableAction(field, context, reason));
  }

  // TODO(b/142378367): Context is the containing method that is cause of the instantiation.
  // Consider updating call sites with the context information to increase precision where possible.
  public void enqueueMarkInstantiatedAction(
      DexProgramClass clazz,
      ProgramMethod context,
      InstantiationReason instantiationReason,
      KeepReason keepReason) {
    assert !clazz.isAnnotation();
    assert !clazz.isInterface();
    addToQueue(new MarkInstantiatedAction(clazz, context, instantiationReason, keepReason));
  }

  void enqueueMarkAnnotationInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
    assert clazz.isAnnotation();
    assert clazz.isInterface();
    addToQueue(new MarkAnnotationInstantiatedAction(clazz, reason));
  }

  void enqueueMarkInterfaceInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
    assert !clazz.isAnnotation();
    assert clazz.isInterface();
    addToQueue(new MarkInterfaceInstantiatedAction(clazz, reason));
  }

  void enqueueMarkMethodLiveAction(ProgramMethod method, ProgramDefinition context) {
    addToQueue(new MarkMethodLiveAction(method, context));
  }

  void enqueueMarkMethodKeptAction(ProgramMethod method, KeepReason reason) {
    addToQueue(new MarkMethodKeptAction(method, reason));
  }

  void enqueueMarkFieldKeptAction(ProgramField field, KeepReasonWitness witness) {
    addToQueue(new MarkFieldKeptAction(field, witness));
  }

  public void enqueueTraceCodeAction(ProgramMethod method) {
    addToQueue(new TraceCodeAction(method));
  }

  public void enqueueTraceConstClassAction(DexType type, ProgramMethod context) {
    addToQueue(new TraceConstClassAction(type, context));
  }

  public void enqueueTraceInvokeDirectAction(DexMethod invokedMethod, ProgramMethod context) {
    addToQueue(new TraceInvokeDirectAction(invokedMethod, context));
  }

  public void enqueueTraceNewInstanceAction(DexType type, ProgramMethod context) {
    addToQueue(new TraceNewInstanceAction(type, context));
  }

  public void enqueueTraceStaticFieldRead(DexField field, ProgramMethod context) {
    addToQueue(new TraceStaticFieldReadAction(field, context));
  }

  private void addToQueue(EnqueuerAction action) {
    EnqueuerAction existingAction =
        predecessorActionsForTesting.put(action, currentActionForTesting);
    assert existingAction == null;
    queue.add(action);
  }

  /**
   * Use this method for getting the path of actions leading to the action passed in. This only
   * works if run with assertions enabled.
   */
  public List<EnqueuerAction> getPathForTesting(EnqueuerAction action) {
    ArrayList<EnqueuerAction> actions = new ArrayList<>();
    actions.add(action);
    EnqueuerAction currentAction = action;
    while (predecessorActionsForTesting.containsKey(currentAction)) {
      currentAction = predecessorActionsForTesting.get(currentAction);
      actions.add(currentAction);
    }
    return actions;
  }
}
