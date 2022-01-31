// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.function.BiConsumer;

class AndroidApiLevelDatabaseHelper {

  static void visitAdditionalKnownApiReferences(
      DexItemFactory factory, BiConsumer<DexReference, AndroidApiLevel> apiLevelConsumer) {
    // StringBuilder and StringBuffer lack api definitions for the exact same methods in
    // api-versions.xml. See b/216587554 for related error.
    for (DexType type : new DexType[] {factory.stringBuilderType, factory.stringBufferType}) {
      apiLevelConsumer.accept(
          factory.createMethod(type, factory.createProto(factory.intType), "capacity"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.intType), "codePointAt"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.intType), "codePointBefore"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.intType, factory.intType),
              "codePointCount"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.voidType, factory.intType), "ensureCapacity"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(
                  factory.voidType,
                  factory.intType,
                  factory.intType,
                  factory.charArrayType,
                  factory.intType),
              "getChars"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.stringType), "indexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.stringType, factory.intType),
              "indexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.intType, factory.stringType), "lastIndexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.stringType, factory.intType),
              "lastIndexOf"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.intType, factory.intType, factory.intType),
              "offsetByCodePoints"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.voidType, factory.intType, factory.charType),
              "setCharAt"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.voidType, factory.intType), "setLength"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type, factory.createProto(factory.stringType, factory.intType), "substring"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(
              type,
              factory.createProto(factory.stringType, factory.intType, factory.intType),
              "substring"),
          AndroidApiLevel.B);
      apiLevelConsumer.accept(
          factory.createMethod(type, factory.createProto(factory.voidType), "trimToSize"),
          AndroidApiLevel.B);
    }
  }
}
