// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.GraphReporter.KeepReasonWitness;
import java.util.ArrayDeque;
import java.util.Queue;

public class EnqueuerWorklist {

  public abstract static class Action {
    public abstract void run(Enqueuer enqueuer);
  }

  static class MarkReachableDirectAction extends Action {
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

  static class MarkReachableVirtualAction extends Action {
    final DexMethod target;
    final KeepReason reason;

    MarkReachableVirtualAction(DexMethod target, KeepReason reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markVirtualMethodAsReachable(target, false, reason);
    }
  }

  static class MarkReachableInterfaceAction extends Action {
    final DexMethod target;
    final KeepReason reason;

    public MarkReachableInterfaceAction(DexMethod target, KeepReason reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markVirtualMethodAsReachable(target, true, reason);
    }
  }

  static class MarkReachableSuperAction extends Action {
    final DexMethod target;
    final DexEncodedMethod context;

    public MarkReachableSuperAction(DexMethod target, DexEncodedMethod context) {
      this.target = target;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markSuperMethodAsReachable(target, context);
    }
  }

  static class MarkReachableFieldAction extends Action {
    final DexEncodedField target;
    final KeepReason reason;

    public MarkReachableFieldAction(DexEncodedField target, KeepReason reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markInstanceFieldAsReachable(target, reason);
    }
  }

  static class MarkInstantiatedAction extends Action {
    final DexProgramClass target;
    final DexEncodedMethod context;
    final KeepReason reason;

    public MarkInstantiatedAction(
        DexProgramClass target, DexEncodedMethod context, KeepReason reason) {
      this.target = target;
      this.context = context;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.processNewlyInstantiatedClass(target, context, reason);
    }
  }

  static class MarkMethodLiveAction extends Action {
    final DexEncodedMethod target;
    final KeepReason reason;

    public MarkMethodLiveAction(DexEncodedMethod target, KeepReason reason) {
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markMethodAsLive(target, reason);
    }
  }

  static class MarkMethodKeptAction extends Action {
    final DexProgramClass holder;
    final DexEncodedMethod target;
    final KeepReason reason;

    public MarkMethodKeptAction(
        DexProgramClass holder, DexEncodedMethod target, KeepReason reason) {
      this.holder = holder;
      this.target = target;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markMethodAsKept(holder, target, reason);
    }
  }

  static class MarkFieldKeptAction extends Action {
    final DexProgramClass holder;
    final DexEncodedField target;
    final KeepReasonWitness witness;

    public MarkFieldKeptAction(
        DexProgramClass holder, DexEncodedField target, KeepReasonWitness witness) {
      this.holder = holder;
      this.target = target;
      this.witness = witness;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markFieldAsKept(holder, target, witness);
    }
  }

  static class TraceConstClassAction extends Action {
    final DexType type;
    final DexEncodedMethod currentMethod;

    TraceConstClassAction(DexType type, DexEncodedMethod currentMethod) {
      this.type = type;
      this.currentMethod = currentMethod;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceConstClass(type, currentMethod);
    }
  }

  static class TraceStaticFieldReadAction extends Action {
    final DexField field;
    final DexEncodedMethod currentMethod;

    TraceStaticFieldReadAction(DexField field, DexEncodedMethod currentMethod) {
      this.field = field;
      this.currentMethod = currentMethod;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceStaticFieldRead(field, currentMethod);
    }
  }

  private final AppView<?> appView;
  private final Queue<Action> queue = new ArrayDeque<>();

  private EnqueuerWorklist(AppView<?> appView) {
    this.appView = appView;
  }

  public static EnqueuerWorklist createWorklist(AppView<?> appView) {
    return new EnqueuerWorklist(appView);
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public Action poll() {
    return queue.poll();
  }

  void enqueueMarkReachableDirectAction(DexMethod method, KeepReason reason) {
    queue.add(new MarkReachableDirectAction(method, reason));
  }

  void enqueueMarkReachableVirtualAction(DexMethod method, KeepReason reason) {
    queue.add(new MarkReachableVirtualAction(method, reason));
  }

  void enqueueMarkReachableInterfaceAction(DexMethod method, KeepReason reason) {
    queue.add(new MarkReachableInterfaceAction(method, reason));
  }

  void enqueueMarkReachableSuperAction(DexMethod method, DexEncodedMethod from) {
    queue.add(new MarkReachableSuperAction(method, from));
  }

  public void enqueueMarkReachableFieldAction(
      DexProgramClass clazz, DexEncodedField field, KeepReason reason) {
    assert field.field.holder == clazz.type;
    queue.add(new MarkReachableFieldAction(field, reason));
  }

  // TODO(b/142378367): Context is the containing method that is cause of the instantiation.
  // Consider updating call sites with the context information to increase precision where possible.
  void enqueueMarkInstantiatedAction(
      DexProgramClass clazz, DexEncodedMethod context, KeepReason reason) {
    assert !clazz.isInterface() || clazz.accessFlags.isAnnotation();
    queue.add(new MarkInstantiatedAction(clazz, context, reason));
  }

  void enqueueMarkMethodLiveAction(
      DexProgramClass clazz, DexEncodedMethod method, KeepReason reason) {
    assert method.method.holder == clazz.type;
    queue.add(new MarkMethodLiveAction(method, reason));
  }

  void enqueueMarkMethodKeptAction(
      DexProgramClass clazz, DexEncodedMethod method, KeepReason reason) {
    assert method.method.holder == clazz.type;
    queue.add(new MarkMethodKeptAction(clazz, method, reason));
  }

  void enqueueMarkFieldKeptAction(
      DexProgramClass holder, DexEncodedField field, KeepReasonWitness witness) {
    assert field.isProgramField(appView);
    queue.add(new MarkFieldKeptAction(holder, field, witness));
  }

  public void enqueueTraceConstClassAction(DexType type, DexEncodedMethod currentMethod) {
    assert currentMethod.isProgramMethod(appView);
    queue.add(new TraceConstClassAction(type, currentMethod));
  }

  public void enqueueTraceStaticFieldRead(DexField field, DexEncodedMethod currentMethod) {
    assert currentMethod.isProgramMethod(appView);
    queue.add(new TraceStaticFieldReadAction(field, currentMethod));
  }
}
