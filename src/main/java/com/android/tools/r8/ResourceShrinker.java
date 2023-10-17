// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.code.DexConst;
import com.android.tools.r8.dex.code.DexConst16;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstHigh16;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexConstStringJumbo;
import com.android.tools.r8.dex.code.DexConstWide16;
import com.android.tools.r8.dex.code.DexConstWide32;
import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexFillArrayDataPayload;
import com.android.tools.r8.dex.code.DexFormat35c;
import com.android.tools.r8.dex.code.DexFormat3rc;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeDirectRange;
import com.android.tools.r8.dex.code.DexInvokeInterface;
import com.android.tools.r8.dex.code.DexInvokeInterfaceRange;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexInvokeStaticRange;
import com.android.tools.r8.dex.code.DexInvokeSuper;
import com.android.tools.r8.dex.code.DexInvokeSuperRange;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexInvokeVirtualRange;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.dex.code.DexSget;
import com.android.tools.r8.dex.code.DexSgetBoolean;
import com.android.tools.r8.dex.code.DexSgetByte;
import com.android.tools.r8.dex.code.DexSgetChar;
import com.android.tools.r8.dex.code.DexSgetObject;
import com.android.tools.r8.dex.code.DexSgetShort;
import com.android.tools.r8.dex.code.DexSgetWide;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.code.WideConstant;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * This class is deprecated and should not be used. It is a temporary solution to use R8 to analyze
 * dex files and compute resource shrinker related bits.
 *
 * <p>Users or this API should implement {@link ReferenceChecker} interface, and through callbacks
 * in that interface, they will be notified when element relevant for resource shrinking is found.
 *
 * <p>This class extracts all integer constants and string constants, which might refer to resource.
 * More specifically, we look for the following while analyzing dex:
 * <ul>
 *   <li>const instructions that might load integers or strings
 *   <li>static fields that have an initial value. This initial value might be integer, string,
 *   or array of integers.
 *   <li>integer array payloads. Only payloads referenced in fill-array-data instructions will be
 *   processed. More specifically, if a payload is referenced in fill-array-data, and we are able
 *   to determine that array is not array of integers, payload will be ignored. Otherwise, it will
 *   be processed once fill-array-data-payload instruction is encountered.
 *   <li>all annotations (class, field, method) that contain annotation element whose value is
 *   integer, string or array of integers are processed.
 * </ul>
 *
 * <p>Please note that switch payloads are not analyzed. Although they might contain integer
 * constants, ones referring to resource ids would have to be loaded in the code analyzed in list
 * above.
 *
 * <p>Usage of this feature is intentionally not supported from the command line.
 */

// TODO(b/121121779) Remove keep if possible.
@Deprecated
@Keep
final public class ResourceShrinker {

  @Keep
  public final static class Command extends BaseCommand {

    Command(AndroidApp app) {
      super(app);
    }

    @Override
    InternalOptions getInternalOptions() {
      return new InternalOptions();
    }
  }

  @Keep
  public final static class Builder extends BaseCommand.Builder<Command, Builder> {

    @Override
    Builder self() {
      return this;
    }

    @Override
    Command makeCommand() {
      return new Command(getAppBuilder().build());
    }
  }

  /**
   * Classes that would like to process data relevant to resource shrinking should implement this
   * interface.
   */
  @KeepForSubclassing
  public interface ReferenceChecker {

    /**
     * Returns if the class with specified internal name should be processed. Typically,
     * resource type classes like R$drawable, R$styleable etc. should be skipped.
     */
    boolean shouldProcess(String internalName);

    void referencedInt(int value);

    void referencedString(String value);

    void referencedStaticField(String internalName, String fieldName);

    void referencedMethod(String internalName, String methodName, String methodDescriptor);

    default void startMethodVisit(MethodReference methodReference) {}

    default void endMethodVisit(MethodReference methodReference) {}

    default void startClassVisit(ClassReference classReference) {}

    default void endClassVisit(ClassReference classReference) {}
  }

  private static final class DexClassUsageVisitor {

    private final DexProgramClass classDef;
    private final ReferenceChecker callback;

    DexClassUsageVisitor(DexProgramClass classDef, ReferenceChecker callback) {
      this.classDef = classDef;
      this.callback = callback;
    }

    public void visit() {
      callback.startClassVisit(classDef.getClassReference());
      if (!callback.shouldProcess(classDef.type.getInternalName())) {
        return;
      }

      for (DexEncodedField field : classDef.staticFields()) {
        DexValue staticValue = field.getStaticValue();
        if (staticValue != null) {
          processFieldValue(staticValue);
        }
      }

      for (DexEncodedMethod method : classDef.allMethodsSorted()) {

        callback.startMethodVisit(method.getReference().asMethodReference());
        processMethod(method);
        callback.endMethodVisit(method.getReference().asMethodReference());
      }

      if (classDef.hasClassOrMemberAnnotations()) {
        processAnnotations(classDef);
      }
      callback.endClassVisit(classDef.getClassReference());
    }

    private void processFieldValue(DexValue value) {
      switch (value.getValueKind()) {
        case ARRAY:
          for (DexValue elementValue : value.asDexValueArray().getValues()) {
            if (elementValue.isDexValueInt()) {
              callback.referencedInt(elementValue.asDexValueInt().getValue());
            }
          }
          break;

        case INT:
          callback.referencedInt(value.asDexValueInt().getValue());
          break;

        case STRING:
          callback.referencedString(value.asDexValueString().value.toString());
          break;

        default:
          // Intentionally empty.
      }
    }

    private void processMethod(DexEncodedMethod method) {
      Code implementation = method.getCode();
      if (implementation != null) {

        // Tracks the offsets of integer array payloads.
        final Set<Integer> methodIntArrayPayloadOffsets = Sets.newHashSet();
        // First we collect payloads, and then we process them because payload can be before the
        // fill-array-data instruction referencing it.
        final List<DexFillArrayDataPayload> payloads = Lists.newArrayList();

        DexInstruction[] instructions = implementation.asDexCode().instructions;
        int current = 0;
        while (current < instructions.length) {
          DexInstruction instruction = instructions[current];
          if (isIntConstInstruction(instruction)) {
            processIntConstInstruction(instruction);
          } else if (isStringConstInstruction(instruction)) {
            processStringConstantInstruction(instruction);
          } else if (isGetStatic(instruction)) {
            processGetStatic(instruction);
          } else if (isInvokeInstruction(instruction)) {
            processInvokeInstruction(instruction);
          } else if (isInvokeRangeInstruction(instruction)) {
            processInvokeRangeInstruction(instruction);
          } else if (instruction instanceof DexFillArrayData) {
            processFillArray(instructions, current, methodIntArrayPayloadOffsets);
          } else if (instruction instanceof DexFillArrayDataPayload) {
            payloads.add((DexFillArrayDataPayload) instruction);
          }
          current++;
        }

        for (DexFillArrayDataPayload payload : payloads) {
          if (isIntArrayPayload(payload, methodIntArrayPayloadOffsets)) {
            processIntArrayPayload(payload);
          }
        }
      }
    }

    private void processAnnotations(DexProgramClass classDef) {
      Stream<DexAnnotation> classAnnotations = classDef.annotations().stream();
      Stream<DexAnnotation> fieldAnnotations =
          Streams.stream(classDef.fields())
              .filter(DexEncodedField::hasAnnotations)
              .flatMap(f -> f.annotations().stream());
      Stream<DexAnnotation> methodAnnotations =
          Streams.stream(classDef.methods())
              .filter(DexEncodedMethod::hasAnyAnnotations)
              .flatMap(m -> m.annotations().stream());

      Streams.concat(classAnnotations, fieldAnnotations, methodAnnotations)
          .forEach(
              annotation -> {
                for (DexAnnotationElement element : annotation.annotation.elements) {
                  DexValue value = element.value;
                  processAnnotationValue(value);
                }
              });
    }

    private void processIntArrayPayload(DexInstruction instruction) {
      DexFillArrayDataPayload payload = (DexFillArrayDataPayload) instruction;

      for (int i = 0; i < payload.data.length / 2; i++) {
        int intValue = payload.data[2 * i + 1] << 16 | payload.data[2 * i];
        callback.referencedInt(intValue);
      }
    }

    private boolean isIntArrayPayload(
        DexInstruction instruction, Set<Integer> methodIntArrayPayloadOffsets) {
      if (!(instruction instanceof DexFillArrayDataPayload)) {
        return false;
      }

      DexFillArrayDataPayload payload = (DexFillArrayDataPayload) instruction;
      return methodIntArrayPayloadOffsets.contains(payload.getOffset());
    }

    private void processFillArray(
        DexInstruction[] instructions, int current, Set<Integer> methodIntArrayPayloadOffsets) {
      DexFillArrayData fillArrayData = (DexFillArrayData) instructions[current];
      if (current > 0 && instructions[current - 1] instanceof DexNewArray) {
        DexNewArray newArray = (DexNewArray) instructions[current - 1];
        if (!Objects.equals(newArray.getType().descriptor.toString(), "[I")) {
          return;
        }
        // Typically, new-array is right before fill-array-data. If not, assume referenced array is
        // of integers. This can be improved later, but for now we make sure no ints are missed.
      }

      methodIntArrayPayloadOffsets.add(
          fillArrayData.getPayloadOffset() + fillArrayData.getOffset());
    }

    private void processAnnotationValue(DexValue value) {
      switch (value.getValueKind()) {
        case ANNOTATION:
          for (DexAnnotationElement element : value.asDexValueAnnotation().value.elements) {
            processAnnotationValue(element.value);
          }
          break;

        case ARRAY:
          for (DexValue elementValue : value.asDexValueArray().getValues()) {
            processAnnotationValue(elementValue);
          }
          break;

        case INT:
          callback.referencedInt(value.asDexValueInt().value);
          break;

        case STRING:
          callback.referencedString(value.asDexValueString().value.toString());
          break;

        default:
          // Intentionally empty.
      }
    }

    private boolean isIntConstInstruction(DexInstruction instruction) {
      int opcode = instruction.getOpcode();
      return opcode == DexConst4.OPCODE
          || opcode == DexConst16.OPCODE
          || opcode == DexConst.OPCODE
          || opcode == DexConstWide32.OPCODE
          || opcode == DexConstHigh16.OPCODE
          || opcode == DexConstWide16.OPCODE;
    }

    private void processIntConstInstruction(DexInstruction instruction) {
      assert isIntConstInstruction(instruction);

      int constantValue;
      if (instruction instanceof SingleConstant) {
        SingleConstant singleConstant = (SingleConstant) instruction;
        constantValue = singleConstant.decodedValue();
      } else if (instruction instanceof WideConstant) {
        WideConstant wideConstant = (WideConstant) instruction;
        if (((int) wideConstant.decodedValue()) != wideConstant.decodedValue()) {
          // We care only about values that fit in int range.
          return;
        }
        constantValue = (int) wideConstant.decodedValue();
      } else {
        throw new AssertionError("Not an int const instruction.");
      }

      callback.referencedInt(constantValue);
    }

    private boolean isStringConstInstruction(DexInstruction instruction) {
      int opcode = instruction.getOpcode();
      return opcode == DexConstString.OPCODE || opcode == DexConstStringJumbo.OPCODE;
    }

    private void processStringConstantInstruction(DexInstruction instruction) {
      assert isStringConstInstruction(instruction);

      String constantValue;
      if (instruction instanceof DexConstString) {
        DexConstString constString = (DexConstString) instruction;
        constantValue = constString.getString().toString();
      } else if (instruction instanceof DexConstStringJumbo) {
        DexConstStringJumbo constStringJumbo = (DexConstStringJumbo) instruction;
        constantValue = constStringJumbo.getString().toString();
      } else {
        throw new AssertionError("Not a string constant instruction.");
      }

      callback.referencedString(constantValue);
    }

    private boolean isGetStatic(DexInstruction instruction) {
      int opcode = instruction.getOpcode();
      return opcode == DexSget.OPCODE
          || opcode == DexSgetBoolean.OPCODE
          || opcode == DexSgetByte.OPCODE
          || opcode == DexSgetChar.OPCODE
          || opcode == DexSgetObject.OPCODE
          || opcode == DexSgetShort.OPCODE
          || opcode == DexSgetWide.OPCODE;
    }

    private void processGetStatic(DexInstruction instruction) {
      assert isGetStatic(instruction);

      DexField field;
      if (instruction instanceof DexSget) {
        DexSget sget = (DexSget) instruction;
        field = sget.getField();
      } else if (instruction instanceof DexSgetBoolean) {
        DexSgetBoolean sgetBoolean = (DexSgetBoolean) instruction;
        field = sgetBoolean.getField();
      } else if (instruction instanceof DexSgetByte) {
        DexSgetByte sgetByte = (DexSgetByte) instruction;
        field = sgetByte.getField();
      } else if (instruction instanceof DexSgetChar) {
        DexSgetChar sgetChar = (DexSgetChar) instruction;
        field = sgetChar.getField();
      } else if (instruction instanceof DexSgetObject) {
        DexSgetObject sgetObject = (DexSgetObject) instruction;
        field = sgetObject.getField();
      } else if (instruction instanceof DexSgetShort) {
        DexSgetShort sgetShort = (DexSgetShort) instruction;
        field = sgetShort.getField();
      } else if (instruction instanceof DexSgetWide) {
        DexSgetWide sgetWide = (DexSgetWide) instruction;
        field = sgetWide.getField();
      } else {
        throw new AssertionError("Not a get static instruction");
      }

      callback.referencedStaticField(field.holder.getInternalName(), field.name.toString());
    }

    private boolean isInvokeInstruction(DexInstruction instruction) {
      int opcode = instruction.getOpcode();
      return opcode == DexInvokeVirtual.OPCODE
          || opcode == DexInvokeSuper.OPCODE
          || opcode == DexInvokeDirect.OPCODE
          || opcode == DexInvokeStatic.OPCODE
          || opcode == DexInvokeInterface.OPCODE;
    }

    private void processInvokeInstruction(DexInstruction instruction) {
      assert isInvokeInstruction(instruction);

      DexFormat35c ins35c = (DexFormat35c) instruction;
      DexMethod method = (DexMethod) ins35c.BBBB;

      callback.referencedMethod(
          method.holder.getInternalName(),
          method.name.toString(),
          method.proto.toDescriptorString());
    }

    private boolean isInvokeRangeInstruction(DexInstruction instruction) {
      int opcode = instruction.getOpcode();
      return opcode == DexInvokeVirtualRange.OPCODE
          || opcode == DexInvokeSuperRange.OPCODE
          || opcode == DexInvokeDirectRange.OPCODE
          || opcode == DexInvokeStaticRange.OPCODE
          || opcode == DexInvokeInterfaceRange.OPCODE;
    }

    private void processInvokeRangeInstruction(DexInstruction instruction) {
      assert isInvokeRangeInstruction(instruction);

      DexFormat3rc ins3rc = (DexFormat3rc) instruction;
      DexMethod method = (DexMethod) ins3rc.BBBB;

      callback.referencedMethod(
          method.holder.getInternalName(),
          method.name.toString(),
          method.proto.toDescriptorString());
    }
  }

  @SuppressWarnings("RedundantThrows")
  public static void run(Command command, ReferenceChecker callback)
      throws IOException, ExecutionException {
    runForTesting(command.getInputApp(), command.getInternalOptions(), callback);
  }

  public static void runForTesting(
      AndroidApp inputApp, InternalOptions options, ReferenceChecker callback)
      throws IOException, ExecutionException {
    Timing timing = new Timing("resource shrinker analyzer");
    DexApplication dexApplication = new ApplicationReader(inputApp, options, timing).read();
    for (DexProgramClass programClass : dexApplication.classes()) {
      new DexClassUsageVisitor(programClass, callback).visit();
    }
  }
}
