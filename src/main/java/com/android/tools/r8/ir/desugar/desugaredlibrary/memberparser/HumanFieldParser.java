// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;

public class HumanFieldParser extends AbstractFieldParser {

  // Values accumulated while parsing.
  private FieldAccessFlags.Builder flagBuilder;
  private DexType fieldType;
  private DexType holder;
  private DexString fieldName;
  // Resulting values.
  private DexField field;
  private FieldAccessFlags flags;

  public HumanFieldParser(DexItemFactory factory) {
    super(factory);
  }

  private boolean parsingFinished() {
    return field != null;
  }

  public DexField getField() {
    assert parsingFinished();
    return field;
  }

  public FieldAccessFlags getFlags() {
    assert parsingFinished();
    return flags;
  }

  @Override
  protected void modifier(int access) {
    assert !parsingFinished();
    flagBuilder.set(access);
  }

  @Override
  protected void holderType(DexType type) {
    assert !parsingFinished();
    holder = type;
  }

  @Override
  protected void fieldName(DexString name) {
    assert !parsingFinished();
    fieldName = name;
  }

  @Override
  protected void fieldStart() {
    flagBuilder = FieldAccessFlags.builder();
    fieldType = null;
    holder = null;
    fieldName = null;
    field = null;
    flags = null;
  }

  @Override
  protected void fieldEnd() {
    field = factory.createField(holder, fieldType, fieldName);
    flags = flagBuilder.build();
  }

  @Override
  protected void fieldType(DexType type) {
    assert !parsingFinished();
    fieldType = type;
  }
}
