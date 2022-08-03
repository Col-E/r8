// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import java.util.ArrayList;
import java.util.List;

public class HumanMethodParser extends AbstractMethodParser {

  // Values accumulated while parsing.
  private MethodAccessFlags.Builder flagBuilder;
  private DexType returnType;
  private DexType holder;
  private DexString methodName;
  private List<DexType> argTypes;
  // Resulting values.
  private DexMethod method;
  private MethodAccessFlags flags;

  public HumanMethodParser(DexItemFactory factory) {
    super(factory);
  }

  private boolean parsingFinished() {
    return method != null;
  }

  public DexMethod getMethod() {
    assert parsingFinished();
    return method;
  }

  public MethodAccessFlags getFlags() {
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
  protected void methodName(DexString name) {
    assert !parsingFinished();
    methodName = name;
  }

  @Override
  protected void methodStart() {
    flagBuilder = MethodAccessFlags.builder();
    returnType = null;
    holder = null;
    methodName = null;
    argTypes = new ArrayList<>();
    method = null;
    flags = null;
  }

  @Override
  protected void methodEnd() {
    DexProto proto = factory.createProto(returnType, argTypes);
    method = factory.createMethod(holder, proto, methodName);
    flags = flagBuilder.build();
  }

  @Override
  protected void returnType(DexType type) {
    assert !parsingFinished();
    returnType = type;
  }

  @Override
  protected void argType(DexType type) {
    assert !parsingFinished();
    argTypes.add(type);
  }
}
