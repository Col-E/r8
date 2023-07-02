// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.dexitembasedstring;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Specific subclass for Record field names. Record field names are specific DexString instances
 * used at the Java class file level for Records which encode the record field names in a single
 * String separated by semi-colon. The computation is able to minify and prune such names.
 *
 * <p>Example: record Person(String name, int age) {} The string is: name;age
 *
 * <p>The JVM normally generates the Record field names string such as it includes each field name
 * in the string. However, bytecode manipulation tools may rewrite the field name and not the
 * string. For this reason we have two subclasses. - MatchingRecordFieldNamesComputationInfo is used
 * when the name in the string matches the field names, and the R8 compilation is able to minify and
 * prune the string based on the fields. - MissMatchingRecordFieldNamesComputationInfo is used when
 * the name in the string does not match the field names, and the R8 compilation is then only able
 * to prune the fields while maintaining the non-matching field names.
 */
public abstract class RecordFieldNamesComputationInfo extends NameComputationInfo<DexType> {

  final DexField[] fields;

  protected RecordFieldNamesComputationInfo(DexField[] fields) {
    this.fields = fields;
  }

  private static class MissMatchingRecordFieldNamesComputationInfo
      extends RecordFieldNamesComputationInfo {

    private final String[] fieldNames;

    private MissMatchingRecordFieldNamesComputationInfo(String[] fieldNames, DexField[] fields) {
      super(fields);
      this.fieldNames = fieldNames;
    }

    @Override
    public DexString internalComputeNameFor(
        DexType type,
        DexDefinitionSupplier definitions,
        GraphLens graphLens,
        NamingLens namingLens) {
      return internalComputeNameFor(type, definitions, graphLens, i -> fieldNames[i]);
    }
  }

  private static class MatchingRecordFieldNamesComputationInfo
      extends RecordFieldNamesComputationInfo {

    public MatchingRecordFieldNamesComputationInfo(DexField[] fields) {
      super(fields);
    }

    @Override
    public DexString internalComputeNameFor(
        DexType type,
        DexDefinitionSupplier definitions,
        GraphLens graphLens,
        NamingLens namingLens) {
      return internalComputeNameFor(
          type,
          definitions,
          graphLens,
          i ->
              namingLens
                  .lookupField(
                      graphLens.getRenamedFieldSignature(fields[i]), definitions.dexItemFactory())
                  .name
                  .toString());
    }
  }

  static DexString dexStringFromFieldNames(List<String> fieldNames, DexItemFactory factory) {
    return factory.createString(StringUtils.join(";", fieldNames));
  }

  public static RecordFieldNamesComputationInfo forFieldNamesAndFields(
      DexString fieldNames, DexField[] fields) {
    String fieldNamesString = fieldNames.toString();
    String[] fieldNamesSplit =
        fieldNamesString.isEmpty() ? new String[0] : fieldNamesString.split(";");
    assert fieldNamesSplit.length == fields.length;
    if (fieldsMatchNames(fieldNamesSplit, fields)) {
      return new MatchingRecordFieldNamesComputationInfo(fields);
    }
    return new MissMatchingRecordFieldNamesComputationInfo(fieldNamesSplit, fields);
  }

  private static boolean fieldsMatchNames(String[] fieldNames, DexField[] fields) {
    for (int i = 0; i < fieldNames.length; i++) {
      if (!(fields[i].name.toString().equals(fieldNames[i]))) {
        return false;
      }
    }
    return true;
  }

  public DexString internalComputeNameFor(
      DexType type,
      DexDefinitionSupplier definitions,
      GraphLens graphLens,
      IntFunction<String> nameSupplier) {
    DexClass recordClass = definitions.contextIndependentDefinitionFor(type);
    assert recordClass != null;
    List<String> names = new ArrayList<>(fields.length);
    for (int i = 0; i < fields.length; i++) {
      DexEncodedField recordField =
          recordClass.lookupInstanceField(graphLens.getRenamedFieldSignature(fields[i]));
      if (recordField != null) {
        names.add(nameSupplier.apply(i));
      }
    }
    return dexStringFromFieldNames(names, definitions.dexItemFactory());
  }

  @Override
  DexString internalComputeNameFor(
      DexType reference, DexDefinitionSupplier definitions, NamingLens namingLens) {
    throw new Unreachable();
  }

  public abstract DexString internalComputeNameFor(
      DexType type, DexDefinitionSupplier definitions, GraphLens graphLens, NamingLens namingLens);

  @Override
  public boolean needsToComputeName() {
    return true;
  }

  @Override
  public boolean needsToRegisterReference() {
    return false;
  }

  @Override
  public boolean isRecordFieldNamesComputationInfo() {
    return true;
  }

  @Override
  public RecordFieldNamesComputationInfo asRecordFieldNamesComputationInfo() {
    return this;
  }
}
