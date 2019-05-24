// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.graphinfo;

import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.google.common.base.Objects;
import java.util.Set;

@Keep
public final class ConditionalKeepRuleGraphNode extends GraphNode {

  private final ProguardKeepRule rule;
  private final Set<GraphNode> preconditions;

  public ConditionalKeepRuleGraphNode(ProguardKeepRule rule, Set<GraphNode> preconditions) {
    super(false);
    assert rule != null;
    assert preconditions != null;
    assert !preconditions.isEmpty();
    this.rule = rule;
    this.preconditions = preconditions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConditionalKeepRuleGraphNode)) {
      return false;
    }
    ConditionalKeepRuleGraphNode other = (ConditionalKeepRuleGraphNode) o;
    return other.rule == rule && other.preconditions.equals(preconditions);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(rule, preconditions);
  }

  public Origin getOrigin() {
    return rule.getOrigin();
  }

  public Position getPosition() {
    return rule.getPosition();
  }

  public String getContent() {
    return rule.getSource();
  }

  public Set<GraphNode> getPreconditions() {
    return preconditions;
  }

  /**
   * Get an identity string determining this keep rule.
   *
   * <p>The identity string is typically the source-file (if present) followed by the line number.
   * {@code <keep-rule-file>:<keep-rule-start-line>:<keep-rule-start-column>}.
   */
  @Override
  public String toString() {
    return (getOrigin() == Origin.unknown() ? getContent() : getOrigin())
        + ":"
        + shortPositionInfo(getPosition());
  }

  private static String shortPositionInfo(Position position) {
    if (position instanceof TextRange) {
      TextPosition start = ((TextRange) position).getStart();
      return start.getLine() + ":" + start.getColumn();
    }
    if (position instanceof TextPosition) {
      TextPosition start = (TextPosition) position;
      return start.getLine() + ":" + start.getColumn();
    }
    return position.getDescription();
  }
}
