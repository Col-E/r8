// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepClassReference;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.keeprules.KeepEdgeSplitter.Holder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class PgRule {
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
    return (StringBuilder builder, KeepClassReference classReference) -> {
      assert classReference.isBindingReference()
          || classReference.asClassNamePattern().equals(classNamePattern);
      KeepRuleExtractor.printClassName(builder, classNamePattern);
    };
  }

  void printKeepOptions(StringBuilder builder) {
    KeepRuleExtractor.printKeepOptions(builder, options);
  }

  public void printRule(StringBuilder builder) {
    KeepRuleExtractor.printHeader(builder, metaInfo);
    printCondition(builder);
    printConsequence(builder);
  }

  void printCondition(StringBuilder builder) {
    if (hasCondition()) {
      builder.append("-if ");
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
  ;

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
   * Representation of an unconditional rule to keep a class.
   *
   * <pre>
   *   -keep class <holder>
   * </pre>
   *
   * and with no dependencies / back-references.
   */
  static class PgUnconditionalClassRule extends PgRule {
    final KeepQualifiedClassNamePattern holderNamePattern;
    final KeepItemPattern holderPattern;

    public PgUnconditionalClassRule(KeepEdgeMetaInfo metaInfo, KeepOptions options, Holder holder) {
      super(metaInfo, options);
      this.holderNamePattern = holder.namePattern;
      this.holderPattern = holder.itemPattern;
    }

    @Override
    String getConsequenceKeepType() {
      return "-keep";
    }

    @Override
    List<String> getTargetMembers() {
      return Collections.emptyList();
    }

    @Override
    void printTargetHolder(StringBuilder builder) {
      KeepRuleExtractor.printClassHeader(
          builder, holderPattern, classReferencePrinter(holderNamePattern));
    }

    @Override
    void printTargetMember(StringBuilder builder, String memberReference) {
      throw new KeepEdgeException("Unreachable");
    }
  }

  abstract static class PgConditionalRuleBase extends PgRule {
    final KeepItemPattern classCondition;
    final KeepItemPattern classTarget;
    final Map<String, KeepMemberPattern> memberPatterns;
    final List<String> memberConditions;

    public PgConditionalRuleBase(
        KeepEdgeMetaInfo metaInfo,
        KeepOptions options,
        Holder classCondition,
        Holder classTarget,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions) {
      super(metaInfo, options);
      this.classCondition = classCondition.itemPattern;
      this.classTarget = classTarget.itemPattern;
      this.memberPatterns = memberPatterns;
      this.memberConditions = memberConditions;
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
      KeepRuleExtractor.printClassHeader(builder, classCondition, this::printClassName);
    }

    @Override
    void printConditionMember(StringBuilder builder, String member) {
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      KeepRuleExtractor.printMemberClause(builder, memberPattern);
    }

    @Override
    void printTargetHolder(StringBuilder builder) {
      KeepRuleExtractor.printClassHeader(builder, classTarget, this::printClassName);
    }

    void printClassName(StringBuilder builder, KeepClassReference clazz) {
      KeepRuleExtractor.printClassName(builder, clazz.asClassNamePattern());
    }
  }

  /**
   * Representation of conditional rules but without dependencies between condition and target.
   *
   * <pre>
   *   -if class <class-condition> { <member-conditions> }
   *   -keep class <class-target>
   * </pre>
   *
   * and with no dependencies / back-references.
   */
  static class PgConditionalClassRule extends PgConditionalRuleBase {

    public PgConditionalClassRule(
        KeepEdgeMetaInfo metaInfo,
        KeepOptions options,
        Holder classCondition,
        Holder classTarget,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions) {
      super(metaInfo, options, classCondition, classTarget, memberPatterns, memberConditions);
    }

    @Override
    String getConsequenceKeepType() {
      return "-keep";
    }

    @Override
    List<String> getTargetMembers() {
      return Collections.emptyList();
    }

    @Override
    void printTargetMember(StringBuilder builder, String member) {
      throw new KeepEdgeException("Unreachable");
    }
  }

  /**
   * Representation of conditional rules but without dependencies between condition and target.
   *
   * <pre>
   *   -if class <class-condition> { <member-conditions> }
   *   -keepclassmembers class <class-target> { <member-targets> }
   * </pre>
   *
   * and with no dependencies / back-references.
   */
  static class PgConditionalMemberRule extends PgConditionalRuleBase {

    private final List<String> memberTargets;
    private final boolean classAndMembers;

    public PgConditionalMemberRule(
        KeepEdgeMetaInfo metaInfo,
        KeepOptions options,
        Holder classCondition,
        Holder classTarget,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions,
        List<String> memberTargets,
        boolean classAndMembers) {
      super(metaInfo, options, classCondition, classTarget, memberPatterns, memberConditions);
      this.memberTargets = memberTargets;
      this.classAndMembers = classAndMembers;
    }

    @Override
    String getConsequenceKeepType() {
      return classAndMembers ? "-keepclasseswithmembers" : "-keepclassmembers";
    }

    @Override
    List<String> getTargetMembers() {
      return memberTargets;
    }

    @Override
    void printTargetMember(StringBuilder builder, String member) {
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      KeepRuleExtractor.printMemberClause(builder, memberPattern);
    }
  }

  abstract static class PgDependentRuleBase extends PgRule {

    final KeepQualifiedClassNamePattern holderNamePattern;
    final KeepItemPattern holderPattern;
    final Map<String, KeepMemberPattern> memberPatterns;
    final List<String> memberConditions;

    public PgDependentRuleBase(
        KeepEdgeMetaInfo metaInfo,
        Holder holder,
        KeepOptions options,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions) {
      super(metaInfo, options);
      this.holderNamePattern = holder.namePattern;
      this.holderPattern = holder.itemPattern;
      this.memberPatterns = memberPatterns;
      this.memberConditions = memberConditions;
    }

    int nextBackReferenceNumber = 1;
    String holderBackReferencePattern;
    // TODO(b/248408342): Support back-ref to members too.

    private StringBuilder addBackRef(StringBuilder backReferenceBuilder) {
      return backReferenceBuilder.append('<').append(nextBackReferenceNumber++).append('>');
    }

    @Override
    List<String> getConditionMembers() {
      return memberConditions;
    }

    @Override
    void printConditionHolder(StringBuilder b) {
      KeepRuleExtractor.printClassHeader(
          b,
          holderPattern,
          (builder, classReference) -> {
            StringBuilder backReference = new StringBuilder();
            if (holderNamePattern.isAny()) {
              addBackRef(backReference);
              builder.append('*');
            } else {
              printPackagePrefix(builder, holderNamePattern.getPackagePattern(), backReference);
              printSimpleClassName(builder, holderNamePattern.getNamePattern(), backReference);
            }
            holderBackReferencePattern = backReference.toString();
          });
    }

    @Override
    void printConditionMember(StringBuilder builder, String member) {
      // TODO(b/248408342): Support back-ref to member instances too.
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      KeepRuleExtractor.printMemberClause(builder, memberPattern);
    }

    @Override
    void printTargetHolder(StringBuilder builder) {
      KeepRuleExtractor.printClassHeader(
          builder,
          holderPattern,
          (b, reference) -> {
            assert reference.isBindingReference()
                || reference.asClassNamePattern().equals(holderNamePattern);
            b.append(holderBackReferencePattern);
          });
    }

    private StringBuilder printPackagePrefix(
        StringBuilder builder,
        KeepPackagePattern packagePattern,
        StringBuilder backReferenceBuilder) {
      if (packagePattern.isAny()) {
        addBackRef(backReferenceBuilder).append('.');
        return builder.append("**.");
      }
      if (packagePattern.isTop()) {
        return builder;
      }
      assert packagePattern.isExact();
      String exactPackage = packagePattern.getExactPackageAsString();
      backReferenceBuilder.append(exactPackage).append('.');
      return builder.append(exactPackage).append('.');
    }

    private StringBuilder printSimpleClassName(
        StringBuilder builder,
        KeepUnqualfiedClassNamePattern namePattern,
        StringBuilder backReferenceBuilder) {
      if (namePattern.isAny()) {
        addBackRef(backReferenceBuilder);
        return builder.append('*');
      }
      assert namePattern.isExact();
      String exactName = namePattern.asExact().getExactNameAsString();
      backReferenceBuilder.append(exactName);
      return builder.append(exactName);
    }
  }

  /**
   * Representation of a conditional class rule that is match/instance dependent.
   *
   * <pre>
   *   -if class <class-pattern> { <member-condition>* }
   *   -keep class <class-backref>
   * </pre>
   */
  static class PgDependentClassRule extends PgDependentRuleBase {

    public PgDependentClassRule(
        KeepEdgeMetaInfo metaInfo,
        Holder holder,
        KeepOptions options,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions) {
      super(metaInfo, holder, options, memberPatterns, memberConditions);
    }

    @Override
    String getConsequenceKeepType() {
      return "-keep";
    }

    @Override
    boolean hasCondition() {
      return true;
    }

    @Override
    List<String> getTargetMembers() {
      return Collections.emptyList();
    }

    @Override
    void printTargetMember(StringBuilder builder, String member) {
      throw new KeepEdgeException("Unreachable");
    }
  }

  /**
   * Representation of a conditional member rule that is match/instance dependent.
   *
   * <pre>
   *   -if class <class-pattern> { <member-condition>* }
   *   -keepclassmembers class <class-backref> { <member-target | member-backref>* }
   * </pre>
   *
   * or if the only condition is the class itself, just:
   *
   * <pre>
   *   -keepclassmembers <class-pattern> { <member-target> }
   * </pre>
   */
  static class PgDependentMembersRule extends PgDependentRuleBase {

    final List<String> memberTargets;
    final boolean classAndMembers;

    public PgDependentMembersRule(
        KeepEdgeMetaInfo metaInfo,
        Holder holder,
        KeepOptions options,
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberConditions,
        List<String> memberTargets,
        boolean classAndMembers) {
      super(metaInfo, holder, options, memberPatterns, memberConditions);
      assert !memberTargets.isEmpty();
      this.memberTargets = memberTargets;
      this.classAndMembers = classAndMembers;
    }

    @Override
    boolean hasCondition() {
      return !memberConditions.isEmpty() && !classAndMembers;
    }

    @Override
    String getConsequenceKeepType() {
      return classAndMembers ? "-keepclasseswithmembers" : "-keepclassmembers";
    }

    @Override
    List<String> getTargetMembers() {
      return memberTargets;
    }

    @Override
    void printTargetHolder(StringBuilder builder) {
      if (hasCondition()) {
        super.printTargetHolder(builder);
      } else {
        KeepRuleExtractor.printClassHeader(
            builder, holderPattern, classReferencePrinter(holderNamePattern));
      }
    }

    @Override
    void printTargetMember(StringBuilder builder, String member) {
      // TODO(b/248408342): Support back-ref to member instances too.
      KeepMemberPattern memberPattern = memberPatterns.get(member);
      KeepRuleExtractor.printMemberClause(builder, memberPattern);
    }
  }
}
