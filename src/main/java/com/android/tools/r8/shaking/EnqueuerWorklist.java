// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.BiPredicate;

public class EnqueuerWorklist {

  public static class Action {

    public enum Kind {
      MARK_REACHABLE_DIRECT,
      MARK_REACHABLE_VIRTUAL,
      MARK_REACHABLE_INTERFACE,
      MARK_REACHABLE_SUPER,
      MARK_REACHABLE_FIELD,
      MARK_INSTANTIATED,
      MARK_METHOD_LIVE,
      MARK_METHOD_KEPT,
      MARK_FIELD_KEPT
    }

    final Kind kind;
    final DexItem target;
    final DexItem context;
    final KeepReason reason;

    private Action(Kind kind, DexItem target, DexItem context, KeepReason reason) {
      this.kind = kind;
      this.target = target;
      this.context = context;
      this.reason = reason;
    }
  }

  private final AppView<?> appView;
  private final Queue<Action> queue = new ArrayDeque<>();

  private final boolean restrictToProguardCompatibilityRules;

  private EnqueuerWorklist(AppView<?> appView, boolean restrictToProguardCompatibilityRules) {
    this.appView = appView;
    this.restrictToProguardCompatibilityRules = restrictToProguardCompatibilityRules;
  }

  public static EnqueuerWorklist createWorklist(AppView<?> appView) {
    return new EnqueuerWorklist(appView, false);
  }

  public static EnqueuerWorklist createProguardCompatibilityWorklist(AppView<?> appView) {
    return new EnqueuerWorklist(appView, true);
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public Action poll() {
    return queue.poll();
  }

  public void transferTo(
      EnqueuerWorklist worklist, BiPredicate<DexEncodedMethod, KeepReason> filter) {
    while (!queue.isEmpty()) {
      Action action = queue.poll();
      if (action.kind == Action.Kind.MARK_METHOD_LIVE) {
        DexEncodedMethod method = (DexEncodedMethod) action.target;
        if (!filter.test(method, action.reason)) {
          continue;
        }
      }
      worklist.queue.add(action);
    }
  }

  void enqueueMarkReachableDirectAction(DexMethod method, KeepReason reason) {
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    queue.add(new Action(Action.Kind.MARK_REACHABLE_DIRECT, method, null, reason));
  }

  void enqueueMarkReachableVirtualAction(DexMethod method, KeepReason reason) {
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    queue.add(new Action(Action.Kind.MARK_REACHABLE_VIRTUAL, method, null, reason));
  }

  void enqueueMarkReachableInterfaceAction(DexMethod method, KeepReason reason) {
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    queue.add(new Action(Action.Kind.MARK_REACHABLE_INTERFACE, method, null, reason));
  }

  void enqueueMarkReachableSuperAction(DexMethod method, DexEncodedMethod from) {
    queue.add(new Action(Action.Kind.MARK_REACHABLE_SUPER, method, from, null));
  }

  public void enqueueMarkReachableFieldAction(
      DexProgramClass clazz, DexEncodedField field, KeepReason reason) {
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    assert field.field.holder == clazz.type;
    queue.add(new Action(Action.Kind.MARK_REACHABLE_FIELD, field, null, reason));
  }

  void enqueueMarkInstantiatedAction(DexProgramClass clazz, KeepReason reason) {
    assert !clazz.isInterface() || clazz.accessFlags.isAnnotation();
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    queue.add(new Action(Action.Kind.MARK_INSTANTIATED, clazz, null, reason));
  }

  void enqueueMarkMethodLiveAction(
      DexProgramClass clazz, DexEncodedMethod method, KeepReason reason) {
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    assert method.method.holder == clazz.type;
    queue.add(new Action(Action.Kind.MARK_METHOD_LIVE, method, null, reason));
  }

  void enqueueMarkMethodKeptAction(DexEncodedMethod method, KeepReason reason) {
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    assert method.isProgramMethod(appView);
    queue.add(new Action(Action.Kind.MARK_METHOD_KEPT, method, null, reason));
  }

  void enqueueMarkFieldKeptAction(DexEncodedField field, KeepReason reason) {
    assert !restrictToProguardCompatibilityRules || reason.isDueToProguardCompatibility();
    assert field.isProgramField(appView);
    queue.add(new Action(Action.Kind.MARK_FIELD_KEPT, field, null, reason));
  }
}
