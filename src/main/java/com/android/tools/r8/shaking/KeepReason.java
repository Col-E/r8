// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo;
import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo.EdgeKind;
import com.android.tools.r8.experimental.graphinfo.GraphNode;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;

// TODO(herhut): Canonicalize reason objects.
public abstract class KeepReason {

  public abstract GraphEdgeInfo.EdgeKind edgeKind();

  public abstract GraphNode getSourceNode(GraphReporter graphReporter);

  static KeepReason annotatedOn(DexDefinition definition) {
    return new AnnotatedOn(definition);
  }

  static KeepReason instantiatedIn(DexEncodedMethod method) {
    return new InstantiatedIn(method);
  }

  static KeepReason instantiatedIn(ProgramMethod method) {
    return new InstantiatedIn(method.getDefinition());
  }

  public static KeepReason invokedViaSuperFrom(DexEncodedMethod from) {
    return new InvokedViaSuper(from);
  }

  public static KeepReason invokedViaSuperFrom(ProgramMethod from) {
    return new InvokedViaSuper(from.getDefinition());
  }

  public static KeepReason reachableFromLiveType(DexType type) {
    return new ReachableFromLiveType(type);
  }

  public static KeepReason invokedFrom(DexProgramClass holder, DexEncodedMethod method) {
    return new InvokedFrom(holder, method);
  }

  public static KeepReason invokedFrom(ProgramMethod context) {
    return invokedFrom(context.getHolder(), context.getDefinition());
  }

  public static KeepReason invokedFromLambdaCreatedIn(ProgramMethod method) {
    return new InvokedFromLambdaCreatedIn(method.getDefinition());
  }

  public static KeepReason fieldReferencedIn(ProgramMethod method) {
    return new ReferencedFrom(method.getDefinition());
  }

  public static KeepReason referencedInAnnotation(
      DexAnnotation annotation, ProgramDefinition annotatedItem) {
    return new ReferencedInAnnotation(annotation, annotatedItem);
  }

  public boolean isDueToKeepRule() {
    return false;
  }

  public boolean isDueToReflectiveUse() {
    return false;
  }

  public static KeepReason targetedBySuperFrom(ProgramMethod from) {
    return new TargetedBySuper(from.getDefinition());
  }

  public static ReflectiveUseFrom reflectiveUseIn(ProgramMethod method) {
    return new ReflectiveUseFrom(method.getDefinition());
  }

  public static KeepReason methodHandleReferencedIn(ProgramMethod method) {
    return new MethodHandleReferencedFrom(method.getDefinition());
  }

  private abstract static class BasedOnOtherMethod extends KeepReason {

    private final DexEncodedMethod method;

    private BasedOnOtherMethod(DexEncodedMethod method) {
      this.method = method;
    }

    abstract String getKind();

    public DexMethod getMethod() {
      return method.getReference();
    }

    @Override
    public GraphNode getSourceNode(GraphReporter graphReporter) {
      return graphReporter.getMethodGraphNode(method.getReference());
    }
  }

  public static class InstantiatedIn extends BasedOnOtherMethod {

    private InstantiatedIn(DexEncodedMethod method) {
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

    @SuppressWarnings("ReferenceEquality")
    private InvokedFrom(DexProgramClass holder, DexEncodedMethod method) {
      super(method);
      assert holder.type == method.getHolderType();
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
    public GraphNode getSourceNode(GraphReporter graphReporter) {
      return graphReporter.getClassGraphNode(type);
    }
  }

  private static class ReferencedInAnnotation extends KeepReason {

    private final DexAnnotation annotation;
    private final ProgramDefinition annotatedItem;

    private ReferencedInAnnotation(DexAnnotation annotation, ProgramDefinition annotatedItem) {
      this.annotation = annotation;
      this.annotatedItem = annotatedItem;
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.ReferencedInAnnotation;
    }

    @Override
    public GraphNode getSourceNode(GraphReporter graphReporter) {
      return graphReporter.getAnnotationGraphNode(annotation, annotatedItem);
    }
  }

  private static class AnnotatedOn extends KeepReason {

    private final DexDefinition holder;

    private AnnotatedOn(DexDefinition holder) {
      this.holder = holder;
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.AnnotatedOn;
    }

    @Override
    public GraphNode getSourceNode(GraphReporter graphReporter) {
      if (holder.isDexClass()) {
        return graphReporter.getClassGraphNode(holder.asDexClass().type);
      } else if (holder.isDexEncodedField()) {
        return graphReporter.getFieldGraphNode(holder.asDexEncodedField().getReference());
      } else {
        assert holder.isDexEncodedMethod();
        return graphReporter.getMethodGraphNode(holder.asDexEncodedMethod().getReference());
      }
    }
  }

  public static class ReflectiveUseFrom extends BasedOnOtherMethod {

    private ReflectiveUseFrom(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public boolean isDueToReflectiveUse() {
      return true;
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.ReflectiveUseFrom;
    }

    @Override
    String getKind() {
      return "reflective use in";
    }
  }

  private static class MethodHandleReferencedFrom extends BasedOnOtherMethod {

    private MethodHandleReferencedFrom(DexEncodedMethod method) {
      super(method);
    }

    @Override
    public EdgeKind edgeKind() {
      return EdgeKind.MethodHandleUseFrom;
    }

    @Override
    String getKind() {
      return "method handle referenced from";
    }
  }
}
