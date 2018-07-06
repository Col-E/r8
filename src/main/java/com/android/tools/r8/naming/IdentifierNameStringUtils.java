// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptorIfValidJavaType;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemBasedString;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IdentifierNameStringUtils {

  /**
   * Checks if the given {@param method} is a reflection method in Java.
   *
   * @param dexItemFactory where pre-defined descriptors are retrieved
   * @param method to test
   * @return {@code true} if the given {@param method} is a reflection method in Java.
   */
  public static boolean isReflectionMethod(DexItemFactory dexItemFactory, DexMethod method) {
    // For java.lang.Class:
    //   (String) -> java.lang.Class | java.lang.reflect.Field
    //   (String, Class[]) -> java.lang.reflect.Method
    // For java.util.concurrent.atomic.Atomic(Integer|Long)FieldUpdater:
    //   (Class, String) -> $holderType
    // For java.util.concurrent.atomic.AtomicReferenceFieldUpdater:
    //   (Class, Class, String) -> $holderType
    // For any other types:
    //   (Class, String) -> java.lang.reflect.Field
    //   (Class, String, Class[]) -> java.lang.reflect.Method
    int arity = method.getArity();
    if (method.holder.descriptor == dexItemFactory.classDescriptor) {
      // Virtual methods of java.lang.Class, such as getField, getMethod, etc.
      if (arity != 1 && arity != 2) {
        return false;
      }
      if (arity == 1) {
        if (method.proto.returnType.descriptor != dexItemFactory.classDescriptor
            && method.proto.returnType.descriptor != dexItemFactory.fieldDescriptor) {
          return false;
        }
      } else {
        if (method.proto.returnType.descriptor != dexItemFactory.methodDescriptor) {
          return false;
        }
      }
      if (method.proto.parameters.values[0].descriptor != dexItemFactory.stringDescriptor) {
        return false;
      }
      if (arity == 2) {
        if (method.proto.parameters.values[1].descriptor != dexItemFactory.classArrayDescriptor) {
          return false;
        }
      }
    } else if (
        method.holder.descriptor == dexItemFactory.intFieldUpdaterDescriptor
        || method.holder.descriptor == dexItemFactory.longFieldUpdaterDescriptor) {
      // Atomic(Integer|Long)FieldUpdater->newUpdater(Class, String)AtomicFieldUpdater
      if (arity != 2) {
        return false;
      }
      if (method.proto.returnType.descriptor != method.holder.descriptor) {
        return false;
      }
      if (method.proto.parameters.values[0].descriptor != dexItemFactory.classDescriptor) {
        return false;
      }
      if (method.proto.parameters.values[1].descriptor != dexItemFactory.stringDescriptor) {
        return false;
      }
    } else if (method.holder.descriptor == dexItemFactory.referenceFieldUpdaterDescriptor) {
      // AtomicReferenceFieldUpdater->newUpdater(Class, Class, String)AtomicFieldUpdater
      if (arity != 3) {
        return false;
      }
      if (method.proto.returnType.descriptor != method.holder.descriptor) {
        return false;
      }
      if (method.proto.parameters.values[0].descriptor != dexItemFactory.classDescriptor) {
        return false;
      }
      if (method.proto.parameters.values[1].descriptor != dexItemFactory.classDescriptor) {
        return false;
      }
      if (method.proto.parameters.values[2].descriptor != dexItemFactory.stringDescriptor) {
        return false;
      }
    } else {
      // Methods whose first argument is of java.lang.Class type.
      if (arity != 2 && arity != 3) {
        return false;
      }
      if (arity == 2) {
        if (method.proto.returnType.descriptor != dexItemFactory.fieldDescriptor) {
          return false;
        }
      } else {
        if (method.proto.returnType.descriptor != dexItemFactory.methodDescriptor) {
          return false;
        }
      }
      if (method.proto.parameters.values[0].descriptor != dexItemFactory.classDescriptor) {
        return false;
      }
      if (method.proto.parameters.values[1].descriptor != dexItemFactory.stringDescriptor) {
        return false;
      }
      if (arity == 3) {
        if (method.proto.parameters.values[2].descriptor != dexItemFactory.classArrayDescriptor) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Creates {@link DexItemBasedString}, which represents a string literal that corresponds to
   * either class or member name (i.e., identifier).
   *
   * @param appInfo {@link AppInfo} that contains {@link DexItemFactory} to create
   *     {@link DexItemBasedString}.
   * @param invoke {@link InvokeMethod} that is expected to have an identifier in its arguments.
   * @return {@link DexItemBasedString} whose string literal corresponds to {@link DexItem},
   *     otherwise {@code null}.
   */
  public static DexItemBasedString identifyIdentiferNameString(
      AppInfo appInfo, InvokeMethod invoke) {
    List<Value> ins = invoke.arguments();
    // The only static call: Class#forName, which receives (String) as ins.
    if (ins.size() == 1) {
      Value in = ins.get(0);
      if (in.isConstString()) {
        ConstString constString = in.getConstInstruction().asConstString();
        return inferMemberOrTypeFromNameString(appInfo, constString.getValue());
      }
      return null;
    }
    // All the other cases receive either (Class, String) or (Class, String, Class[]) as ins.
    boolean isReferenceFieldUpdater =
        invoke.getReturnType().descriptor == appInfo.dexItemFactory.referenceFieldUpdaterDescriptor;
    int positionOfIdentifier = isReferenceFieldUpdater ? 2 : 1;
    Value in = ins.get(positionOfIdentifier);
    if (!in.isConstString()) {
      return null;
    }
    Value classValue = ins.get(0);
    if (!classValue.isConstClass()) {
      return null;
    }
    DexType holderType = classValue.getConstInstruction().asConstClass().getValue();
    DexClass holder = appInfo.definitionFor(holderType);
    if (holder == null) {
      return null;
    }
    DexString dexString = in.getConstInstruction().asConstString().getValue();
    DexItemBasedString itemBasedString = null;
    int numOfParams = ins.size();
    if (isReferenceFieldUpdater) {
      Value fieldTypeValue = ins.get(1);
      if (!fieldTypeValue.isConstClass()) {
        return null;
      }
      DexType fieldType = fieldTypeValue.getConstInstruction().asConstClass().getValue();
      itemBasedString = inferFieldInHolder(appInfo, holder, dexString.toString(), fieldType);
    } else if (numOfParams == 2) {
      itemBasedString = inferFieldInHolder(appInfo, holder, dexString.toString(), null);
    } else {
      assert numOfParams == 3;
      DexTypeList arguments = retrieveDexTypeListFromClassList(invoke, ins.get(2));
      itemBasedString = inferMethodInHolder(appInfo, holder, dexString.toString(), arguments);
    }
    return itemBasedString;
  }

  static DexItemBasedString inferMemberOrTypeFromNameString(
      AppInfo appInfo, DexString dexString) {
    // "fully.qualified.ClassName.fieldOrMethodName"
    // "fully.qualified.ClassName#fieldOrMethodName"
    DexItemBasedString itemBasedString = inferMemberFromNameString(appInfo, dexString);
    if (itemBasedString == null) {
      // "fully.qualified.ClassName"
      String maybeDescriptor = javaTypeToDescriptorIfValidJavaType(dexString.toString());
      if (maybeDescriptor != null) {
        DexType type = appInfo.dexItemFactory.createType(maybeDescriptor);
        itemBasedString = appInfo.dexItemFactory.createItemBasedString(type);
      }
    }
    return itemBasedString;
  }

  private static DexItemBasedString inferMemberFromNameString(
      AppInfo appInfo, DexString dexString) {
    String identifier = dexString.toString();
    String typeIdentifier = null;
    String memberIdentifier = null;
    String[] items = identifier.split("#");
    // "x#y#z"
    if (items.length > 2) {
      return null;
    }
    // "fully.qualified.ClassName#fieldOrMethodName"
    if (items.length == 2) {
      typeIdentifier = items[0];
      memberIdentifier = items[1];
    } else {
      int lastDot = identifier.lastIndexOf(".");
      // "fully.qualified.ClassName.fieldOrMethodName"
      if (0 < lastDot && lastDot < identifier.length() - 1) {
        typeIdentifier = identifier.substring(0, lastDot);
        memberIdentifier = identifier.substring(lastDot + 1);
      }
    }
    if (typeIdentifier == null) {
      return null;
    }
    String maybeDescriptor = javaTypeToDescriptorIfValidJavaType(typeIdentifier);
    if (maybeDescriptor == null) {
      return null;
    }
    DexType type = appInfo.dexItemFactory.createType(maybeDescriptor);
    DexClass holder = appInfo.definitionFor(type);
    if (holder == null) {
      return null;
    }
    DexItemBasedString itemBasedString =
        inferFieldInHolder(appInfo, holder, memberIdentifier, null);
    if (itemBasedString == null) {
      itemBasedString = inferMethodInHolder(appInfo, holder, memberIdentifier, null);
    }
    return itemBasedString;
  }

  private static DexItemBasedString inferFieldInHolder(
      AppInfo appInfo, DexClass holder, String name, DexType fieldType) {
    DexItemBasedString itemBasedString = null;
    for (DexEncodedField encodedField : holder.staticFields()) {
      if (encodedField.field.name.toString().equals(name)
          && (fieldType == null || encodedField.field.type == fieldType)) {
        itemBasedString = appInfo.dexItemFactory.createItemBasedString(encodedField.field);
        break;
      }
    }
    if (itemBasedString == null) {
      for (DexEncodedField encodedField : holder.instanceFields()) {
        if (encodedField.field.name.toString().equals(name)
            && (fieldType == null || encodedField.field.type == fieldType)) {
          itemBasedString = appInfo.dexItemFactory.createItemBasedString(encodedField.field);
          break;
        }
      }
    }
    return itemBasedString;
  }

  private static DexItemBasedString inferMethodInHolder(
      AppInfo appInfo, DexClass holder, String name, DexTypeList arguments) {
    DexItemBasedString itemBasedString = null;
    for (DexEncodedMethod encodedMethod : holder.directMethods()) {
      if (encodedMethod.method.name.toString().equals(name)
          && (arguments == null || encodedMethod.method.proto.parameters.equals(arguments))) {
        itemBasedString = appInfo.dexItemFactory.createItemBasedString(encodedMethod.method);
        break;
      }
    }
    if (itemBasedString == null) {
      for (DexEncodedMethod encodedMethod : holder.virtualMethods()) {
        if (encodedMethod.method.name.toString().equals(name)
            && (arguments == null || encodedMethod.method.proto.parameters.equals(arguments))) {
          itemBasedString = appInfo.dexItemFactory.createItemBasedString(encodedMethod.method);
          break;
        }
      }
    }
    return itemBasedString;
  }

  /**
   * Visits all {@link ArrayPut}'s with the given {@param classListValue} as array and {@link Class}
   * as value. Then collects all corresponding {@link DexType}s so as to determine reflective cases.
   *
   * @param invoke the instruction that invokes a reflective method with -identifiernamestring rule
   * @param classListValue the register that holds an array of {@link Class}'s
   * @return a list of {@link DexType} that corresponds to const class in {@param classListValue}
   */
  private static DexTypeList retrieveDexTypeListFromClassList(
      InvokeMethod invoke, Value classListValue) {
    // Make sure this Value refers to an array.
    if (!classListValue.definition.isInvokeNewArray()
        && !classListValue.definition.isNewArrayEmpty()) {
      return null;
    }
    // The only pattern we consider is: new Class[] { A.class, B.class, ... }, which looks like
    //   new-array va ...
    //   const-class vc ...
    //   const/4 vi ...
    //   aput-object vc va vi
    //   ... repeat putting const class into one location at a time ...
    //   invoke-static ... va // Use that array at {@param invoke}.
    BasicBlock block = classListValue.definition.getBlock();
    InstructionIterator iterator = block.iterator();
    iterator.nextUntil(instr -> instr == classListValue.definition);
    Set<Instruction> users = classListValue.definition.outValue().uniqueUsers();
    int maxIndex = -1;
    Map<Integer, DexType> typeMap = new Int2ObjectArrayMap<>();
    while (iterator.hasNext()) {
      Instruction instr = iterator.next();
      // Iterate the instructions up to the current {@param invoke}.
      if (instr == invoke) {
        break;
      }
      if (!users.contains(instr)) {
        continue;
      }
      // Any other kinds of users mean that elements could be escaped and altered.
      if (!instr.isArrayPut()) {
        return null;
      }
      ArrayPut arrayPut = instr.asArrayPut();
      assert arrayPut.array() == classListValue;
      // Ignore statically unknown index.
      if (!(arrayPut.value().isConstClass() && arrayPut.index().isConstNumber())) {
        return null;
      }
      int index = arrayPut.index().getConstInstruction().asConstNumber().getIntValue();
      // Filter out out-of-bound index or non-deterministic index.
      if (index < 0 || typeMap.containsKey(index)) {
        return null;
      }
      maxIndex = maxIndex < index ? index : maxIndex;
      DexType type = arrayPut.value().getConstInstruction().asConstClass().getValue();
      typeMap.put(index, type);
    }
    if (maxIndex < 0) {
      return DexTypeList.empty();
    }
    // Make sure we were able to collect *all* {@link ConstClass}'s.
    for (int i = 0; i <= maxIndex; i++) {
      if (!typeMap.containsKey(i)) {
        return null;
      }
    }
    DexType[] types = new DexType [maxIndex + 1];
    for (int i = 0; i <= maxIndex; i++) {
      types[i] = typeMap.get(i);
    }
    return new DexTypeList(types);
  }

  public static void warnUndeterminedIdentifier(
      Reporter reporter,
      DexItem member,
      Origin origin,
      Instruction instruction,
      DexString original) {
    assert member instanceof DexField || member instanceof DexMethod;
    String kind = member instanceof DexField ? "field" : "method";
    String originalMessage = original == null ? "what identifier string flows to "
        : "what '" + original.toString() + "' refers to, which flows to ";
    String message =
        "Cannot determine " + originalMessage + member.toSourceString()
            + " that is specified in -identifiernamestring rules."
            + " Thus, not all identifier strings flowing to that " + kind
            + " are renamed, which can cause resolution failures at runtime.";
    StringDiagnostic diagnostic =
        instruction.getPosition().line >= 1
            ? new StringDiagnostic(message, origin,
                new TextPosition(0L, instruction.getPosition().line, 1))
            : new StringDiagnostic(message, origin);
    reporter.warning(diagnostic);
  }
}
