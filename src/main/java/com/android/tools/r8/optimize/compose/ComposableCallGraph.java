// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.compose;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.function.Consumer;

/**
 * A partial call graph that stores call edges to @Composable functions. By processing all the call
 * sites of a given @Composable function we can reapply arguent propagation for the @Composable
 * function.
 */
public class ComposableCallGraph {

  private final ProgramMethodMap<ComposableCallGraphNode> nodes;

  public ComposableCallGraph(ProgramMethodMap<ComposableCallGraphNode> nodes) {
    this.nodes = nodes;
  }

  public static Builder builder(AppView<AppInfoWithLiveness> appView) {
    return new Builder(appView);
  }

  public static ComposableCallGraph empty() {
    return new ComposableCallGraph(ProgramMethodMap.empty());
  }

  public void forEachNode(Consumer<ComposableCallGraphNode> consumer) {
    nodes.forEachValue(consumer);
  }

  public ProgramMethodMap<ComposableCallGraphNode> getNodes() {
    return nodes;
  }

  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final ProgramMethodMap<ComposableCallGraphNode> nodes = ProgramMethodMap.create();

    Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    public ComposableCallGraph build() {
      createCallGraphNodesForComposableFunctions();
      if (!nodes.isEmpty()) {
        addCallEdgesToComposableFunctions();
      }
      return new ComposableCallGraph(nodes);
    }

    private void createCallGraphNodesForComposableFunctions() {
      ComposeReferences rewrittenComposeReferences =
          appView
              .getComposeReferences()
              .rewrittenWithLens(appView.graphLens(), GraphLens.getIdentityLens());
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        clazz.forEachProgramDirectMethodMatching(
            method -> method.annotations().hasAnnotation(rewrittenComposeReferences.composableType),
            method -> {
              // TODO(b/302483644): Don't include kept @Composable functions, since we can't
              //  optimize them anyway.
              assert method.getAccessFlags().isStatic();
              nodes.put(method, new ComposableCallGraphNode(method, true));
            });
      }
    }

    // TODO(b/302483644): Parallelize identification of @Composable call sites.
    private void addCallEdgesToComposableFunctions() {
      // Code is fully rewritten so no need to lens rewrite in registry.
      assert appView.codeLens() == appView.graphLens();

      for (DexProgramClass clazz : appView.appInfo().classes()) {
        clazz.forEachProgramMethodMatching(
            DexEncodedMethod::hasCode,
            method -> {
              Code code = method.getDefinition().getCode();

              // TODO(b/302483644): Leverage LIR code constant pool for efficient checking.
              // TODO(b/302483644): Maybe remove the possibility of CF/DEX at this point.
              assert code.isLirCode()
                  || code.isCfCode()
                  || code.isDexCode()
                  || code.isDefaultInstanceInitializerCode()
                  || code.isThrowNullCode();

              code.registerCodeReferences(
                  method,
                  new UseRegistry<>(appView, method) {

                    private final AppView<AppInfoWithLiveness> appViewWithLiveness =
                        appView.withLiveness();

                    @Override
                    public void registerInvokeStatic(DexMethod method) {
                      ProgramMethod resolvedMethod =
                          appViewWithLiveness
                              .appInfo()
                              .unsafeResolveMethodDueToDexFormat(method)
                              .getResolvedProgramMethod();
                      if (resolvedMethod == null) {
                        return;
                      }

                      ComposableCallGraphNode callee = nodes.get(resolvedMethod);
                      if (callee == null || !callee.isComposable()) {
                        // Only record calls to Composable functions.
                        return;
                      }

                      ComposableCallGraphNode caller =
                          nodes.computeIfAbsent(
                              getContext(), context -> new ComposableCallGraphNode(context, false));
                      callee.addCaller(caller);
                    }

                    @Override
                    public void registerInitClass(DexType type) {}

                    @Override
                    public void registerInvokeDirect(DexMethod method) {}

                    @Override
                    public void registerInvokeInterface(DexMethod method) {}

                    @Override
                    public void registerInvokeSuper(DexMethod method) {}

                    @Override
                    public void registerInvokeVirtual(DexMethod method) {}

                    @Override
                    public void registerInstanceFieldRead(DexField field) {}

                    @Override
                    public void registerInstanceFieldWrite(DexField field) {}

                    @Override
                    public void registerStaticFieldRead(DexField field) {}

                    @Override
                    public void registerStaticFieldWrite(DexField field) {}

                    @Override
                    public void registerTypeReference(DexType type) {}
                  });
            });
      }
    }
  }
}
