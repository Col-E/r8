// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature;

/**
 * Actions triggered by the generic signature parser.
 */
public interface GenericSignatureAction<T> {

  enum ParserPosition {
    TOP_LEVEL,
    INNER_ENCLOSING_OR_TYPE_ARGUMENT
  }

  public void parsedSymbol(char symbol);

  public void parsedIdentifier(String identifier);

  public T parsedTypeName(String name, ParserPosition isTopLevel);

  public T parsedInnerTypeName(T enclosingType, String name);

  public void start();

  public void stop();
}
