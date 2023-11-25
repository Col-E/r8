// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.shaking.ProguardIfRule;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import java.util.List;

@KeepForApi
public class InlinableStaticFinalFieldPreconditionDiagnostic implements Diagnostic {

  private final ProguardIfRule rule;
  private final Collection<FieldReference> fields;

  public InlinableStaticFinalFieldPreconditionDiagnostic(
      ProguardIfRule rule, List<DexField> fields) {
    this.rule = rule;
    this.fields = ListUtils.map(fields, DexField::asFieldReference);
  }

  @Override
  public Origin getOrigin() {
    return rule.getOrigin();
  }

  @Override
  public Position getPosition() {
    return rule.getPosition();
  }

  @Override
  public String getDiagnosticMessage() {
    return StringUtils.lines(
            "Rule precondition matches static final fields javac has inlined.",
            "Such rules are unsound as the shrinker cannot infer the inlining precisely.",
            "Consider adding !static to the rule.",
            "Matched fields are: ")
        + StringUtils.joinLines(ListUtils.map(fields, FieldReferenceUtils::toSourceString));
  }
}
