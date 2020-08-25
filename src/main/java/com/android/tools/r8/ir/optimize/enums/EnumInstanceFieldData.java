// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import java.util.Map;

/*
 * My instances represent the values of an enum field for each of the enum instance.
 * For example:
 * <code> enum E {
 *  A(10),B(20);
 *  int f;
 *  public E(int f) {
 *     this.f = f;
 *   }
 * }</code>
 * <p> The EnumFieldData for the field E#f is A -> 10, B -> 20.
 * The EnumFieldData may be unknown, for example, if the enum fields are not set with constants.
 */
public abstract class EnumInstanceFieldData {

  public abstract boolean isUnknown();

  public boolean isKnown() {
    return !isUnknown();
  }

  public EnumInstanceFieldKnownData asEnumFieldKnownData() {
    return null;
  }

  public static class EnumInstanceFieldUnknownData extends EnumInstanceFieldData {

    private static final EnumInstanceFieldUnknownData INSTANCE = new EnumInstanceFieldUnknownData();

    public static EnumInstanceFieldUnknownData getInstance() {
      return INSTANCE;
    }

    private EnumInstanceFieldUnknownData() {}

    @Override
    public boolean isUnknown() {
      return true;
    }
  }

  public abstract static class EnumInstanceFieldKnownData extends EnumInstanceFieldData {

    @Override
    public boolean isUnknown() {
      return false;
    }

    public abstract boolean isOrdinal();

    public abstract boolean isMapping();

    @Override
    public EnumInstanceFieldKnownData asEnumFieldKnownData() {
      return this;
    }

    public EnumInstanceFieldMappingData asEnumFieldMappingData() {
      return null;
    }
  }

  public static class EnumInstanceFieldOrdinalData extends EnumInstanceFieldKnownData {
    @Override
    public boolean isOrdinal() {
      return true;
    }

    @Override
    public boolean isMapping() {
      return false;
    }
  }

  public static class EnumInstanceFieldMappingData extends EnumInstanceFieldKnownData {
    private final Map<DexField, AbstractValue> mapping;

    public EnumInstanceFieldMappingData(Map<DexField, AbstractValue> mapping) {
      this.mapping = mapping;
    }

    @Override
    public boolean isOrdinal() {
      return false;
    }

    @Override
    public boolean isMapping() {
      return true;
    }

    @Override
    public EnumInstanceFieldMappingData asEnumFieldMappingData() {
      return this;
    }

    public AbstractValue getData(DexField field) {
      return mapping.get(field);
    }
  }
}
