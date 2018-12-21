// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graphinfo;

import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.shaking.ProguardKeepRule;

@Keep
public final class KeepRuleGraphNode extends GraphNode {

  private final ProguardKeepRule rule;

  public KeepRuleGraphNode(ProguardKeepRule rule) {
    super(false);
    assert rule != null;
    this.rule = rule;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof KeepRuleGraphNode && ((KeepRuleGraphNode) o).rule == rule);
  }

  @Override
  public int hashCode() {
    return rule.hashCode();
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
