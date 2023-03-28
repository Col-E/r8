// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.profile.AbstractProfile;
import com.android.tools.r8.profile.AbstractProfileClassRule;
import com.android.tools.r8.profile.AbstractProfileMethodRule;
import com.android.tools.r8.profile.AbstractProfileRule;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Mutable extension of an existing profile. */
public abstract class ProfileAdditions<
    Additions extends
        ProfileAdditions<
                Additions,
                ClassRule,
                ClassRuleBuilder,
                MethodRule,
                MethodRuleBuilder,
                ProfileRule,
                Profile,
                ProfileBuilder>,
    ClassRule extends AbstractProfileClassRule,
    ClassRuleBuilder extends AbstractProfileClassRule.Builder<ClassRule>,
    MethodRule extends AbstractProfileMethodRule,
    MethodRuleBuilder extends AbstractProfileMethodRule.Builder<MethodRule, MethodRuleBuilder>,
    ProfileRule extends AbstractProfileRule,
    Profile extends AbstractProfile<ClassRule, MethodRule>,
    ProfileBuilder extends
        AbstractProfile.Builder<ClassRule, MethodRule, Profile, ProfileBuilder>> {

  public interface ProfileAdditionsBuilder {

    default ProfileAdditionsBuilder addRule(ProgramDefinition definition) {
      return addRule(definition.getReference());
    }

    default ProfileAdditionsBuilder addRule(DexReference reference) {
      if (reference.isDexType()) {
        return addClassRule(reference.asDexType());
      } else {
        assert reference.isDexMethod();
        return addMethodRule(reference.asDexMethod());
      }
    }

    ProfileAdditionsBuilder addClassRule(DexType type);

    ProfileAdditionsBuilder addMethodRule(DexMethod method);

    default void removeMovedMethodRule(ProgramMethod oldMethod, ProgramMethod newMethod) {
      removeMovedMethodRule(oldMethod.getReference(), newMethod);
    }

    void removeMovedMethodRule(DexMethod oldMethod, ProgramMethod newMethod);
  }

  protected Profile profile;

  final Map<DexType, ClassRuleBuilder> classRuleAdditions = new ConcurrentHashMap<>();
  final Map<DexMethod, MethodRuleBuilder> methodRuleAdditions = new ConcurrentHashMap<>();
  private final Set<DexMethod> methodRuleRemovals = Sets.newConcurrentHashSet();

  private final NestedMethodRuleAdditionsGraph nestedMethodRuleAdditionsGraph =
      new NestedMethodRuleAdditionsGraph();

  protected ProfileAdditions(Profile profile) {
    this.profile = profile;
  }

  public void applyIfContextIsInProfile(DexType context, Consumer<? super Additions> fn) {
    if (profile.containsClassRule(context) || classRuleAdditions.containsKey(context)) {
      fn.accept(self());
    }
  }

  public void applyIfContextIsInProfile(
      DexMethod context, Consumer<ProfileAdditionsBuilder> builderConsumer) {
    MethodRule contextMethodRule = profile.getMethodRule(context);
    if (contextMethodRule != null) {
      builderConsumer.accept(
          new ProfileAdditionsBuilder() {

            @Override
            public ProfileAdditionsBuilder addClassRule(DexType type) {
              ProfileAdditions.this.addClassRule(type);
              return this;
            }

            @Override
            public ProfileAdditionsBuilder addMethodRule(DexMethod method) {
              ProfileAdditions.this.addMethodRule(
                  method, methodRuleBuilder -> methodRuleBuilder.join(contextMethodRule));
              return this;
            }

            @Override
            public void removeMovedMethodRule(DexMethod oldMethod, ProgramMethod newMethod) {
              ProfileAdditions.this.removeMovedMethodRule(oldMethod, newMethod);
            }
          });
    } else if (methodRuleAdditions.containsKey(context)) {
      builderConsumer.accept(
          new ProfileAdditionsBuilder() {

            @Override
            public ProfileAdditionsBuilder addClassRule(DexType type) {
              ProfileAdditions.this.addClassRule(type);
              return this;
            }

            @Override
            public ProfileAdditionsBuilder addMethodRule(DexMethod method) {
              MethodRuleBuilder contextRuleBuilder = methodRuleAdditions.get(context);
              ProfileAdditions.this.addMethodRule(
                  method, methodRuleBuilder -> methodRuleBuilder.join(contextRuleBuilder));
              nestedMethodRuleAdditionsGraph.recordMethodRuleInfoFlagsLargerThan(method, context);
              return this;
            }

            @Override
            public void removeMovedMethodRule(DexMethod oldMethod, ProgramMethod newMethod) {
              ProfileAdditions.this.removeMovedMethodRule(oldMethod, newMethod);
            }
          });
    }
  }

  public Additions addClassRule(DexClass clazz) {
    addClassRule(clazz.getType());
    return self();
  }

  public void addClassRule(DexType type) {
    if (profile.containsClassRule(type)) {
      return;
    }

    // Create profile rule for class.
    classRuleAdditions.computeIfAbsent(type, this::createClassRuleBuilder);
  }

  public Additions addMethodRule(
      DexClassAndMethod method, Consumer<? super MethodRuleBuilder> methodRuleBuilderConsumer) {
    return addMethodRule(method.getReference(), methodRuleBuilderConsumer);
  }

  public Additions addMethodRule(
      DexMethod method, Consumer<? super MethodRuleBuilder> methodRuleBuilderConsumer) {
    // Create profile rule for method.
    MethodRuleBuilder methodRuleBuilder =
        methodRuleAdditions.computeIfAbsent(method, this::createMethodRuleBuilder);

    // Setup the rule.
    synchronized (methodRuleBuilder) {
      methodRuleBuilderConsumer.accept(methodRuleBuilder);
    }

    return self();
  }

  void removeMovedMethodRule(DexMethod oldMethod, ProgramMethod newMethod) {
    assert profile.containsMethodRule(oldMethod) || methodRuleAdditions.containsKey(oldMethod);
    assert methodRuleAdditions.containsKey(newMethod.getReference());
    methodRuleRemovals.add(oldMethod);
  }

  public Profile createNewProfile() {
    if (!hasAdditions()) {
      assert !hasRemovals();
      return profile;
    }

    nestedMethodRuleAdditionsGraph.propagateMethodRuleInfoFlags(methodRuleAdditions);

    // Add existing rules to new profile.
    ProfileBuilder profileBuilder = createProfileBuilder();
    profile.forEachRule(
        profileBuilder::addClassRule,
        methodRule -> {
          if (methodRuleRemovals.contains(methodRule.getReference())) {
            return;
          }
          MethodRuleBuilder methodRuleBuilder =
              methodRuleAdditions.remove(methodRule.getReference());
          if (methodRuleBuilder != null) {
            MethodRule newMethodRule = methodRuleBuilder.join(methodRule).build();
            profileBuilder.addMethodRule(newMethodRule);
          } else {
            profileBuilder.addMethodRule(methodRule);
          }
        });

    // Sort and add additions to new profile. Sorting is needed since the additions to this
    // collection may be concurrent.
    List<AbstractProfileRule> ruleAdditionsSorted =
        new ArrayList<>(classRuleAdditions.size() + methodRuleAdditions.size());
    classRuleAdditions
        .values()
        .forEach(classRuleBuilder -> ruleAdditionsSorted.add(classRuleBuilder.build()));
    methodRuleAdditions
        .values()
        .forEach(methodRuleBuilder -> ruleAdditionsSorted.add(methodRuleBuilder.build()));
    ruleAdditionsSorted.sort(getRuleComparator());
    ruleAdditionsSorted.forEach(profileBuilder::addRule);

    return profileBuilder.build();
  }

  public boolean hasAdditions() {
    return !classRuleAdditions.isEmpty() || !methodRuleAdditions.isEmpty();
  }

  private boolean hasRemovals() {
    return !methodRuleRemovals.isEmpty();
  }

  public Additions rewriteMethodReferences(Function<DexMethod, DexMethod> methodFn) {
    Additions rewrittenAdditions = create();
    assert methodRuleRemovals.isEmpty();
    rewrittenAdditions.classRuleAdditions.putAll(classRuleAdditions);
    methodRuleAdditions.forEach(
        (method, methodRuleBuilder) -> {
          DexMethod newMethod = methodFn.apply(method);
          MethodRuleBuilder existingMethodRuleBuilder =
              rewrittenAdditions.methodRuleAdditions.put(
                  newMethod, methodRuleBuilder.setMethod(newMethod));
          assert existingMethodRuleBuilder == null;
        });
    return rewrittenAdditions;
  }

  public abstract Additions create();

  public abstract ClassRuleBuilder createClassRuleBuilder(DexType type);

  public abstract MethodRuleBuilder createMethodRuleBuilder(DexMethod method);

  public abstract ProfileBuilder createProfileBuilder();

  public abstract Comparator<AbstractProfileRule> getRuleComparator();

  public abstract Additions self();

  public void setProfile(Profile profile) {
    this.profile = profile;
  }

  private class NestedMethodRuleAdditionsGraph {

    private final Map<DexMethod, Set<DexMethod>> successors = new ConcurrentHashMap<>();
    private final Map<DexMethod, Set<DexMethod>> predecessors = new ConcurrentHashMap<>();

    void recordMethodRuleInfoFlagsLargerThan(DexMethod largerFlags, DexMethod smallerFlags) {
      predecessors
          .computeIfAbsent(largerFlags, ignoreKey(Sets::newConcurrentHashSet))
          .add(smallerFlags);
      successors
          .computeIfAbsent(smallerFlags, ignoreKey(Sets::newConcurrentHashSet))
          .add(largerFlags);
    }

    void propagateMethodRuleInfoFlags(Map<DexMethod, MethodRuleBuilder> methodRuleAdditions) {
      List<DexMethod> leaves =
          successors.keySet().stream()
              .filter(method -> predecessors.getOrDefault(method, Collections.emptySet()).isEmpty())
              .collect(Collectors.toList());
      WorkList<DexMethod> worklist = WorkList.newIdentityWorkList(leaves);
      while (worklist.hasNext()) {
        DexMethod method = worklist.next();
        MethodRuleBuilder methodRuleBuilder = methodRuleAdditions.get(method);
        for (DexMethod successor : successors.getOrDefault(method, Collections.emptySet())) {
          MethodRuleBuilder successorMethodRuleBuilder = methodRuleAdditions.get(successor);
          // If this assertion fails, that means we have synthetics with multiple
          // synthesizing contexts, which are not guaranteed to be processed before the
          // synthetic itself. In that case this assertion should simply be removed.
          assert successorMethodRuleBuilder.isGreaterThanOrEqualTo(methodRuleBuilder);
          successorMethodRuleBuilder.join(methodRuleBuilder);
          // Note: no need to addIgnoringSeenSet() since the graph will not have cycles. Indeed, it
          // should never be the case that a method m2(), which is synthesized from method context
          // m1(), would itself be a synthesizing context for m1().
          worklist.addIfNotSeen(successor);
        }
      }
    }
  }
}
