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
import com.android.tools.r8.utils.SetUtils;
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
  private final Set<DexMethod> methodRuleRemovals = SetUtils.newConcurrentHashSet();

  private final NestedMethodRuleAdditionsGraph<MethodRule, MethodRuleBuilder>
      nestedMethodRuleAdditionsGraph = new NestedMethodRuleAdditionsGraph<>();

  protected ProfileAdditions(Profile profile) {
    this.profile = profile;
  }

  public void applyIfContextIsInProfile(
      DexType context, Consumer<ProfileAdditionsBuilder> builderConsumer) {
    if (profile.containsClassRule(context) || classRuleAdditions.containsKey(context)) {
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
                  method, AbstractProfileMethodRule.Builder::setIsStartup);
              return this;
            }

            @Override
            public void removeMovedMethodRule(DexMethod oldMethod, ProgramMethod newMethod) {
              ProfileAdditions.this.removeMovedMethodRule(oldMethod, newMethod);
            }
          });
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

  private Additions addMethodRule(
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

    // Assert that there are no cycles in the propagation graph. If there are any cycles, this
    // likely means that we have mutually recursive synthetics, which could be unintentional.
    // Note that this algorithm correctly deals with cycles, and thus this assertion can simply be
    // disabled to allow cycles.
    assert nestedMethodRuleAdditionsGraph.verifyNoCycles();
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

  public static class NestedMethodRuleAdditionsGraph<
      MethodRule extends AbstractProfileMethodRule,
      MethodRuleBuilder extends AbstractProfileMethodRule.Builder<MethodRule, MethodRuleBuilder>> {

    private final Map<DexMethod, Set<DexMethod>> successors = new ConcurrentHashMap<>();
    private final Map<DexMethod, Set<DexMethod>> predecessors = new ConcurrentHashMap<>();

    public void recordMethodRuleInfoFlagsLargerThan(DexMethod largerFlags, DexMethod smallerFlags) {
      predecessors
          .computeIfAbsent(largerFlags, ignoreKey(Sets::newConcurrentHashSet))
          .add(smallerFlags);
      successors
          .computeIfAbsent(smallerFlags, ignoreKey(Sets::newConcurrentHashSet))
          .add(largerFlags);
    }

    public void propagateMethodRuleInfoFlags(
        Map<DexMethod, MethodRuleBuilder> methodRuleAdditions) {
      WorkList.newIdentityWorkList(successors.keySet())
          .process(
              (method, worklist) -> {
                MethodRuleBuilder methodRuleBuilder = methodRuleAdditions.get(method);
                for (DexMethod successor :
                    successors.getOrDefault(method, Collections.emptySet())) {
                  MethodRuleBuilder successorMethodRuleBuilder = methodRuleAdditions.get(successor);
                  successorMethodRuleBuilder.join(
                      methodRuleBuilder,
                      // If the successor's flags changed, then reprocess the successor to propagate
                      // its flags to the successors of the successor.
                      () -> worklist.addIgnoringSeenSet(successor));
                }
              });
    }

    public boolean verifyNoCycles() {
      Set<DexMethod> seen = Sets.newIdentityHashSet();
      for (DexMethod method : successors.keySet()) {
        if (seen.add(method)) {
          seen.addAll(verifyNoCyclesStartingFrom(method));
        }
      }
      return true;
    }

    public Set<DexMethod> verifyNoCyclesStartingFrom(DexMethod root) {
      Set<DexMethod> seen = Sets.newIdentityHashSet();
      Set<DexMethod> stack = Sets.newIdentityHashSet();
      WorkList<DexMethod> worklist = WorkList.newIdentityWorkList(root);
      worklist.process(
          current -> {
            if (seen.add(current)) {
              // Seen for the first time, append to stack and continue the search for a cycle from
              // the successors.
              stack.add(current);
              worklist.addFirstIgnoringSeenSet(current);
              for (DexMethod successor : successors.getOrDefault(current, Collections.emptySet())) {
                assert !stack.contains(successor) : "Found a cycle";
                worklist.addFirstIfNotSeen(successor);
              }
            } else {
              // Backtracking, remove current method from stack since we are done exploring the
              // (transitive) successors.
              boolean removed = stack.remove(current);
              assert removed;
            }
          });
      assert stack.isEmpty();
      return worklist.getSeenSet();
    }
  }
}
