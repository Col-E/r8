// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ListUtils;
import java.util.List;
import java.util.function.Consumer;

public class GenerateMainDexListRunResult
    extends SingleTestRunResult<GenerateMainDexListRunResult> {

  final TestState state;
  List<String> mainDexList;

  public GenerateMainDexListRunResult(List<String> mainDexList, TestState state) {
    super(null, null, null, state);
    this.mainDexList = mainDexList;
    this.state = state;
  }

  public List<ClassReference> getMainDexList() {
    return ListUtils.map(
        mainDexList,
        entry -> {
          assert entry.endsWith(".class");
          String binaryName = entry.substring(0, entry.length() - ".class".length());
          return Reference.classFromBinaryName(binaryName);
        });
  }

  public final GenerateMainDexListRunResult inspectMainDexClasses(
      Consumer<List<ClassReference>> consumer) {
    consumer.accept(getMainDexList());
    return self();
  }

  public GenerateMainDexListRunResult inspectDiagnosticMessages(
      Consumer<TestDiagnosticMessages> consumer) {
    consumer.accept(state.getDiagnosticsMessages());
    return self();
  }

  @Override
  protected GenerateMainDexListRunResult self() {
    return this;
  }
}
