// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import static com.android.tools.r8.keepanno.keeprules.RulePrintingUtils.printClassHeader;
import static com.android.tools.r8.keepanno.keeprules.RulePrintingUtils.printMemberClause;

import com.android.tools.r8.keepanno.ast.KeepClassReference;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor.Holder;
import com.android.tools.r8.keepanno.keeprules.RulePrinter.BackReferencePrinter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class PgRule {
  public enum TargetKeepKind {
    JUST_MEMBERS(RulePrintingUtils.KEEP_CLASS_MEMBERS),
    CLASS_OR_MEMBERS(RulePrintingUtils.KEEP),
    CLASS_AND_MEMBERS(RulePrintingUtils.KEEP_CLASSES_WITH_MEMBERS),
    CHECK_DISCARD(RulePrintingUtils.CHECK_DISCARD);

    private final String ruleKind;

    TargetKeepKind(String ruleKind) {
      this.ruleKind = ruleKind;
    }

    String getKeepRuleKind() {
      return ruleKind;
    }

    boolean isKeepKind() {
      return this != CHECK_DISCARD;
    }
  }

  private static void printNonEmptyMembersPatternAsDefaultInitWorkaround(
      StringBuilder builder, TargetKeepKind kind) {
    if (kind.isKeepKind()) {
      // If no members is given, compat R8 and legacy full mode will implicitly keep <init>().
      // Add a keep of finalize which is a library method that would be kept in any case.
      builder.append(" { void finalize(); }");
    }
  }

  private final KeepEdgeMetaInfo metaInfo;
  private final KeepOptions options;

  private PgRule(KeepEdgeMetaInfo metaInfo, KeepOptions options) {
    this.metaInfo = metaInfo;
    this.options = options;
  }

  // Helper to print the class-name pattern in a class-item.
  // The item is assumed to either be a binding (where the binding is a class with
  // the supplied class-name pattern), or a class-item that has the class-name pattern itself (e.g.,
  // without a binding indirection).
  public static BiConsumer<StringBuilder, KeepClassReference> classReferencePrinter(
      KeepQualifiedClassNamePattern classNamePattern) {
    return (builder, classReference) -> {
      assert classReference.isBindingReference()
          || classReference.asClassNamePattern().equals(classNamePattern);
      RulePrintingUtils.printClassName(
          classNamePattern, RulePrinter.withoutBackReferences(builder));
    };
  }

  void printKeepOptions(StringBuilder builder) {
    RulePrintingUtils.printKeepOptions(builder, options);
  }

  public void printRule(StringBuilder builder) {
    RulePrintingUtils.printHeader(builder, metaInfo);
    printCondition(builder);
    printConsequence(builder);
  }

  void printCondition(StringBuilder builder) {
    if (hasCondition()) {
      builder.append(RulePrintingUtils.IF).append(' ');
      printConditionHolder(builder);
      List<String> members = getConditionMembers();
      if (!members.isEmpty()) {
        builder.append(" {");
        for (String member : members) {
          builder.append(' ');
          printConditionMember(builder, member);
        }
        builder.append(" }");
      }
      builder.append(' ');
    }
  }

  void printConsequence(StringBuilder builder) {
    builder.append(getConsequenceKeepType());
    printKeepOptions(builder);
    builder.append(' ');
    printTargetHolder(builder);
    List<String> members = getTargetMembers();
    if (!members.isEmpty()) {
      builder.append(" {");
      for (String member : members) {
        builder.append(' ');
        printTargetMember(builder, member);
      }
      builder.append(" }");
    }
  }

  boolean hasCondition() {
    return false;
  }

  List<String> getConditionMembers() {
    throw new KeepEdgeException("Unreachable");
  }

  abstract String getConsequenceKeepType();

  abstract List<String> getTargetMembers();

  void printConditionHolder(StringBuilder builder) {
    throw new KeepEdgeException("Unreachable");
  }

  void printConditionMember(StringBuilder builder, String member) {
    throw new KeepEdgeException("Unreachable");
  }

  abstract void printTargetHolder(StringBuilder builder);

  abstract void printTargetMember(StringBuilder builder, String member);

  /**
   * Representation of an unconditional rule to keep a class and methods.
   *
   * <pre>
   *   -keep[classeswithmembers] class <holder> [{ <members> }]
   * </pre>
   *
   * and with no dependencies / back-references.
   */
  static class PgUnconditionalRule extends PgRule {
    private final KeepQualifiedClassNamePattern holderNamePattern;
    private final KeepItemPattern holderPattern;
    private final TargetKeepKind targetKeepKind;
    private final List<String> targetMembers;
    private final Map<String, KeepMemberPattern> memberPatterns;

    public PgUnconditionalRule(
        KeepEdgeMetaInfo metaInfo,
        Holder holder,
        KeepOptions options,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> targetMembers,
        TargetKeepKind targetKeepKind) {
      super(metaInfo, options);
      assert !targetKeepKind.equals(TargetKeepKind.JUST_MEMBERS);
      this.holderNamePattern = holder.namePattern;
      this.holderPattern = holder.itemPattern;
      this.targetKeepKind = targetKeepKind;
      this.memberPatterns = memberPatterns;
      this.targetMembers = targetMembers;
    }

    @Override
    String getConsequenceKeepType() {
      return targetKeepKind.getKeepRuleKind();
    }

    @Override
    List<String> getTargetMembers() {
      return targetMembers;
    }

    @Override
    void printTargetHolder(StringBuilder builder) {
      printClassHeader(builder, holderPattern, classReferencePrinter(holderNamePattern));
      if (getTargetMembers().isEmpty()) {
        printNonEmptyMembersPatternAsDefaultInitWorkaround(builder, targetKeepKind);
      }
    }

    @Override
    void printTargetMember(StringBuilder builder, String memberReference) {
      KeepMemberPattern memberPattern = memberPatterns.get(memberReference);
      printMemberClause(memberPattern, RulePrinter.withoutBackReferences(builder));
    }
  }

  /**
   * Representation of conditional rules but without dependencies between condition and target.
   *
   * <pre>
   *   -if class <class-condition> [{ <member-conditions> }]
   *   -keepX class <class-target> [{ <member-targets> }]
   * </pre>
   *
   * and with no dependencies / back-references.
   */
  static class PgConditionalRule extends PgRule {

    final KeepItemPattern classCondition;
    final KeepItemPattern classTarget;
    final Map<String, KeepMemberPattern> memberPatterns;
    final List<String> memberConditions;
    private final List<String> memberTargets;
    private final TargetKeepKind keepKind;

    public PgConditionalRule(
        KeepEdgeMetaInfo metaInfo,
        KeepOptions options,
        Holder classCondition,
        Holder classTarget,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions,
        List<String> memberTargets,
        TargetKeepKind keepKind) {
      super(metaInfo, options);
      this.classCondition = classCondition.itemPattern;
      this.classTarget = classTarget.itemPattern;
      this.memberPatterns = memberPatterns;
      this.memberConditions = memberConditions;
      this.memberTargets = memberTargets;
      this.keepKind = keepKind;
    }

    @Override
    boolean hasCondition() {
      return true;
    }

    @Override
    List<String> getConditionMembers() {
      return memberConditions;
    }

    @Override
    void printConditionHolder(StringBuilder builder) {
      printClassHeader(builder, classCondition, this::printClassName);
    }

    @Override
    void printConditionMember(StringBuilder builder, String member) {
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      printMemberClause(memberPattern, RulePrinter.withoutBackReferences(builder));
    }

    @Override
    void printTargetHolder(StringBuilder builder) {
      printClassHeader(builder, classTarget, this::printClassName);
      if (getTargetMembers().isEmpty()) {
        PgRule.printNonEmptyMembersPatternAsDefaultInitWorkaround(builder, keepKind);
      }
    }

    @Override
    String getConsequenceKeepType() {
      return keepKind.getKeepRuleKind();
    }

    @Override
    List<String> getTargetMembers() {
      return memberTargets;
    }

    @Override
    void printTargetMember(StringBuilder builder, String member) {
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      printMemberClause(memberPattern, RulePrinter.withoutBackReferences(builder));
    }

    private void printClassName(StringBuilder builder, KeepClassReference clazz) {
      RulePrintingUtils.printClassName(
          clazz.asClassNamePattern(), RulePrinter.withoutBackReferences(builder));
    }
  }

  /**
   * Representation of a conditional rule that is match/instance dependent.
   *
   * <pre>
   *   -if class <class-pattern> [{ <member-condition>* }]
   *   -keepX class <class-backref> [{ <member-target | member-backref>* }]
   * </pre>
   *
   * or if the only condition is the class itself and targeting members, just:
   *
   * <pre>
   *   -keepclassmembers <class-pattern> { <member-target> }
   * </pre>
   */
  static class PgDependentMembersRule extends PgRule {

    private final KeepQualifiedClassNamePattern holderNamePattern;
    private final KeepItemPattern holderPattern;
    private final Map<String, KeepMemberPattern> memberPatterns;
    private final List<String> memberConditions;
    private final List<String> memberTargets;
    private final TargetKeepKind keepKind;

    private int nextBackReferenceNumber = 1;
    private String holderBackReferencePattern = null;
    private Map<String, String> membersBackReferencePatterns = new HashMap<>();

    public PgDependentMembersRule(
        KeepEdgeMetaInfo metaInfo,
        Holder holder,
        KeepOptions options,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions,
        List<String> memberTargets,
        TargetKeepKind keepKind) {
      super(metaInfo, options);
      this.holderNamePattern = holder.namePattern;
      this.holderPattern = holder.itemPattern;
      this.memberPatterns = memberPatterns;
      this.memberConditions = memberConditions;
      this.memberTargets = memberTargets;
      this.keepKind = keepKind;
    }

    private int getNextBackReferenceNumber() {
      return nextBackReferenceNumber++;
    }

    @Override
    boolean hasCondition() {
      // We can avoid an if-rule if the condition is simply the class and the target is just
      // members.
      boolean canUseDependentRule =
          memberConditions.isEmpty() && keepKind == TargetKeepKind.JUST_MEMBERS;
      return !canUseDependentRule;
    }

    @Override
    String getConsequenceKeepType() {
      return keepKind.getKeepRuleKind();
    }

    @Override
    List<String> getConditionMembers() {
      return memberConditions;
    }

    @Override
    List<String> getTargetMembers() {
      return memberTargets;
    }

    @Override
    void printConditionHolder(StringBuilder b) {
      printClassHeader(
          b,
          holderPattern,
          (builder, classReference) -> {
            BackReferencePrinter printer =
                RulePrinter.withBackReferences(b, this::getNextBackReferenceNumber);
            RulePrintingUtils.printClassName(holderNamePattern, printer);
            holderBackReferencePattern = printer.getBackReference();
          });
    }

    @Override
    void printConditionMember(StringBuilder builder, String member) {
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      BackReferencePrinter printer =
          RulePrinter.withBackReferences(builder, this::getNextBackReferenceNumber);
      printMemberClause(memberPattern, printer);
      membersBackReferencePatterns.put(member, printer.getBackReference());
    }

    @Override
    void printTargetHolder(StringBuilder builder) {
      printClassHeader(
          builder,
          holderPattern,
          (b, reference) -> {
            assert reference.isBindingReference()
                || reference.asClassNamePattern().equals(holderNamePattern);
            if (hasCondition()) {
              b.append(holderBackReferencePattern);
            } else {
              assert holderBackReferencePattern == null;
              RulePrintingUtils.printClassName(
                  holderNamePattern, RulePrinter.withoutBackReferences(builder));
            }
          });
      if (getTargetMembers().isEmpty()) {
        PgRule.printNonEmptyMembersPatternAsDefaultInitWorkaround(builder, keepKind);
      }
    }

    @Override
    void printTargetMember(StringBuilder builder, String member) {
      if (hasCondition()) {
        String backref = membersBackReferencePatterns.get(member);
        if (backref != null) {
          builder.append(backref);
          return;
        }
      }
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      printMemberClause(memberPattern, RulePrinter.withoutBackReferences(builder));
    }
  }
}
