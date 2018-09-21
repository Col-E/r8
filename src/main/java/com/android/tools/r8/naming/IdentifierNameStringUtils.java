// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptorIfValidJavaType;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemBasedString;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.Value;
import java.util.List;
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

  // Perform a conservative evaluation of the constant content of an array from its construction
  // until its use at a given instruction.
  private static ConstInstruction[] evaluateConstArrayContentFromConstructionToUse(
      NewArrayEmpty newArray, int size, Instruction user) {
    ConstInstruction[] values = new ConstInstruction[size];
    int remaining = size;
    Set<Instruction> users = newArray.outValue().uniqueUsers();
    // Follow the path from the array construction to the requested use collecting the constants
    // put into the array. Conservatively bail out if the content of the array cannot be statically
    // computed.
    BasicBlock block = newArray.getBlock();
    InstructionListIterator iterator = block.listIterator();
    iterator.nextUntil(i -> i == newArray);
    do {
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        // Ignore instructions which do not use the array.
        if (!users.contains(instruction)) {
          continue;
        }
        if (instruction == user) {
          // Return the array content if all elements are known when hitting the user for which
          // the content was requested.
          return remaining == 0 ? values : null;
        }
        // Any other kinds of use besides array-put mean that the array escapes and could be
        // altered.
        if (!instruction.isArrayPut()) {
          return null;
        }
        ArrayPut arrayPut = instruction.asArrayPut();
        if (!(arrayPut.value().isConstant() && arrayPut.index().isConstNumber())) {
          return null;
        }
        int index = arrayPut.index().getConstInstruction().asConstNumber().getIntValue();
        if (index < 0 || index >= values.length) {
          return null;
        }
        // Allow several writes to the same array element.
        if (values[index] == null) {
          remaining--;
        }
        ConstInstruction value = arrayPut.value().getConstInstruction();
        values[index] = value;
      }
      if (!block.exit().isGoto()) {
        return null;
      }
      block = block.exit().asGoto().getTarget();
      // Don't allow any other control flow into the sequence of blocks filling the array from
      // construction to requested use. This will also includes loopback and guarantee that
      // this will terminate without marking visited blocks.
      if (block.getPredecessors().size() != 1) {
        return null;
      }
      iterator = block.listIterator();
    } while (iterator != null);
    return null;
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

    // The code
    //   A.class.getMethod("m", String.class, String.class)
    // results in the following Java byte code from javac:
    //
    // LDC LA;.class
    // LDC "m"
    // ICONST_2
    // ANEWARRAY java/lang/Class
    // DUP
    // ICONST_0
    // LDC Ljava/lang/String;.class
    // AASTORE
    // DUP
    // ICONST_1
    // LDC Ljava/lang/String;.class
    // AASTORE
    // INVOKEVIRTUAL java/lang/Class.getMethod \
    //     (Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;

    // Make sure this Value refers to a new array.
    if (!classListValue.definition.isNewArrayEmpty()) {
      return null;
    }

    int size =
        classListValue
            .definition
            .asNewArrayEmpty()
            .size()
            .getConstInstruction()
            .asConstNumber()
            .getIntValue();
    if (size == 0) {
      return DexTypeList.empty();
    }

    ConstInstruction[] arrayContent =
        evaluateConstArrayContentFromConstructionToUse(
            classListValue.definition.asNewArrayEmpty(), size, invoke);

    if (arrayContent == null) {
      return null;
    }
    DexType[] types = new DexType[size];
    for (int i = 0; i < size; i++) {
      if (!arrayContent[i].isConstClass()) {
        return null;
      }
      types[i] = arrayContent[i].asConstClass().getValue();
    }
    return new DexTypeList(types);
  }
}
