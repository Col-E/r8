// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.List;

@KeepForApi
public class CheckEnumUnboxedDiagnostic implements Diagnostic {

  private final List<String> messages;

  CheckEnumUnboxedDiagnostic(List<String> messages) {
    this.messages = messages;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The origin of a -checkenumunboxed failure is not unique. (The whole app is to blame.) */
  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  /** The position of a -checkenumunboxed failure is always unknown. */
  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder("Enum unboxing checks failed.");
    for (String message : messages) {
      builder.append(System.lineSeparator());
      builder.append(message);
    }
    return builder.toString();
  }

  public static class Builder {

    private final ImmutableList.Builder<String> messagesBuilder = ImmutableList.builder();

    public Builder addFailedEnums(List<DexProgramClass> failed) {
      failed.sort(Comparator.comparing(DexClass::getType));
      for (DexProgramClass clazz : failed) {
        messagesBuilder.add("Enum " + clazz.getTypeName() + " was not unboxed.");
      }
      return this;
    }

    public CheckEnumUnboxedDiagnostic build() {
      return new CheckEnumUnboxedDiagnostic(messagesBuilder.build());
    }
  }
}
