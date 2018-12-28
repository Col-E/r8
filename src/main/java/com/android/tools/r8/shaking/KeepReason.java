// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graphinfo.GraphEdgeInfo;
import com.android.tools.r8.graphinfo.GraphEdgeInfo.EdgeKind;
import com.android.tools.r8.graphinfo.GraphNode;

// TODO(herhut): Canonicalize reason objects.
public abstract class KeepReason {

  public abstract GraphEdgeInfo.EdgeKind edgeKind();

  public abstract GraphNode getSourceNode(Enqueuer enqueuer);

  static KeepReason dueToKeepRule(ProguardKeepRule rule) {
    return new DueToKeepRule(rule);
  }

  static KeepReason dueToProguardCompatibilityKeepRule(ProguardKeepRule rule) {
    return new DueToProguardCompatibilityKeepRule(rule);
  }

  static KeepReason instantiatedIn(DexEncodedMethod method) {
    return new InstatiatedIn(method);
  }

  public static KeepReason invokedViaSuperFrom(DexEncodedMethod from) {
    return new InvokedViaSuper(from);
  }

  public static KeepReason reachableFromLiveType(DexType type) {
    return new ReachableFromLiveType(type);
  }

  public static KeepReason invokedFrom(DexEncodedMethod method) {
    return new InvokedFrom(method);
  }

  public static KeepReason invokedFromLambdaCreatedIn(DexEncodedMethod method) {
    return new InvokedFromLambdaCreatedIn(method);
  }

  public static KeepReason isLibraryMethod() {
    return new IsLibraryMethod();
  }

  public static KeepReason fieldReferencedIn(DexEncodedMethod method) {
    return new ReferencedFrom(method);
  }

  public static KeepReason referencedInAnnotation(DexItem holder) {
    return new ReferencedInAnnotation(holder);
  }

  public boolean isDueToKeepRule() {
    return false;
  }

  public boolean isDueToProguardCompatibility() {
    return false;
  }

  public ProguardKeepRule getProguardKeepRule() {
    return null;
  }

  public static KeepReason targetedBySuperFrom(DexEncodedMethod from) {
    return new TargetedBySuper(from);
  }

  private static class DueToKeepRule extends KeepReason {

    final ProguardKeepRule keepRule;

    private DueToKeepRule(ProguardKeepRule keepRule) {
      this.keepRule = keepRule;
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.KeepRule;
    }

    @Override
    public boolean isDueToKeepRule() {
      return true;
    }

    @Override
    public ProguardKeepRule getProguardKeepRule() {
      return keepRule;
    }

    @Override
    public GraphNode getSourceNode(Enqueuer enqueuer) {
      return enqueuer.getKeepRuleGraphNode(keepRule);
    }
  }

  private static class DueToProguardCompatibilityKeepRule extends DueToKeepRule {
    private DueToProguardCompatibilityKeepRule(ProguardKeepRule keepRule) {
      super(keepRule);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.CompatibilityRule;
    }

    @Override
    public boolean isDueToProguardCompatibility() {
      return true;
    }
  }

  private abstract static class BasedOnOtherMethod extends KeepReason {

    private final DexEncodedMethod method;

    private BasedOnOtherMethod(DexEncodedMethod method) {
      this.method = method;
    }

    abstract String getKind();

    @Override
    public GraphNode getSourceNode(Enqueuer enqueuer) {
      return enqueuer.getMethodGraphNode(method.method);
    }
  }

  private static class InstatiatedIn extends BasedOnOtherMethod {

    private InstatiatedIn(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.InstantiatedIn;
    }

    @Override
    String getKind() {
      return "instantiated in";
    }
  }

  private static class InvokedViaSuper extends BasedOnOtherMethod {

    private InvokedViaSuper(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.InvokedViaSuper;
    }

    @Override
    String getKind() {
      return "invoked via super from";
    }
  }

  private static class TargetedBySuper extends BasedOnOtherMethod {

    private TargetedBySuper(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.TargetedBySuper;
    }

    @Override
    String getKind() {
      return "targeted by super from";
    }
  }

  private static class InvokedFrom extends BasedOnOtherMethod {

    private InvokedFrom(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.InvokedFrom;
    }

    @Override
    String getKind() {
      return "invoked from";
    }
  }

  private static class InvokedFromLambdaCreatedIn extends BasedOnOtherMethod {

    private InvokedFromLambdaCreatedIn(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.InvokedFromLambdaCreatedIn;
    }

    @Override
    String getKind() {
      return "invoked from lambda created in";
    }
  }

  private static class ReferencedFrom extends BasedOnOtherMethod {

    private ReferencedFrom(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.ReferencedFrom;
    }

    @Override
    String getKind() {
      return "referenced from";
    }
  }

  private static class ReachableFromLiveType extends KeepReason {

    private final DexType type;

    private ReachableFromLiveType(DexType type) {
      this.type = type;
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.ReachableFromLiveType;
    }

    @Override
    public GraphNode getSourceNode(Enqueuer enqueuer) {
      return enqueuer.getClassGraphNode(type);
    }
  }

  private static class IsLibraryMethod extends KeepReason {

    private IsLibraryMethod() {
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.IsLibraryMethod;
    }

    @Override
    public GraphNode getSourceNode(Enqueuer enqueuer) {
      return null;
    }
  }

  private static class ReferencedInAnnotation extends KeepReason {

    private final DexItem holder;

    private ReferencedInAnnotation(DexItem holder) {
      this.holder = holder;
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.ReferencedInAnnotation;
    }

    @Override
    public GraphNode getSourceNode(Enqueuer enqueuer) {
      return enqueuer.getAnnotationGraphNode(holder);
    }
  }
}
