// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@KeepForApi
public class AssumeNoSideEffectsRuleForObjectMembersDiagnostic implements Diagnostic {

  private final List<MethodReference> methods;
  private final Origin origin;
  private final Position position;

  private AssumeNoSideEffectsRuleForObjectMembersDiagnostic(
      List<MethodReference> methods, Origin origin, Position position) {
    this.methods = methods;
    this.origin = origin;
    this.position = position;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    Iterator<MethodReference> iterator = methods.iterator();
    StringBuilder message =
        new StringBuilder("The -assumenosideeffects rule matches the following method(s) ")
            .append("on java.lang.Object: ")
            .append(MethodReferenceUtils.toSourceStringWithoutHolderAndReturnType(iterator.next()));
    while (iterator.hasNext()) {
      MethodReference method = iterator.next();
      message
          .append(iterator.hasNext() ? ", " : " and ")
          .append(MethodReferenceUtils.toSourceStringWithoutHolderAndReturnType(method));
    }
    return message
        .append(". ")
        .append("This is most likely not intended. ")
        .append("Consider specifying the methods more precisely.")
        .toString();
  }

  public static class Builder {

    private final List<MethodReference> methods = new ArrayList<>();
    private Origin origin;
    private Position position;

    public Builder() {}

    public Builder addMatchedMethods(Set<DexMethod> methods) {
      for (DexMethod method : methods) {
        this.methods.add(method.asMethodReference());
      }
      return this;
    }

    public Builder setOrigin(Origin origin) {
      this.origin = origin;
      return this;
    }

    public Builder setPosition(Position position) {
      this.position = position;
      return this;
    }

    public AssumeNoSideEffectsRuleForObjectMembersDiagnostic build() {
      return new AssumeNoSideEffectsRuleForObjectMembersDiagnostic(methods, origin, position);
    }
  }
}
