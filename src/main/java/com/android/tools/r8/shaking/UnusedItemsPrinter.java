// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class UnusedItemsPrinter {

  private static class Members {
    final List<DexEncodedField> fields = new ArrayList<>();
    final List<DexEncodedMethod> methods = new ArrayList<>();

    boolean hasMembers() {
      return !fields.isEmpty() || !methods.isEmpty();
    }

    void sort() {
      fields.sort((a, b) -> a.getReference().slowCompareTo(b.getReference()));
      methods.sort((a, b) -> a.getReference().slowCompareTo(b.getReference()));
    }
  }

  private static final String INDENT = "    ";

  static final UnusedItemsPrinter DONT_PRINT = new NopPrinter();

  private final Consumer<String> consumer;

  private DexType currentType = null;
  private Members currentMembers = new Members();

  private List<Pair<DexType, Members>> classes = new ArrayList<>();

  UnusedItemsPrinter(Consumer<String> consumer) {
    this.consumer = consumer;
  }

  void registerUnusedClass(DexProgramClass clazz) {
    assert currentType == null;
    classes.add(new Pair<>(clazz.type, null));
  }

  // Visiting methods and fields of the given clazz.
  void visiting(DexProgramClass clazz) {
    assert currentType == null;
    currentType = clazz.type;
  }

  // Visited methods and fields of the top at the clazz stack.
  void visited() {
    if (currentMembers.hasMembers()) {
      classes.add(new Pair<>(currentType, currentMembers));
      currentMembers = new Members();
    }
    currentType = null;
  }

  void registerUnusedMethod(DexEncodedMethod method) {
    currentMembers.methods.add(method);
  }

  void registerUnusedField(DexEncodedField field) {
    currentMembers.fields.add(field);
  }

  public void finished() {
    classes.sort((a, b) -> a.getFirst().slowCompareTo(b.getFirst()));
    for (Pair<DexType, Members> entry : classes) {
      DexType type = entry.getFirst();
      Members members = entry.getSecond();
      consumer.accept(type.toSourceString());
      if (members == null) {
        consumer.accept(StringUtils.LINE_SEPARATOR);
      } else {
        consumer.accept(":" + StringUtils.LINE_SEPARATOR);
        members.sort();
        members.fields.forEach(this::printUnusedField);
        members.methods.forEach(this::printUnusedMethod);
      }
    }
    classes = null;
  }

  private void append(String string) {
    consumer.accept(string);
  }

  private void newline() {
    append(StringUtils.LINE_SEPARATOR);
  }

  private void printUnusedMethod(DexEncodedMethod method) {
    append(INDENT);
    String accessFlags = method.accessFlags.toString();
    if (!accessFlags.isEmpty()) {
      append(accessFlags);
      append(" ");
    }
    append(method.method.proto.returnType.toSourceString());
    append(" ");
    append(method.method.name.toSourceString());
    append("(");
    for (int i = 0; i < method.method.proto.parameters.values.length; i++) {
      if (i != 0) {
        append(",");
      }
      append(method.method.proto.parameters.values[i].toSourceString());
    }
    append(")");
    newline();
  }

  private void printUnusedField(DexEncodedField field) {
    append(INDENT);
    String accessFlags = field.accessFlags.toString();
    if (!accessFlags.isEmpty()) {
      append(accessFlags);
      append(" ");
    }
    append(field.field.type.toSourceString());
    append(" ");
    append(field.field.name.toSourceString());
    newline();
  }

  // Empty implementation to silently ignore printing dead code.
  private static class NopPrinter extends UnusedItemsPrinter {

    public NopPrinter() {
      super(null);
    }

    @Override
    void registerUnusedClass(DexProgramClass clazz) {
      // Intentionally left empty.
    }

    @Override
    void visiting(DexProgramClass clazz) {
      // Intentionally left empty.
    }

    @Override
    void visited() {
      // Intentionally left empty.
    }

    @Override
    void registerUnusedMethod(DexEncodedMethod method) {
      // Intentionally left empty.
    }

    @Override
    void registerUnusedField(DexEncodedField field) {
      // Intentionally left empty.
    }

    @Override
    public void finished() {
      // Intentionally left empty.
    }
  }
}
