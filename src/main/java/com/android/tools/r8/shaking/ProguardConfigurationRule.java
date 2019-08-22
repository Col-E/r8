// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Iterables;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public abstract class ProguardConfigurationRule extends ProguardClassSpecification {

  private boolean used = false;

  ProguardConfigurationRule(
      Origin origin,
      Position position,
      String source,
      ProguardTypeMatcher classAnnotation,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      ProguardTypeMatcher inheritanceAnnotation,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules) {
    super(origin, position, source, classAnnotation, classAccessFlags, negatedClassAccessFlags,
        classTypeNegated, classType, classNames, inheritanceAnnotation, inheritanceClassName,
        inheritanceIsExtends, memberRules);
  }

  public boolean isUsed() {
    return used;
  }

  public void markAsUsed() {
    used = true;
  }

  abstract String typeString();

  String modifierString() {
    return null;
  }

  public boolean applyToNonProgramClasses() {
    return false;
  }

  protected Iterable<ProguardWildcard> getWildcards() {
    List<ProguardMemberRule> memberRules = getMemberRules();
    return Iterables.concat(
        ProguardTypeMatcher.getWildcardsOrEmpty(getClassAnnotation()),
        ProguardClassNameList.getWildcardsOrEmpty(getClassNames()),
        ProguardTypeMatcher.getWildcardsOrEmpty(getInheritanceAnnotation()),
        ProguardTypeMatcher.getWildcardsOrEmpty(getInheritanceClassName()),
        memberRules != null
            ? memberRules.stream()
                .map(ProguardMemberRule::getWildcards)
                .flatMap(it -> StreamSupport.stream(it.spliterator(), false))
                ::iterator
            : Collections::emptyIterator
    );
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardConfigurationRule)) {
      return false;
    }
    ProguardConfigurationRule that = (ProguardConfigurationRule) o;
    if (used != that.used) {
      return false;
    }
    if (!Objects.equals(typeString(), that.typeString())) {
      return false;
    }
    if (!Objects.equals(modifierString(), that.modifierString())) {
      return false;
    }
    return super.equals(that);
  }

  @Override
  public int hashCode() {
    int result = 3 * typeString().hashCode();
    result = 3 * result + (used ? 1 : 0);
    String modifier = modifierString();
    result = 3 * result + (modifier != null ? modifier.hashCode() : 0);
    return result + super.hashCode();
  }

  @Override
  protected StringBuilder append(StringBuilder builder, boolean includeMemberRules) {
    builder.append("-");
    builder.append(typeString());
    StringUtils.appendNonEmpty(builder, ",", modifierString(), null);
    builder.append(' ');
    super.append(builder, includeMemberRules);
    return builder;
  }
}
