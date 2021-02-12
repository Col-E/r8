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
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayDeque;
import java.util.Queue;

public class EnqueuerWorklist {

  public abstract static class EnqueuerAction {
    public abstract void run(Enqueuer enqueuer);
  }

  static class AssertAction extends EnqueuerAction {
    private final Action assertion;

    AssertAction(Action assertion) {
      this.assertion = assertion;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      assertion.execute();
    }
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

  static class MarkFieldAsReachableAction extends EnqueuerAction {
    private final ProgramField field;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramDefinition context;
    private final KeepReason reason;

    public MarkFieldAsReachableAction(
        ProgramField field, ProgramDefinition context, KeepReason reason) {
      this.field = field;
      this.context = context;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markFieldAsReachable(field, context, reason);
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

  static class TraceMethodDefinitionExcludingCodeAction extends EnqueuerAction {
    private final ProgramMethod method;

    TraceMethodDefinitionExcludingCodeAction(ProgramMethod method) {
      this.method = method;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceMethodDefinitionExcludingCode(method);
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

  private final Enqueuer enqueuer;
  private final Queue<EnqueuerAction> queue = new ArrayDeque<>();

  private EnqueuerWorklist(Enqueuer enqueuer) {
    this.enqueuer = enqueuer;
  }

  public static EnqueuerWorklist createWorklist(Enqueuer enqueuer) {
    return new EnqueuerWorklist(enqueuer);
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public EnqueuerAction poll() {
    return queue.poll();
  }

  boolean enqueueAssertAction(Action assertion) {
    if (InternalOptions.assertionsEnabled()) {
      queue.add(new AssertAction(assertion));
    }
    return true;
  }

  void enqueueMarkReachableDirectAction(
      DexMethod method, ProgramDefinition context, KeepReason reason) {
    queue.add(new MarkReachableDirectAction(method, context, reason));
  }

  void enqueueMarkReachableSuperAction(DexMethod method, ProgramMethod from) {
    queue.add(new MarkReachableSuperAction(method, from));
  }

  public void enqueueMarkFieldAsReachableAction(
      ProgramField field, ProgramDefinition context, KeepReason reason) {
    queue.add(new MarkFieldAsReachableAction(field, context, reason));
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

  boolean enqueueMarkMethodLiveAction(
      ProgramMethod method, ProgramDefinition context, KeepReason reason) {
    if (enqueuer.addLiveMethod(method, reason)) {
      queue.add(new MarkMethodLiveAction(method, context));
      if (!enqueuer.isMethodTargeted(method)) {
        queue.add(new TraceMethodDefinitionExcludingCodeAction(method));
      }
      return true;
    }
    return false;
  }

  void enqueueMarkMethodKeptAction(ProgramMethod method, KeepReason reason) {
    queue.add(new MarkMethodKeptAction(method, reason));
  }

  void enqueueMarkFieldKeptAction(ProgramField field, KeepReasonWitness witness) {
    queue.add(new MarkFieldKeptAction(field, witness));
  }

  public void enqueueTraceCodeAction(ProgramMethod method) {
    queue.add(new TraceCodeAction(method));
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
