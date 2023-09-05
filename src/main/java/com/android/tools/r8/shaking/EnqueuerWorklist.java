// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessKind;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessMetadata;
import com.android.tools.r8.shaking.GraphReporter.KeepReasonWitness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class EnqueuerWorklist {

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

  static class TraceAnnotationAction extends EnqueuerAction {
    private final ProgramDefinition annotatedItem;
    private final DexAnnotation annotation;
    private final AnnotatedKind annotatedKind;

    TraceAnnotationAction(
        ProgramDefinition annotatedItem, DexAnnotation annotation, AnnotatedKind annotatedKind) {
      this.annotatedItem = annotatedItem;
      this.annotation = annotation;
      this.annotatedKind = annotatedKind;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.processAnnotation(annotatedItem, annotation, annotatedKind);
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
    private final boolean ignoreCompatRules;

    TraceConstClassAction(DexType type, ProgramMethod context, boolean ignoreCompatRules) {
      this.type = type;
      this.context = context;
      this.ignoreCompatRules = ignoreCompatRules;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceConstClass(type, context, null, ignoreCompatRules);
    }
  }

  static class TraceDirectAndIndirectClassInitializers extends EnqueuerAction {
    private final DexProgramClass clazz;

    TraceDirectAndIndirectClassInitializers(DexProgramClass clazz) {
      this.clazz = clazz;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markDirectAndIndirectClassInitializersAsLive(clazz);
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

  static class TraceInvokeStaticAction extends EnqueuerAction {
    private final DexMethod invokedMethod;
    // TODO(b/175854431): Avoid pushing context on worklist.
    private final ProgramMethod context;

    TraceInvokeStaticAction(DexMethod invokedMethod, ProgramMethod context) {
      this.invokedMethod = invokedMethod;
      this.context = context;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceInvokeStatic(invokedMethod, context);
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

  static class TraceReflectiveFieldAccessAction extends EnqueuerAction {
    private final ProgramField field;
    private final ProgramMethod context;
    private final FieldAccessKind kind;

    TraceReflectiveFieldAccessAction(ProgramField field, ProgramMethod context) {
      this(field, context, null);
    }

    TraceReflectiveFieldAccessAction(
        ProgramField field, ProgramMethod context, FieldAccessKind kind) {
      this.field = field;
      this.context = context;
      this.kind = kind;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      if (kind != null) {
        if (kind.isRead()) {
          enqueuer.traceReflectiveFieldRead(field, context);
        } else {
          enqueuer.traceReflectiveFieldWrite(field, context);
        }
      } else {
        enqueuer.traceReflectiveFieldAccess(field, context);
      }
    }
  }

  static class TraceTypeReferenceAction extends EnqueuerAction {
    private final DexProgramClass clazz;
    private final KeepReason reason;

    TraceTypeReferenceAction(DexProgramClass clazz, KeepReason reason) {
      this.clazz = clazz;
      this.reason = reason;
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.markTypeAsLive(clazz, reason);
    }
  }

  abstract static class TraceFieldAccessAction extends EnqueuerAction {
    protected final DexField field;
    // TODO(b/175854431): Avoid pushing context on worklist.
    protected final ProgramMethod context;
    protected final FieldAccessMetadata metadata;

    TraceFieldAccessAction(DexField field, ProgramMethod context, FieldAccessMetadata metadata) {
      this.field = field;
      this.context = context;
      this.metadata = metadata;
    }

    @SuppressWarnings("ReferenceEquality")
    protected boolean baseEquals(TraceFieldAccessAction action) {
      return field == action.field
          && context.isStructurallyEqualTo(action.context)
          && metadata.equals(action.metadata);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      TraceFieldAccessAction action = (TraceFieldAccessAction) obj;
      return baseEquals(action);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, context.getReference(), metadata);
    }
  }

  static class TraceInstanceFieldReadAction extends TraceFieldAccessAction {

    TraceInstanceFieldReadAction(
        DexField field, ProgramMethod context, FieldAccessMetadata metadata) {
      super(field, context, metadata);
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceInstanceFieldRead(field, context, metadata);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      TraceInstanceFieldReadAction action = (TraceInstanceFieldReadAction) obj;
      return baseEquals(action);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, context.getReference(), metadata);
    }
  }

  static class TraceInstanceFieldWriteAction extends TraceFieldAccessAction {

    TraceInstanceFieldWriteAction(
        DexField field, ProgramMethod context, FieldAccessMetadata metadata) {
      super(field, context, metadata);
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceInstanceFieldWrite(field, context, metadata);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      TraceInstanceFieldWriteAction action = (TraceInstanceFieldWriteAction) obj;
      return baseEquals(action);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, context.getReference(), metadata);
    }
  }

  static class TraceStaticFieldReadAction extends TraceFieldAccessAction {

    TraceStaticFieldReadAction(
        DexField field, ProgramMethod context, FieldAccessMetadata metadata) {
      super(field, context, metadata);
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceStaticFieldRead(field, context, metadata);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      TraceStaticFieldReadAction action = (TraceStaticFieldReadAction) obj;
      return baseEquals(action);
    }
  }

  static class TraceStaticFieldWriteAction extends TraceFieldAccessAction {

    TraceStaticFieldWriteAction(
        DexField field, ProgramMethod context, FieldAccessMetadata metadata) {
      super(field, context, metadata);
    }

    @Override
    public void run(Enqueuer enqueuer) {
      enqueuer.traceStaticFieldWrite(field, context, metadata);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      TraceStaticFieldWriteAction action = (TraceStaticFieldWriteAction) obj;
      return baseEquals(action);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, context.getReference(), metadata);
    }
  }

  final Enqueuer enqueuer;
  final Queue<EnqueuerAction> queue;

  public static EnqueuerWorklist createWorklist(Enqueuer enqueuer) {
    return new PushableEnqueuerWorkList(enqueuer);
  }

  private EnqueuerWorklist(Enqueuer enqueuer, Queue<EnqueuerAction> queue) {
    this.enqueuer = enqueuer;
    this.queue = queue;
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public EnqueuerAction poll() {
    return queue.poll();
  }

  abstract EnqueuerWorklist nonPushable();

  final void enqueueAll(Collection<? extends EnqueuerAction> actions) {
    actions.forEach(this::enqueue);
  }

  abstract void enqueue(EnqueuerAction action);

  abstract boolean enqueueAssertAction(Action assertion);

  abstract void enqueueMarkReachableDirectAction(
      DexMethod method, ProgramDefinition context, KeepReason reason);

  abstract void enqueueMarkReachableSuperAction(DexMethod method, ProgramMethod from);

  public abstract void enqueueMarkFieldAsReachableAction(
      ProgramField field, ProgramDefinition context, KeepReason reason);

  public abstract void enqueueMarkInstantiatedAction(
      DexProgramClass clazz,
      ProgramMethod context,
      InstantiationReason instantiationReason,
      KeepReason keepReason);

  abstract void enqueueMarkAnnotationInstantiatedAction(
      DexProgramClass clazz, KeepReasonWitness reason);

  abstract void enqueueMarkInterfaceInstantiatedAction(
      DexProgramClass clazz, KeepReasonWitness reason);

  abstract boolean enqueueMarkMethodLiveAction(
      ProgramMethod method, ProgramDefinition context, KeepReason reason);

  abstract void enqueueMarkMethodKeptAction(ProgramMethod method, KeepReason reason);

  abstract void enqueueMarkFieldKeptAction(ProgramField field, KeepReasonWitness witness);

  abstract void enqueueTraceAnnotationAction(
      ProgramDefinition annotatedItem, DexAnnotation annotation, AnnotatedKind annotatedKind);

  public abstract void enqueueTraceCodeAction(ProgramMethod method);

  public abstract void enqueueTraceConstClassAction(
      DexType type, ProgramMethod context, boolean ignoreCompatRules);

  public abstract void enqueueTraceDirectAndIndirectClassInitializers(DexProgramClass clazz);

  public abstract void enqueueTraceInvokeDirectAction(
      DexMethod invokedMethod, ProgramMethod context);

  public abstract void enqueueTraceInvokeStaticAction(
      DexMethod invokedMethod, ProgramMethod context);

  public abstract void enqueueTraceNewInstanceAction(DexType type, ProgramMethod context);

  public abstract void enqueueTraceReflectiveFieldAccessAction(
      ProgramField field, ProgramMethod context);

  public abstract void enqueueTraceReflectiveFieldReadAction(
      ProgramField field, ProgramMethod context);

  public abstract void enqueueTraceReflectiveFieldWriteAction(
      ProgramField field, ProgramMethod context);

  public abstract void enqueueTraceStaticFieldRead(DexField field, ProgramMethod context);

  public abstract void enqueueTraceTypeReferenceAction(DexProgramClass clazz, KeepReason reason);

  static class PushableEnqueuerWorkList extends EnqueuerWorklist {

    PushableEnqueuerWorkList(Enqueuer enqueuer) {
      super(enqueuer, new ConcurrentLinkedQueue<>());
    }

    @Override
    EnqueuerWorklist nonPushable() {
      return new NonPushableEnqueuerWorklist(this);
    }

    @Override
    void enqueue(EnqueuerAction action) {
      queue.add(action);
    }

    @Override
    boolean enqueueAssertAction(Action assertion) {
      if (InternalOptions.assertionsEnabled()) {
        queue.add(new AssertAction(assertion));
      }
      return true;
    }

    @Override
    void enqueueMarkReachableDirectAction(
        DexMethod method, ProgramDefinition context, KeepReason reason) {
      queue.add(new MarkReachableDirectAction(method, context, reason));
    }

    @Override
    void enqueueMarkReachableSuperAction(DexMethod method, ProgramMethod from) {
      queue.add(new MarkReachableSuperAction(method, from));
    }

    @Override
    public void enqueueMarkFieldAsReachableAction(
        ProgramField field, ProgramDefinition context, KeepReason reason) {
      queue.add(new MarkFieldAsReachableAction(field, context, reason));
    }

    // TODO(b/142378367): Context is the containing method that is cause of the instantiation.
    // Consider updating call sites with the context information to increase precision where
    // possible.
    @Override
    public void enqueueMarkInstantiatedAction(
        DexProgramClass clazz,
        ProgramMethod context,
        InstantiationReason instantiationReason,
        KeepReason keepReason) {
      assert !clazz.isAnnotation();
      assert !clazz.isInterface();
      queue.add(new MarkInstantiatedAction(clazz, context, instantiationReason, keepReason));
    }

    @Override
    void enqueueMarkAnnotationInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
      assert clazz.isAnnotation();
      assert clazz.isInterface();
      queue.add(new MarkAnnotationInstantiatedAction(clazz, reason));
    }

    @Override
    void enqueueMarkInterfaceInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
      assert !clazz.isAnnotation();
      assert clazz.isInterface();
      queue.add(new MarkInterfaceInstantiatedAction(clazz, reason));
    }

    @Override
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

    @Override
    void enqueueMarkMethodKeptAction(ProgramMethod method, KeepReason reason) {
      queue.add(new MarkMethodKeptAction(method, reason));
    }

    @Override
    void enqueueMarkFieldKeptAction(ProgramField field, KeepReasonWitness witness) {
      queue.add(new MarkFieldKeptAction(field, witness));
    }

    @Override
    void enqueueTraceAnnotationAction(
        ProgramDefinition annotatedItem, DexAnnotation annotation, AnnotatedKind annotatedKind) {
      queue.add(new TraceAnnotationAction(annotatedItem, annotation, annotatedKind));
    }

    @Override
    public void enqueueTraceCodeAction(ProgramMethod method) {
      queue.add(new TraceCodeAction(method));
    }

    @Override
    public void enqueueTraceConstClassAction(
        DexType type, ProgramMethod context, boolean ignoreCompatRules) {
      queue.add(new TraceConstClassAction(type, context, ignoreCompatRules));
    }

    @Override
    public void enqueueTraceDirectAndIndirectClassInitializers(DexProgramClass clazz) {
      queue.add(new TraceDirectAndIndirectClassInitializers(clazz));
    }

    @Override
    public void enqueueTraceInvokeDirectAction(DexMethod invokedMethod, ProgramMethod context) {
      queue.add(new TraceInvokeDirectAction(invokedMethod, context));
    }

    @Override
    public void enqueueTraceInvokeStaticAction(DexMethod invokedMethod, ProgramMethod context) {
      queue.add(new TraceInvokeStaticAction(invokedMethod, context));
    }

    @Override
    public void enqueueTraceNewInstanceAction(DexType type, ProgramMethod context) {
      queue.add(new TraceNewInstanceAction(type, context));
    }

    @Override
    public void enqueueTraceReflectiveFieldAccessAction(ProgramField field, ProgramMethod context) {
      FieldAccessInfo info = enqueuer.getFieldAccessInfoCollection().get(field.getReference());
      if (info == null || !info.hasReflectiveRead() || !info.hasReflectiveWrite()) {
        queue.add(new TraceReflectiveFieldAccessAction(field, context));
      }
    }

    @Override
    public void enqueueTraceReflectiveFieldReadAction(ProgramField field, ProgramMethod context) {
      FieldAccessInfo info = enqueuer.getFieldAccessInfoCollection().get(field.getReference());
      if (info == null || !info.hasReflectiveRead()) {
        queue.add(
            new TraceReflectiveFieldAccessAction(
                field,
                context,
                field.getAccessFlags().isStatic()
                    ? FieldAccessKind.STATIC_READ
                    : FieldAccessKind.INSTANCE_READ));
      }
    }

    @Override
    public void enqueueTraceReflectiveFieldWriteAction(ProgramField field, ProgramMethod context) {
      FieldAccessInfo info = enqueuer.getFieldAccessInfoCollection().get(field.getReference());
      if (info == null || !info.hasReflectiveWrite()) {
        queue.add(
            new TraceReflectiveFieldAccessAction(
                field,
                context,
                field.getAccessFlags().isStatic()
                    ? FieldAccessKind.STATIC_WRITE
                    : FieldAccessKind.INSTANCE_WRITE));
      }
    }

    @Override
    public void enqueueTraceStaticFieldRead(DexField field, ProgramMethod context) {
      queue.add(new TraceStaticFieldReadAction(field, context, FieldAccessMetadata.DEFAULT));
    }

    @Override
    public void enqueueTraceTypeReferenceAction(DexProgramClass clazz, KeepReason reason) {
      queue.add(new TraceTypeReferenceAction(clazz, reason));
    }
  }

  public static class NonPushableEnqueuerWorklist extends EnqueuerWorklist {

    private NonPushableEnqueuerWorklist(PushableEnqueuerWorkList workList) {
      super(workList.enqueuer, workList.queue);
    }

    @Override
    EnqueuerWorklist nonPushable() {
      return this;
    }

    @Override
    void enqueue(EnqueuerAction action) {
      throw attemptToEnqueue("EnqueuerAction " + action);
    }

    private Unreachable attemptToEnqueue(String msg) {
      throw new Unreachable(
          "Attempt to enqueue an action in a non pushable enqueuer work list (" + msg + ")");
    }

    @Override
    boolean enqueueAssertAction(Action assertion) {
      assertion.execute();
      return true;
    }

    @Override
    void enqueueMarkReachableDirectAction(
        DexMethod method, ProgramDefinition context, KeepReason reason) {
      throw attemptToEnqueue("MarkReachableDirectAction " + method + " from " + context);
    }

    @Override
    void enqueueMarkReachableSuperAction(DexMethod method, ProgramMethod from) {
      throw attemptToEnqueue("MarkReachableSuperAction " + method + " from " + from);
    }

    @Override
    public void enqueueMarkFieldAsReachableAction(
        ProgramField field, ProgramDefinition context, KeepReason reason) {
      throw attemptToEnqueue("MarkFieldAsReachableAction " + field + " from " + context);
    }

    @Override
    public void enqueueMarkInstantiatedAction(
        DexProgramClass clazz,
        ProgramMethod context,
        InstantiationReason instantiationReason,
        KeepReason keepReason) {
      throw attemptToEnqueue("MarkInstantiatedAction " + clazz + " from " + context);
    }

    @Override
    void enqueueMarkAnnotationInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
      throw attemptToEnqueue("MarkAnnotationInstantiatedAction " + clazz);
    }

    @Override
    void enqueueMarkInterfaceInstantiatedAction(DexProgramClass clazz, KeepReasonWitness reason) {
      throw attemptToEnqueue("MarkInterfaceInstantiatedAction " + clazz);
    }

    @Override
    boolean enqueueMarkMethodLiveAction(
        ProgramMethod method, ProgramDefinition context, KeepReason reason) {
      if (!enqueuer.addLiveMethod(method, reason)) {
        return false;
      }
      throw attemptToEnqueue("MarkMethodLiveAction " + method + " from " + context);
    }

    @Override
    void enqueueMarkMethodKeptAction(ProgramMethod method, KeepReason reason) {
      throw attemptToEnqueue("MarkMethodKeptAction " + method);
    }

    @Override
    void enqueueMarkFieldKeptAction(ProgramField field, KeepReasonWitness witness) {
      throw attemptToEnqueue("MarkFieldKeptAction " + field);
    }

    @Override
    void enqueueTraceAnnotationAction(
        ProgramDefinition annotatedItem, DexAnnotation annotation, AnnotatedKind annotatedKind) {
      throw attemptToEnqueue("TraceAnnotationAction " + annotation + " from " + annotatedItem);
    }

    @Override
    public void enqueueTraceCodeAction(ProgramMethod method) {
      throw attemptToEnqueue("TraceCodeAction " + method);
    }

    @Override
    public void enqueueTraceConstClassAction(
        DexType type, ProgramMethod context, boolean ignoreCompatRules) {
      throw attemptToEnqueue("TraceConstClassAction " + type + " from " + context);
    }

    @Override
    public void enqueueTraceDirectAndIndirectClassInitializers(DexProgramClass clazz) {
      throw attemptToEnqueue("TraceDirectAndIndirectClassInitializers " + clazz);
    }

    @Override
    public void enqueueTraceInvokeDirectAction(DexMethod invokedMethod, ProgramMethod context) {
      throw attemptToEnqueue("TraceInvokeDirectAction " + invokedMethod + " from " + context);
    }

    @Override
    public void enqueueTraceInvokeStaticAction(DexMethod invokedMethod, ProgramMethod context) {
      throw attemptToEnqueue("TraceInvokeStaticAction " + invokedMethod + " from " + context);
    }

    @Override
    public void enqueueTraceNewInstanceAction(DexType type, ProgramMethod context) {
      throw attemptToEnqueue("TraceNewInstanceAction " + type + " from " + context);
    }

    @Override
    public void enqueueTraceReflectiveFieldAccessAction(ProgramField field, ProgramMethod context) {
      throw attemptToEnqueue("TraceReflectiveFieldAccessAction " + field + " from " + context);
    }

    @Override
    public void enqueueTraceReflectiveFieldReadAction(ProgramField field, ProgramMethod context) {
      throw attemptToEnqueue("TraceReflectiveFieldReadAction " + field + " from " + context);
    }

    @Override
    public void enqueueTraceReflectiveFieldWriteAction(ProgramField field, ProgramMethod context) {
      throw attemptToEnqueue("TraceReflectiveFieldWriteAction " + field + " from " + context);
    }

    @Override
    public void enqueueTraceStaticFieldRead(DexField field, ProgramMethod context) {
      throw attemptToEnqueue("TraceStaticFieldRead " + field + " from " + context);
    }

    @Override
    public void enqueueTraceTypeReferenceAction(DexProgramClass clazz, KeepReason reason) {
      throw attemptToEnqueue("TraceTypeReferenceAction " + clazz);
    }
  }
}
