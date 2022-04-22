// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.MemberType;

public class CfAssignability {

  // Based on https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.2.
  public static boolean isAssignable(FrameType source, FrameType target, AppView<?> appView) {
    if (target.isTop()) {
      return true;
    }
    if (source.isTop()) {
      return false;
    }
    if (source.isWide() != target.isWide()) {
      return false;
    }
    if (target.isOneWord() || target.isTwoWord()) {
      return true;
    }
    if (source.isUninitializedThis() && target.isUninitializedThis()) {
      return true;
    }
    if (source.isUninitializedNew() && target.isUninitializedNew()) {
      // TODO(b/168190134): Allow for picking the offset from the target if not set.
      DexType uninitializedNewTypeSource = source.getUninitializedNewType();
      DexType uninitializedNewTypeTarget = target.getUninitializedNewType();
      return uninitializedNewTypeSource == null
          || uninitializedNewTypeTarget == null
          || uninitializedNewTypeSource == uninitializedNewTypeTarget;
    }
    // TODO(b/168190267): Clean-up the lattice.
    DexItemFactory factory = appView.dexItemFactory();
    if (!source.isInitialized()
        && target.isInitialized()
        && target.getInitializedType() == factory.objectType) {
      return true;
    }
    if (source.isInitialized() && target.isInitialized()) {
      // Both are instantiated types and we resort to primitive tyoe/java type hierarchy checking.
      return isAssignable(source.getInitializedType(), target.getInitializedType(), appView);
    }
    return false;
  }

  // Rules found at https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.2
  public static boolean isAssignable(DexType source, DexType target, AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    source = byteCharShortOrBooleanToInt(source, factory);
    target = byteCharShortOrBooleanToInt(target, factory);
    if (source == target) {
      return true;
    }
    if (source.isPrimitiveType() || target.isPrimitiveType()) {
      return false;
    }
    // Both are now references - everything is assignable to object.
    if (target == factory.objectType) {
      return true;
    }
    // isAssignable(null, class(_, _)).
    // isAssignable(null, arrayOf(_)).
    if (source == DexItemFactory.nullValueType) {
      return true;
    }
    if (target.isArrayType() != target.isArrayType()) {
      return false;
    }
    if (target.isArrayType()) {
      return isAssignable(
          target.toArrayElementType(factory), target.toArrayElementType(factory), appView);
    }
    // TODO(b/166570659): Do a sub-type check that allows for missing classes in hierarchy.
    return MemberType.fromDexType(source) == MemberType.fromDexType(target);
  }

  private static DexType byteCharShortOrBooleanToInt(DexType type, DexItemFactory factory) {
    // byte, char, short and boolean has verification type int.
    if (type.isByteType() || type.isCharType() || type.isShortType() || type.isBooleanType()) {
      return factory.intType;
    }
    return type;
  }
}
