// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackaging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class RepackagingUseRegistry extends UseRegistry {

  private final AppView<AppInfoWithLiveness> appView;
  private final RepackagingConstraintGraph constraintGraph;
  private final ProgramMethod method;

  public RepackagingUseRegistry(
      AppView<AppInfoWithLiveness> appView,
      RepackagingConstraintGraph constraintGraph,
      ProgramMethod method) {
    super(appView.dexItemFactory());
    this.appView = appView;
    this.constraintGraph = constraintGraph;
    this.method = method;
  }

  @Override
  public boolean registerInitClass(DexType type) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInvokeVirtual(DexMethod method) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInvokeDirect(DexMethod method) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInvokeStatic(DexMethod method) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInvokeInterface(DexMethod method) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInvokeSuper(DexMethod method) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInstanceFieldRead(DexField field) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInstanceFieldWrite(DexField field) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerNewInstance(DexType type) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerStaticFieldRead(DexField field) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerStaticFieldWrite(DexField field) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerTypeReference(DexType type) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }

  @Override
  public boolean registerInstanceOf(DexType type) {
    // TODO(b/165783399): Add reference-edges to the graph.
    return false;
  }
}
