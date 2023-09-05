// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.experimental.graphinfo.AnnotationGraphNode;
import com.android.tools.r8.experimental.graphinfo.ClassGraphNode;
import com.android.tools.r8.experimental.graphinfo.FieldGraphNode;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo;
import com.android.tools.r8.experimental.graphinfo.GraphEdgeInfo.EdgeKind;
import com.android.tools.r8.experimental.graphinfo.GraphNode;
import com.android.tools.r8.experimental.graphinfo.KeepRuleGraphNode;
import com.android.tools.r8.experimental.graphinfo.MethodGraphNode;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.shaking.KeepReason.ReflectiveUseFrom;
import com.android.tools.r8.utils.DequeUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class GraphReporter {

  private final AppView<?> appView;
  private final InternalOptions options;
  private final GraphConsumer keptGraphConsumer;
  private final CollectingGraphConsumer verificationGraphConsumer;

  // Canonicalization of external graph-nodes and edge info.
  private final Map<DexAnnotation, AnnotationGraphNode> annotationNodes = new IdentityHashMap<>();
  private final Map<DexType, ClassGraphNode> classNodes = new IdentityHashMap<>();
  private final Map<DexMethod, MethodGraphNode> methodNodes = new IdentityHashMap<>();
  private final Map<DexField, FieldGraphNode> fieldNodes = new IdentityHashMap<>();
  private final Map<ProguardKeepRuleBase, KeepRuleGraphNode> ruleNodes = new IdentityHashMap<>();
  private final Map<EdgeKind, GraphEdgeInfo> reasonInfo = new IdentityHashMap<>();

  GraphReporter(AppView<?> appView, GraphConsumer keptGraphConsumer) {
    this.appView = appView;
    this.options = appView.options();
    if (appView.options().testing.verifyKeptGraphInfo) {
      this.verificationGraphConsumer = new CollectingGraphConsumer(keptGraphConsumer);
      this.keptGraphConsumer = verificationGraphConsumer;
    } else {
      this.verificationGraphConsumer = null;
      this.keptGraphConsumer = keptGraphConsumer;
    }
  }

  public KeepReasonWitness fakeReportShouldNotBeUsed() {
    return KeepReasonWitness.INSTANCE;
  }

  public boolean verifyRootedPath(DexProgramClass liveType) {
    assert verificationGraphConsumer != null;
    ClassGraphNode node = getClassGraphNode(liveType.type);
    Set<GraphNode> seen = Sets.newIdentityHashSet();
    Deque<GraphNode> targets = DequeUtils.newArrayDeque(node);
    while (!targets.isEmpty()) {
      GraphNode item = targets.pop();
      if (item instanceof KeepRuleGraphNode) {
        KeepRuleGraphNode rule = (KeepRuleGraphNode) item;
        if (rule.getPreconditions().isEmpty()) {
          return true;
        }
      }
      if (seen.add(item)) {
        Map<GraphNode, Set<GraphEdgeInfo>> sources =
            verificationGraphConsumer.getSourcesTargeting(item);
        assert sources != null : "No sources set for " + item;
        assert !sources.isEmpty() : "Empty sources set for " + item;
        targets.addAll(sources.keySet());
      }
    }
    assert false : "No rooted path to " + liveType.type;
    return false;
  }

  private EdgeKind reportPrecondition(KeepRuleGraphNode keepRuleGraphNode) {
    if (keepRuleGraphNode.getPreconditions().isEmpty()) {
      return EdgeKind.KeepRule;
    }
    for (GraphNode precondition : keepRuleGraphNode.getPreconditions()) {
      reportEdge(precondition, keepRuleGraphNode, EdgeKind.KeepRulePrecondition);
    }
    return EdgeKind.ConditionalKeepRule;
  }

  KeepReasonWitness reportKeepClass(
      DexDefinition precondition, ProguardKeepRuleBase rule, DexProgramClass clazz) {
    if (keptGraphConsumer != null) {
      KeepRuleGraphNode ruleNode = getKeepRuleGraphNode(precondition, rule);
      EdgeKind edgeKind = reportPrecondition(ruleNode);
      return reportEdge(ruleNode, getClassGraphNode(clazz.type), edgeKind);
    }
    return KeepReasonWitness.INSTANCE;
  }

  KeepReasonWitness reportKeepClass(
      DexDefinition precondition, Collection<ProguardKeepRuleBase> rules, DexProgramClass clazz) {
    assert !rules.isEmpty() || !options.isShrinking();
    if (keptGraphConsumer != null) {
      for (ProguardKeepRuleBase rule : rules) {
        reportKeepClass(precondition, rule, clazz);
      }
    }
    return KeepReasonWitness.INSTANCE;
  }

  KeepReasonWitness reportKeepMethod(
      DexDefinition precondition, ProguardKeepRuleBase rule, DexEncodedMethod method) {
    if (keptGraphConsumer != null) {
      KeepRuleGraphNode ruleNode = getKeepRuleGraphNode(precondition, rule);
      EdgeKind edgeKind = reportPrecondition(ruleNode);
      return reportEdge(ruleNode, getMethodGraphNode(method.getReference()), edgeKind);
    }
    return KeepReasonWitness.INSTANCE;
  }

  KeepReasonWitness reportKeepMethod(
      DexDefinition precondition, Collection<ProguardKeepRuleBase> rules, DexEncodedMethod method) {
    assert !rules.isEmpty() || !options.isShrinking();
    if (keptGraphConsumer != null) {
      for (ProguardKeepRuleBase rule : rules) {
        reportKeepMethod(precondition, rule, method);
      }
    }
    return KeepReasonWitness.INSTANCE;
  }

  KeepReasonWitness reportKeepField(
      DexDefinition precondition, ProguardKeepRuleBase rule, DexEncodedField field) {
    if (keptGraphConsumer != null) {
      KeepRuleGraphNode ruleNode = getKeepRuleGraphNode(precondition, rule);
      EdgeKind edgeKind = reportPrecondition(ruleNode);
      return reportEdge(ruleNode, getFieldGraphNode(field.getReference()), edgeKind);
    }
    return KeepReasonWitness.INSTANCE;
  }

  KeepReasonWitness reportKeepField(
      DexDefinition precondition,
      Collection<ReflectiveUseFrom> reasons,
      Collection<ProguardKeepRuleBase> rules,
      DexEncodedField field) {
    assert !reasons.isEmpty() || !rules.isEmpty() || !options.isShrinking();
    if (keptGraphConsumer != null) {
      for (KeepReason reason : reasons) {
        registerField(field, reason);
      }
      for (ProguardKeepRuleBase rule : rules) {
        reportKeepField(precondition, rule, field);
      }
    }
    return KeepReasonWitness.INSTANCE;
  }

  @SuppressWarnings("ReferenceEquality")
  public KeepReasonWitness reportCompatKeepDefaultInitializer(ProgramMethod defaultInitializer) {
    assert defaultInitializer.getHolder().getDefaultInitializer()
        == defaultInitializer.getDefinition();
    if (keptGraphConsumer != null) {
      reportEdge(
          getClassGraphNode(defaultInitializer.getHolderType()),
          getMethodGraphNode(defaultInitializer.getReference()),
          EdgeKind.CompatibilityRule);
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportCompatKeepMethod(ProgramMethod method) {
    // TODO(b/141729349): This compat rule is from the method to itself and has not edge. Fix it.
    // The rule is stating that if the method is targeted it is live. Since such an edge does
    // not contribute to additional information in the kept graph as it stands (no distinction
    // of targeted vs live edges), there is little point in emitting it.
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportCompatInstantiated(
      DexProgramClass instantiated, ProgramMethod method) {
    if (keptGraphConsumer != null) {
      reportEdge(
          getMethodGraphNode(method.getReference()),
          getClassGraphNode(instantiated.type),
          EdgeKind.CompatibilityRule);
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportClassReferencedFrom(
      DexProgramClass clazz, ProgramDefinition context) {
    if (context.isProgramClass()) {
      return reportClassReferencedFrom(clazz, context.asProgramClass());
    } else if (context.isProgramField()) {
      return reportClassReferencedFrom(clazz, context.asProgramField());
    } else {
      assert context.isProgramMethod();
      return reportClassReferencedFrom(clazz, context.asProgramMethod());
    }
  }

  public KeepReasonWitness reportClassReferencedFrom(
      DexProgramClass clazz, DexProgramClass implementer) {
    if (keptGraphConsumer != null) {
      ClassGraphNode source = getClassGraphNode(implementer.type);
      ClassGraphNode target = getClassGraphNode(clazz.type);
      return reportEdge(source, target, EdgeKind.ReferencedFrom);
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportClassReferencedFrom(DexProgramClass clazz, ProgramMethod method) {
    if (keptGraphConsumer != null) {
      MethodGraphNode source = getMethodGraphNode(method.getReference());
      ClassGraphNode target = getClassGraphNode(clazz.type);
      return reportEdge(source, target, EdgeKind.ReferencedFrom);
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportClassReferencedFrom(DexProgramClass clazz, ProgramField field) {
    if (keptGraphConsumer != null) {
      FieldGraphNode source = getFieldGraphNode(field.getReference());
      ClassGraphNode target = getClassGraphNode(clazz.type);
      return reportEdge(source, target, EdgeKind.ReferencedFrom);
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportReachableClassInitializer(
      DexProgramClass clazz, ProgramMethod initializer) {
    if (initializer != null) {
      assert initializer.getDefinition().isClassInitializer();
      if (keptGraphConsumer != null) {
        ClassGraphNode source = getClassGraphNode(clazz.type);
        MethodGraphNode target = getMethodGraphNode(initializer.getReference());
        return reportEdge(source, target, EdgeKind.ReachableFromLiveType);
      }
    } else {
      assert !clazz.hasClassInitializer();
    }
    return KeepReasonWitness.INSTANCE;
  }

  @SuppressWarnings("ReferenceEquality")
  public KeepReasonWitness reportReachableMethodAsLive(
      DexMethod overriddenMethod, ProgramMethod derivedMethod) {
    if (keptGraphConsumer != null
        && overriddenMethod != derivedMethod.getDefinition().getReference()) {
      return reportEdge(
          getMethodGraphNode(overriddenMethod),
          getMethodGraphNode(derivedMethod.getDefinition().getReference()),
          EdgeKind.OverridingMethod);
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportLibraryMethodAsLive(
      InstantiatedObject instantiation,
      ProgramMethod derivedMethod,
      DexClass libraryOrClasspathClass) {
    // TODO(b/120959039): Report a clear reason for indirect keep of the lambda target.
    if (keptGraphConsumer != null && instantiation.isClass()) {
      return reportEdge(
          getClassGraphNode(instantiation.asClass().type),
          getMethodGraphNode(derivedMethod.getDefinition().getReference()),
          EdgeKind.IsLibraryMethod);
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness reportCompanionClass(DexProgramClass iface, DexProgramClass companion) {
    assert iface.isInterface();
    assert InterfaceDesugaringSyntheticHelper.isCompanionClassType(companion.type);
    if (keptGraphConsumer == null) {
      return KeepReasonWitness.INSTANCE;
    }
    return reportEdge(
        getClassGraphNode(iface.type), getClassGraphNode(companion.type), EdgeKind.CompanionClass);
  }

  public KeepReasonWitness reportCompanionMethod(
      DexEncodedMethod definition, DexEncodedMethod implementation) {
    assert InterfaceDesugaringSyntheticHelper.isCompanionClassType(implementation.getHolderType());
    if (keptGraphConsumer == null) {
      return KeepReasonWitness.INSTANCE;
    }
    return reportEdge(
        getMethodGraphNode(definition.getReference()),
        getMethodGraphNode(implementation.getReference()),
        EdgeKind.CompanionMethod);
  }

  private KeepReasonWitness reportEdge(GraphNode source, GraphNode target, EdgeKind kind) {
    assert keptGraphConsumer != null;
    keptGraphConsumer.acceptEdge(source, target, getEdgeInfo(kind));
    return KeepReasonWitness.INSTANCE;
  }

  /**
   * Sentinel value indicating that a keep reason has been reported.
   *
   * <p>Should only ever be returned by the graph reporter functions.
   */
  public static class KeepReasonWitness extends KeepReason {

    private static KeepReasonWitness INSTANCE = new KeepReasonWitness();

    private KeepReasonWitness() {
      // Only the reporter may create instances.
    }

    @Override
    public EdgeKind edgeKind() {
      throw new Unreachable();
    }

    @Override
    public GraphNode getSourceNode(GraphReporter graphReporter) {
      throw new Unreachable();
    }
  }

  private boolean hasKeptGraphConsumer() {
    return keptGraphConsumer != null;
  }

  private boolean skipReporting(KeepReason reason) {
    assert reason != null;
    if (reason == KeepReasonWitness.INSTANCE) {
      return true;
    }
    assert getSourceNode(reason) != null;
    return !hasKeptGraphConsumer();
  }

  public KeepReasonWitness registerInterface(DexProgramClass iface, KeepReason reason) {
    assert iface.isInterface();
    if (skipReporting(reason)) {
      return KeepReasonWitness.INSTANCE;
    }
    return registerEdge(getClassGraphNode(iface.type), reason);
  }

  public KeepReasonWitness registerClass(DexProgramClass clazz, KeepReason reason) {
    if (skipReporting(reason)) {
      return KeepReasonWitness.INSTANCE;
    }
    return registerEdge(getClassGraphNode(clazz.type), reason);
  }

  public KeepReasonWitness registerAnnotation(
      DexAnnotation annotation, ProgramDefinition annotatedItem) {
    if (hasKeptGraphConsumer()) {
      registerEdge(
          getAnnotationGraphNode(annotation, annotatedItem),
          KeepReason.annotatedOn(annotatedItem.getDefinition()));
      registerEdge(
          getClassGraphNode(annotation.getAnnotationType()),
          KeepReason.referencedInAnnotation(annotation, annotatedItem));
    }
    return KeepReasonWitness.INSTANCE;
  }

  public KeepReasonWitness registerMethod(DexEncodedMethod method, KeepReason reason) {
    if (skipReporting(reason)) {
      return KeepReasonWitness.INSTANCE;
    }
    if (reason.edgeKind() == EdgeKind.IsLibraryMethod
        && isNonProgramClass(method.getHolderType())) {
      // Don't report edges to actual library methods.
      // TODO(b/120959039): This should be dead code once no library classes are ever enqueued.
      return KeepReasonWitness.INSTANCE;
    }
    return registerEdge(getMethodGraphNode(method.getReference()), reason);
  }

  public KeepReasonWitness registerField(DexEncodedField field, KeepReason reason) {
    if (skipReporting(reason)) {
      return KeepReasonWitness.INSTANCE;
    }
    return registerEdge(getFieldGraphNode(field.getReference()), reason);
  }

  private KeepReasonWitness registerEdge(GraphNode target, KeepReason reason) {
    assert !skipReporting(reason);
    GraphNode sourceNode = getSourceNode(reason);
    // TODO(b/120959039): Make sure we do have edges to nodes deriving library nodes!
    if (!sourceNode.isLibraryNode()) {
      GraphEdgeInfo edgeInfo = getEdgeInfo(reason);
      keptGraphConsumer.acceptEdge(sourceNode, target, edgeInfo);
    }
    return KeepReasonWitness.INSTANCE;
  }

  private boolean isNonProgramClass(DexType type) {
    DexClass clazz = appView.definitionFor(type);
    return clazz == null || clazz.isNotProgramClass();
  }

  private GraphNode getSourceNode(KeepReason reason) {
    return reason.getSourceNode(this);
  }

  public GraphNode getGraphNode(DexReference reference) {
    if (reference.isDexType()) {
      return getClassGraphNode(reference.asDexType());
    }
    if (reference.isDexMethod()) {
      return getMethodGraphNode(reference.asDexMethod());
    }
    if (reference.isDexField()) {
      return getFieldGraphNode(reference.asDexField());
    }
    throw new Unreachable();
  }

  GraphEdgeInfo getEdgeInfo(KeepReason reason) {
    return getEdgeInfo(reason.edgeKind());
  }

  GraphEdgeInfo getEdgeInfo(EdgeKind kind) {
    return reasonInfo.computeIfAbsent(kind, GraphEdgeInfo::new);
  }

  private DexClass definitionFor(DexType type) {
    // The query of the graph can be outside program referenced types and should not fail.
    return appView.appInfo().definitionForWithoutExistenceAssert(type);
  }

  AnnotationGraphNode getAnnotationGraphNode(
      DexAnnotation annotation, ProgramDefinition annotatedItem) {
    return annotationNodes.computeIfAbsent(
        annotation,
        key -> {
          GraphNode annotatedNode =
              annotatedItem
                  .getReference()
                  .apply(
                      this::getClassGraphNode, this::getFieldGraphNode, this::getMethodGraphNode);
          ClassGraphNode annotationClassNode = getClassGraphNode(annotation.getAnnotationType());
          return new AnnotationGraphNode(annotatedNode, annotationClassNode);
        });
  }

  ClassGraphNode getClassGraphNode(DexType type) {
    return classNodes.computeIfAbsent(
        type,
        t -> {
          DexClass definition = definitionFor(t);
          return new ClassGraphNode(
              definition != null && definition.isNotProgramClass(),
              Reference.classFromDescriptor(t.toDescriptorString()));
        });
  }

  MethodGraphNode getMethodGraphNode(DexMethod context) {
    return methodNodes.computeIfAbsent(
        context,
        m -> {
          DexClass holderDefinition = definitionFor(context.holder);
          Builder<TypeReference> builder = ImmutableList.builder();
          for (DexType param : m.proto.parameters.values) {
            builder.add(Reference.typeFromDescriptor(param.toDescriptorString()));
          }
          return new MethodGraphNode(
              holderDefinition != null && holderDefinition.isNotProgramClass(),
              Reference.method(
                  Reference.classFromDescriptor(m.holder.toDescriptorString()),
                  m.name.toString(),
                  builder.build(),
                  m.proto.returnType.isVoidType()
                      ? null
                      : Reference.typeFromDescriptor(m.proto.returnType.toDescriptorString())));
        });
  }

  FieldGraphNode getFieldGraphNode(DexField context) {
    return fieldNodes.computeIfAbsent(
        context,
        f -> {
          DexClass holderDefinition = definitionFor(context.holder);
          return new FieldGraphNode(
              holderDefinition != null && holderDefinition.isNotProgramClass(),
              Reference.field(
                  Reference.classFromDescriptor(f.holder.toDescriptorString()),
                  f.name.toString(),
                  Reference.typeFromDescriptor(f.type.toDescriptorString())));
        });
  }

  // Due to the combined encoding of dependent rules, ala keepclassmembers and conditional keep
  // rules the conversion to a keep-rule graph node can be one of three forms:
  // 1. A non-dependent keep rule. In this case precondtion == null and the rule is not an if-rule.
  // 2. A dependent keep rule. In this case precondtion != null and rule is not an if-rule.
  // 3. A conditional keep rule. In this case rule is an if-rule, but precondition may or may not be
  //    null. In the non-null case, the precondition is the type the consequent may depend on,
  //    say T for the consequent "-keep T { f; }". It is *not* the precondition of the conditional
  //    rule.
  KeepRuleGraphNode getKeepRuleGraphNode(DexDefinition precondition, ProguardKeepRuleBase rule) {
    if (rule instanceof ProguardKeepRule) {
      Set<GraphNode> preconditions =
          precondition != null
              ? Collections.singleton(getGraphNode(precondition.getReference()))
              : Collections.emptySet();
      return ruleNodes.computeIfAbsent(rule, key -> new KeepRuleGraphNode(rule, preconditions));
    }
    if (rule instanceof ProguardIfRule) {
      ProguardIfRule ifRule = (ProguardIfRule) rule;
      assert !ifRule.getPreconditions().isEmpty();
      return ruleNodes.computeIfAbsent(
          ifRule,
          key -> {
            Set<GraphNode> preconditions = new HashSet<>(ifRule.getPreconditions().size());
            for (DexReference condition : ifRule.getPreconditions()) {
              preconditions.add(getGraphNode(condition));
            }
            return new KeepRuleGraphNode(ifRule, preconditions);
          });
    }
    throw new Unreachable("Unexpected type of keep rule: " + rule);
  }
}
