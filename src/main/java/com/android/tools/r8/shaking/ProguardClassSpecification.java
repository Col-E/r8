// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public abstract class ProguardClassSpecification {

  public abstract static class
  Builder<C extends ProguardClassSpecification, B extends Builder<C, B>> {

    protected Origin origin;
    protected Position start;
    protected Position end;
    protected String source;
    private final ImmutableList.Builder<ProguardTypeMatcher> classAnnotations =
        ImmutableList.builder();
    protected ProguardAccessFlags classAccessFlags = new ProguardAccessFlags();
    protected ProguardAccessFlags negatedClassAccessFlags = new ProguardAccessFlags();
    protected boolean classTypeNegated = false;
    protected ProguardClassType classType;
    protected ProguardClassNameList classNames;
    private final ImmutableList.Builder<ProguardTypeMatcher> inheritanceAnnotations =
        ImmutableList.builder();
    protected ProguardTypeMatcher inheritanceClassName;
    protected boolean inheritanceIsExtends = false;
    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    protected List<ProguardMemberRule> memberRules = new LinkedList<>();

    protected Builder() {
      this(Origin.unknown(), Position.UNKNOWN);
    }

    protected Builder(Origin origin, Position start) {
      this.origin = origin;
      this.start = start;
    }

    public abstract C build();

    public abstract B self();

    public B setOrigin(Origin origin) {
      this.origin = origin;
      return self();
    }

    public B setStart(Position start) {
      this.start = start;
      return self();
    }

    public B setEnd(Position end) {
      this.end = end;
      return self();
    }

    public B setSource(String source) {
      this.source = source;
      return self();
    }

    public Position getPosition() {
      if (start == null) {
        return Position.UNKNOWN;
      }
      if (end == null || !((start instanceof TextPosition) && (end instanceof TextPosition))) {
        return start;
      }
      return new TextRange((TextPosition) start, (TextPosition) end);
    }

    public List<ProguardMemberRule> getMemberRules() {
      return memberRules;
    }

    public B setMemberRules(List<ProguardMemberRule> memberRules) {
      this.memberRules = memberRules;
      return self();
    }

    public boolean getInheritanceIsExtends() {
      return inheritanceIsExtends;
    }

    public B setInheritanceIsExtends(boolean inheritanceIsExtends) {
      this.inheritanceIsExtends = inheritanceIsExtends;
      return self();
    }

    public boolean hasInheritanceClassName() {
      return inheritanceClassName != null;
    }

    public ProguardTypeMatcher getInheritanceClassName() {
      return inheritanceClassName;
    }

    public B setInheritanceClassName(ProguardTypeMatcher inheritanceClassName) {
      this.inheritanceClassName = inheritanceClassName;
      return self();
    }

    public B addInheritanceAnnotations(List<ProguardTypeMatcher> inheritanceAnnotations) {
      assert inheritanceAnnotations != null;
      this.inheritanceAnnotations.addAll(inheritanceAnnotations);
      return self();
    }

    public List<ProguardTypeMatcher> buildInheritanceAnnotations() {
      return inheritanceAnnotations.build();
    }

    public ProguardClassNameList getClassNames() {
      return classNames;
    }

    public B setClassNames(ProguardClassNameList classNames) {
      this.classNames = classNames;
      return self();
    }

    public boolean hasClassType() {
      return classType != null;
    }

    public ProguardClassType getClassType() {
      return classType;
    }

    public B setClassType(ProguardClassType classType) {
      this.classType = classType;
      return self();
    }

    public boolean getClassTypeNegated() {
      return classTypeNegated;
    }

    public B setClassTypeNegated(boolean classTypeNegated) {
      this.classTypeNegated = classTypeNegated;
      return self();
    }

    public ProguardAccessFlags getClassAccessFlags() {
      return classAccessFlags;
    }

    public B setClassAccessFlags(ProguardAccessFlags flags) {
      classAccessFlags = flags;
      return self();
    }

    public ProguardAccessFlags getNegatedClassAccessFlags() {
      return negatedClassAccessFlags;
    }

    public B setNegatedClassAccessFlags(ProguardAccessFlags flags) {
      negatedClassAccessFlags = flags;
      return self();
    }

    public B addClassAnnotation(ProguardTypeMatcher classAnnotation) {
      classAnnotations.add(classAnnotation);
      return self();
    }

    public B addClassAnnotations(List<ProguardTypeMatcher> classAnnotations) {
      assert classAnnotations != null;
      this.classAnnotations.addAll(classAnnotations);
      return self();
    }

    public List<ProguardTypeMatcher> buildClassAnnotations() {
      return classAnnotations.build();
    }

    protected void matchAllSpecification() {
      setClassNames(ProguardClassNameList.singletonList(ProguardTypeMatcher.defaultAllMatcher()));
      setMemberRules(ImmutableList.of(ProguardMemberRule.defaultKeepAllRule()));
    }
  }

  private final Origin origin;
  private final Position position;
  private final String source;
  private final List<ProguardTypeMatcher> classAnnotations;
  private final ProguardAccessFlags classAccessFlags;
  private final ProguardAccessFlags negatedClassAccessFlags;
  private final boolean classTypeNegated;
  private final ProguardClassType classType;
  private final ProguardClassNameList classNames;
  private final List<ProguardTypeMatcher> inheritanceAnnotations;
  private final ProguardTypeMatcher inheritanceClassName;
  private final boolean inheritanceIsExtends;
  private final List<ProguardMemberRule> memberRules;

  @SuppressWarnings("ReferenceEquality")
  protected ProguardClassSpecification(
      Origin origin,
      Position position,
      String source,
      List<ProguardTypeMatcher> classAnnotations,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      List<ProguardTypeMatcher> inheritanceAnnotations,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules) {
    assert classType != null;
    assert origin != null;
    assert position != null;
    assert source != null || origin != Origin.unknown();
    this.origin = origin;
    this.position = position;
    this.source = source;
    this.classAnnotations = classAnnotations;
    this.classAccessFlags = classAccessFlags;
    this.negatedClassAccessFlags = negatedClassAccessFlags;
    this.classTypeNegated = classTypeNegated;
    this.classType = classType;
    this.classNames = classNames;
    this.inheritanceAnnotations = inheritanceAnnotations;
    this.inheritanceClassName = inheritanceClassName;
    this.inheritanceIsExtends = inheritanceIsExtends;
    this.memberRules = memberRules;
  }

  public Origin getOrigin() {
    return origin;
  }

  public Position getPosition() {
    return position;
  }

  public String getSource() {
    return source;
  }

  public List<ProguardMemberRule> getMemberRules() {
    return memberRules;
  }

  public boolean getInheritanceIsExtends() {
    return inheritanceIsExtends;
  }

  public boolean getInheritanceIsImplements() {
    return !inheritanceIsExtends;
  }

  public boolean hasInheritanceClassName() {
    return inheritanceClassName != null;
  }

  public ProguardTypeMatcher getInheritanceClassName() {
    return inheritanceClassName;
  }

  public List<ProguardTypeMatcher> getInheritanceAnnotations() {
    return inheritanceAnnotations;
  }

  public ProguardClassNameList getClassNames() {
    return classNames;
  }

  public ProguardClassType getClassType() {
    return classType;
  }

  public boolean getClassTypeNegated() {
    return classTypeNegated;
  }

  public ProguardAccessFlags getClassAccessFlags() {
    return classAccessFlags;
  }

  public ProguardAccessFlags getNegatedClassAccessFlags() {
    return negatedClassAccessFlags;
  }

  public List<ProguardTypeMatcher> getClassAnnotations() {
    return classAnnotations;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardClassSpecification)) {
      return false;
    }
    ProguardClassSpecification that = (ProguardClassSpecification) o;

    if (classTypeNegated != that.classTypeNegated) {
      return false;
    }
    if (inheritanceIsExtends != that.inheritanceIsExtends) {
      return false;
    }
    if (!Objects.equals(classAnnotations, that.classAnnotations)) {
      return false;
    }
    if (!classAccessFlags.equals(that.classAccessFlags)) {
      return false;
    }
    if (!negatedClassAccessFlags.equals(that.negatedClassAccessFlags)) {
      return false;
    }
    if (classType != that.classType) {
      return false;
    }
    if (!classNames.equals(that.classNames)) {
      return false;
    }
    if (!Objects.equals(inheritanceAnnotations, that.inheritanceAnnotations)) {
      return false;
    }
    if (!Objects.equals(inheritanceClassName, that.inheritanceClassName)) {
      return false;
    }
    return memberRules.equals(that.memberRules);
  }

  @Override
  public int hashCode() {
    // Used multiplier 3 to avoid too much overflow when computing hashCode.
    int result = classAnnotations.hashCode();
    result = 3 * result + classAccessFlags.hashCode();
    result = 3 * result + negatedClassAccessFlags.hashCode();
    result = 3 * result + (classTypeNegated ? 1 : 0);
    result = 3 * result + (classType != null ? classType.hashCode() : 0);
    result = 3 * result + classNames.hashCode();
    result = 3 * result + inheritanceAnnotations.hashCode();
    result = 3 * result + (inheritanceClassName != null ? inheritanceClassName.hashCode() : 0);
    result = 3 * result + (inheritanceIsExtends ? 1 : 0);
    result = 3 * result + memberRules.hashCode();
    return result;
  }

  protected StringBuilder append(StringBuilder builder) {
    appendAnnotations(classAnnotations, builder);
    boolean hasAccessFlags = StringUtils.appendNonEmpty(builder, null, classAccessFlags, null);
    boolean hasNegatedAccessFlags =
        StringUtils.appendNonEmpty(
            builder, "!", negatedClassAccessFlags.toString().replace(" ", " !"), null);
    boolean needsSpaceBeforeClassType = hasAccessFlags || hasNegatedAccessFlags;
    if (needsSpaceBeforeClassType) {
      builder.append(' ');
    }
    if (classTypeNegated) {
      builder.append('!');
    }
    builder.append(classType);
    builder.append(' ');
    classNames.writeTo(builder);
    if (hasInheritanceClassName()) {
      builder.append(' ').append(inheritanceIsExtends ? "extends" : "implements").append(' ');
      appendAnnotations(inheritanceAnnotations, builder);
      builder.append(inheritanceClassName);
    }
    if (!memberRules.isEmpty()) {
      builder.append(" {").append(System.lineSeparator());
      memberRules.forEach(memberRule -> {
        builder.append("  ");
        builder.append(memberRule);
        builder.append(";").append(System.lineSeparator());
      });
      builder.append("}");
    }
    return builder;
  }

  private static void appendAnnotations(
      List<ProguardTypeMatcher> annotations, StringBuilder builder) {
    if (!annotations.isEmpty()) {
      Iterator<ProguardTypeMatcher> annotationIterator = annotations.iterator();
      builder.append('@').append(annotationIterator.next());
      while (annotationIterator.hasNext()) {
        builder.append(" @").append(annotationIterator.next());
      }
      builder.append(' ');
    }
  }

  @Override
  public String toString() {
    return append(new StringBuilder()).toString();
  }
}
