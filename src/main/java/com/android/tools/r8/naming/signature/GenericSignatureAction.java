// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.signature;

/**
 * Actions triggered by the generic signature parser.
 */
// TODO(b/129925954): Deprecate this once ...graph.GenericSignature is ready and rewriter is
//   reimplemented based on the internal encoding and transformation logic.
public interface GenericSignatureAction<T> {

  enum ParserPosition {
    CLASS_SUPER_OR_INTERFACE_ANNOTATION,
    ENCLOSING_INNER_OR_TYPE_ANNOTATION,
    MEMBER_ANNOTATION
  }

  void parsedSymbol(char symbol);

  void parsedIdentifier(String identifier);

  T parsedTypeName(String name, ParserPosition isTopLevel);

  T parsedInnerTypeName(T enclosingType, String name);

  void start();

  void stop();
}
