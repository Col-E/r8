// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.GraphReporter.KeepReasonWitness;
import java.util.ArrayDeque;
import java.util.Queue;

public class EnqueuerWorklist {

  public abstract static class EnqueuerAction {
    public abstract void run(Enqueuer enqueuer);
  }

  static class MarkReachableDirectAction extends EnqueuerAction {
    final DexMethod target;
    final KeepReason reason;

    MarkReachableDirectAction(DexMethod target, KeepReason reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markNonStaticDirectMethodAsReachable(target, reason);
    }
  }

  static class MarkReachableSuperAction extends EnqueuerAction {
    final DexMethod target;
    final ProgramMethod context;

    public MarkReachableSuperAction(DexMethod target, ProgramMethod context) {
      this.target = target;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markSuperMethodAsReachable(target, context);
    }
  }

  static class MarkReachableFieldAction extends EnqueuerAction {
    final ProgramField field;
    final KeepReason reason;

    public MarkReachableFieldAction(ProgramField field, KeepReason reason) {
      this.field = field;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markInstanceFieldAsReachable(field, reason);
    }
  }

  static class MarkInstantiatedAction extends EnqueuerAction {

    final DexProgramClass target;
    final ProgramMethod context;
    final InstantiationReason instantiationReason;
    final KeepReason keepReason;

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
    final DexProgramClass target;
    final KeepReasonWitness reason;

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
    final DexProgramClass target;
    final KeepReasonWitness reason;

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
    final ProgramMethod method;
    final KeepReason reason;

    public MarkMethodLiveAction(ProgramMethod method, KeepReason reason) {
      this.method = method;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markMethodAsLive(method, reason);
    }
  }

  static class MarkMethodKeptAction extends EnqueuerAction {
    final ProgramMethod target;
    final KeepReason reason;

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
    final ProgramField field;
    final KeepReasonWitness witness;

    public MarkFieldKeptAction(ProgramField field, KeepReasonWitness witness) {
      this.field = field;
      this.witness = witness;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markFieldAsKept(field, witness);
    }
  }

  static class TraceConstClassAction extends EnqueuerAction {
    final DexType type;
    final ProgramMethod context;

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
    final DexMethod invokedMethod;
    final ProgramMethod context;

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
    final DexType type;
    final ProgramMethod context;

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
    final DexField field;
    final ProgramMethod context;

    TraceStaticFieldReadAction(DexField field, ProgramMethod context) {
      this.field = field;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceStaticFieldRead(field, context);
    }
  }

  private final AppView<?> appView;
  private final Queue<EnqueuerAction> queue = new ArrayDeque<>();

  private EnqueuerWorklist(AppView<?> appView) {
    this.appView = appView;
  }

  public static EnqueuerWorklist createWorklist(AppView<?> appView) {
    return new EnqueuerWorklist(appView);
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public EnqueuerAction poll() {
    return queue.poll();
  }

  void enqueueMarkReachableDirectAction(DexMethod method, KeepReason reason) {
    queue.add(new MarkReachableDirectAction(method, reason));
  }

  void enqueueMarkReachableSuperAction(DexMethod method, ProgramMethod from) {
    queue.add(new MarkReachableSuperAction(method, from));
  }

  public void enqueueMarkReachableFieldAction(ProgramField field, KeepReason reason) {
    queue.add(new MarkReachableFieldAction(field, reason));
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
    queue.add(new MarkInstantiatedAction(clazz, context, instantiationReason, keepReason));
  }

  void enqueueMarkAnnotationInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
    assert clazz.isAnnotation();
    assert clazz.isInterface();
    queue.add(new MarkAnnotationInstantiatedAction(clazz, reason));
  }

  void enqueueMarkInterfaceInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
    assert !clazz.isAnnotation();
    assert clazz.isInterface();
    queue.add(new MarkInterfaceInstantiatedAction(clazz, reason));
  }

  void enqueueMarkMethodLiveAction(ProgramMethod method, KeepReason reason) {
    queue.add(new MarkMethodLiveAction(method, reason));
  }

  void enqueueMarkMethodKeptAction(ProgramMethod method, KeepReason reason) {
    queue.add(new MarkMethodKeptAction(method, reason));
  }

  void enqueueMarkFieldKeptAction(ProgramField field, KeepReasonWitness witness) {
    queue.add(new MarkFieldKeptAction(field, witness));
  }

  public void enqueueTraceConstClassAction(DexType type, ProgramMethod context) {
    queue.add(new TraceConstClassAction(type, context));
  }

  public void enqueueTraceInvokeDirectAction(DexMethod invokedMethod, ProgramMethod context) {
    queue.add(new TraceInvokeDirectAction(invokedMethod, context));
  }

  public void enqueueTraceNewInstanceAction(DexType type, ProgramMethod context) {
    queue.add(new TraceNewInstanceAction(type, context));
  }

  public void enqueueTraceStaticFieldRead(DexField field, ProgramMethod context) {
    queue.add(new TraceStaticFieldReadAction(field, context));
  }
}
