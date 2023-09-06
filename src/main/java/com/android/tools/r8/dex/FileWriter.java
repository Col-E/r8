// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.utils.DexVersion.Layout.SINGLE_DEX;
import static com.android.tools.r8.utils.LebUtils.sizeAsUleb128;

import com.android.tools.r8.ByteBufferProvider;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.UnsupportedDefaultInterfaceMethodDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokeCustomDiagnostic;
import com.android.tools.r8.errors.UnsupportedPrivateInterfaceMethodDiagnostic;
import com.android.tools.r8.errors.UnsupportedStaticInterfaceMethodDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationDirectory;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexCode.TryHandler.TypeAddrPair;
import com.android.tools.r8.graph.DexDebugInfoForWriting;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedArray;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexWritableCode;
import com.android.tools.r8.graph.DexWritableCode.DexWritableCacheKey;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramClassVisitor;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.LebUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.zip.Adler32;

public class FileWriter {

  /** Simple pair of a byte buffer and its written length. */
  public static class ByteBufferResult {

    // Ownership of the buffer is transferred to the receiver of this result structure.
    public final CompatByteBuffer buffer;
    public final int length;

    protected ByteBufferResult(CompatByteBuffer buffer, int length) {
      this.buffer = buffer;
      this.length = length;
    }
  }

  private final AppView<?> appView;
  private final GraphLens graphLens;
  private final ObjectToOffsetMapping mapping;
  private final InternalOptions options;
  private final DexOutputBuffer dest;
  private final MixedSectionOffsets mixedSectionOffsets;
  private final CodeToKeep desugaredLibraryCodeToKeep;
  private final VirtualFile virtualFile;
  private final boolean includeStringData;

  public FileWriter(
      AppView<?> appView,
      ByteBufferProvider provider,
      ObjectToOffsetMapping mapping,
      CodeToKeep desugaredLibraryCodeToKeep,
      VirtualFile virtualFile) {
    this(
        appView,
        new DexOutputBuffer(provider),
        mapping,
        desugaredLibraryCodeToKeep,
        virtualFile,
        true);
  }

  public FileWriter(
      AppView<?> appView,
      DexOutputBuffer dexOutputBuffer,
      ObjectToOffsetMapping mapping,
      CodeToKeep desugaredLibraryCodeToKeep,
      VirtualFile virtualFile,
      boolean includeStringData) {
    this.appView = appView;
    this.graphLens = appView.graphLens();
    this.mapping = mapping;
    this.options = appView.options();
    this.dest = dexOutputBuffer;
    this.mixedSectionOffsets = new MixedSectionOffsets(options);
    this.desugaredLibraryCodeToKeep = desugaredLibraryCodeToKeep;
    this.virtualFile = virtualFile;
    this.includeStringData = includeStringData;
  }

  private NamingLens getNamingLens() {
    return appView.getNamingLens();
  }

  public MixedSectionOffsets getMixedSectionOffsets() {
    return mixedSectionOffsets;
  }

  public static void writeEncodedAnnotation(
      DexEncodedAnnotation annotation, DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
    List<DexAnnotationElement> elements = new ArrayList<>(Arrays.asList(annotation.elements));
    elements.sort((a, b) -> a.name.acceptCompareTo(b.name, mapping.getCompareToVisitor()));
    dest.putUleb128(mapping.getOffsetFor(annotation.type));
    dest.putUleb128(elements.size());
    for (DexAnnotationElement element : elements) {
      dest.putUleb128(mapping.getOffsetFor(element.name));
      element.value.writeTo(dest, mapping);
    }
  }

  public FileWriter collect() {
    // Use the class array from the mapping, as it has a deterministic iteration order.
    new ProgramClassDependencyCollector(appView, mapping.getClasses()).run(mapping.getClasses());

    // Add the static values for all fields now that we have committed to their sorting.
    mixedSectionOffsets.getClassesWithData().forEach(this::addStaticFieldValues);

    // String data is not tracked by the MixedSectionCollection.
    assert mixedSectionOffsets.stringData.size() == 0;
    for (DexString string : mapping.getStrings()) {
      mixedSectionOffsets.add(string);
    }
    // Neither are the typelists in protos...
    for (DexProto proto : mapping.getProtos()) {
      mixedSectionOffsets.add(proto.getParameters());
    }

    DexItem.collectAll(mixedSectionOffsets, mapping.getCallSites());

    DexItem.collectAll(mixedSectionOffsets, mapping.getClasses());

    return this;
  }

  public static class DexContainerSection {
    private final FileWriter writer;
    private final DexOutputBuffer buffer;
    private final Layout layout;

    public DexContainerSection(FileWriter writer, DexOutputBuffer buffer, Layout layout) {
      this.writer = writer;
      this.buffer = buffer;
      this.layout = layout;
    }

    public FileWriter getFileWriter() {
      return writer;
    }

    public DexOutputBuffer getBuffer() {
      return buffer;
    }

    public Layout getLayout() {
      return layout;
    }
  }

  public ByteBufferResult generate() {
    DexContainerSection res = generate(0, SINGLE_DEX);
    return new ByteBufferResult(res.getBuffer().stealByteBuffer(), res.getLayout().getEndOfFile());
  }

  public DexContainerSection generate(int offset, DexVersion.Layout layoutType) {
    // Check restrictions on interface methods.
    checkInterfaceMethods();

    // Check restriction on the names of fields, methods and classes
    assert verifyNames();

    Layout layout = Layout.from(mapping, offset, layoutType, includeStringData);
    layout.setCodesOffset(layout.dataSectionOffset);

    // Sort the codes first, as their order might impact size due to alignment constraints.
    MixedSectionLayoutStrategy mixedSectionLayoutStrategy =
        MixedSectionLayoutStrategy.create(appView, mixedSectionOffsets, virtualFile);
    Collection<ProgramMethod> codes = mixedSectionLayoutStrategy.getCodeLayout();

    // Output the debug_info_items first, as they have no dependencies.
    SizeAndCount sizeAndCountOfCodeItems = sizeAndCountOfCodeItems(codes);
    dest.moveTo(layout.getCodesOffset() + sizeAndCountOfCodeItems.size);
    if (mixedSectionOffsets.getDebugInfos().isEmpty()) {
      layout.setDebugInfosOffset(0);
    } else {
      // Ensure deterministic ordering of debug info by sorting consistent with the code objects.
      layout.setDebugInfosOffset(dest.align(1));
      Set<DexDebugInfoForWriting> seen = new HashSet<>(mixedSectionOffsets.getDebugInfos().size());
      for (ProgramMethod method : codes) {
        DexDebugInfoForWriting info =
            method.getDefinition().getCode().asDexWritableCode().getDebugInfoForWriting();
        if (info != null && seen.add(info)) {
          writeDebugItem(info);
        }
      }
    }

    // Remember the typelist offset for later.
    layout.setTypeListsOffset(dest.align(4)); // type_list are aligned.

    // Now output the code.
    dest.moveTo(layout.getCodesOffset());
    assert dest.isAligned(4);
    Map<DexWritableCacheKey, Integer> offsetCache = new HashMap<>();
    for (ProgramMethod method : codes) {
      DexWritableCode dexWritableCode = method.getDefinition().getCode().asDexWritableCode();
      if (!options.canUseCanonicalizedCodeObjects()) {
        writeCodeItem(method, dexWritableCode);
      } else {
        DexWritableCacheKey cacheLookupKey =
            dexWritableCode.getCacheLookupKey(method, appView.dexItemFactory());
        Integer offsetOrNull = offsetCache.get(cacheLookupKey);
        if (offsetOrNull != null) {
          mixedSectionOffsets.setOffsetFor(method.getDefinition(), offsetOrNull);
        } else {
          offsetCache.put(cacheLookupKey, writeCodeItem(method, dexWritableCode));
        }
      }
    }
    assert sizeAndCountOfCodeItems.getCount()
        == ImmutableSet.copyOf(mixedSectionOffsets.codes.values()).size();
    layout.setCodeCount(sizeAndCountOfCodeItems.getCount());
    assert layout.getDebugInfosOffset() == 0 || dest.position() == layout.getDebugInfosOffset();

    // Now the type lists and rest.
    dest.moveTo(layout.getTypeListsOffset());
    writeItems(
        mixedSectionLayoutStrategy.getTypeListLayout(),
        layout::alreadySetOffset,
        this::writeTypeList);
    if (includeStringData) {
      writeItems(
          mixedSectionLayoutStrategy.getStringDataLayout(),
          layout::setStringDataOffsets,
          this::writeStringData);
    } else {
      layout.stringDataOffsets = 0; // Empty string data section.
    }
    writeItems(
        mixedSectionLayoutStrategy.getAnnotationLayout(),
        layout::setAnnotationsOffset,
        this::writeAnnotation);
    writeItems(
        mixedSectionLayoutStrategy.getClassDataLayout(),
        layout::setClassDataOffset,
        this::writeClassData);
    writeItems(
        mixedSectionLayoutStrategy.getEncodedArrayLayout(),
        layout::setEncodedArraysOffset,
        this::writeEncodedArray);
    writeItems(
        mixedSectionLayoutStrategy.getAnnotationSetLayout(),
        layout::setAnnotationSetsOffset,
        this::writeAnnotationSet,
        4);
    writeItems(
        mixedSectionLayoutStrategy.getAnnotationSetRefListLayout(),
        layout::setAnnotationSetRefListsOffset,
        this::writeAnnotationSetRefList,
        4);
    writeItems(
        mixedSectionLayoutStrategy.getAnnotationDirectoryLayout(),
        layout::setAnnotationDirectoriesOffset,
        this::writeAnnotationDirectory,
        4);

    // Add the map at the end.
    writeMap(layout);
    layout.setEndOfFile(dest.position());

    // Now that we have all mixedSectionOffsets, lets write the indexed items.
    dest.moveTo(layout.headerOffset + layout.getHeaderSize());
    if (includeStringData) {
      writeFixedSectionItems(mapping.getStrings(), layout.stringIdsOffset, this::writeStringItem);
    } else {
      assert layout.stringIdsOffset == layout.typeIdsOffset;
    }
    writeFixedSectionItems(mapping.getTypes(), layout.typeIdsOffset, this::writeTypeItem);
    writeFixedSectionItems(mapping.getProtos(), layout.protoIdsOffset, this::writeProtoItem);
    writeFixedSectionItems(mapping.getFields(), layout.fieldIdsOffset, this::writeFieldItem);
    writeFixedSectionItems(mapping.getMethods(), layout.methodIdsOffset, this::writeMethodItem);
    writeFixedSectionItems(mapping.getClasses(), layout.classDefsOffset, this::writeClassDefItem);
    writeFixedSectionItems(mapping.getCallSites(), layout.callSiteIdsOffset, this::writeCallSite);
    writeFixedSectionItems(
        mapping.getMethodHandles(), layout.methodHandleIdsOffset, this::writeMethodHandle);

    // Fill in the header information.
    writeHeader(layout);
    if (includeStringData) {
      writeSignature(layout);
      writeChecksum(layout);
    }

    // Wrap backing buffer with actual length.
    return new DexContainerSection(this, dest, layout);
  }

  private void checkInterfaceMethods() {
    for (DexProgramClass clazz : mapping.getClasses()) {
      if (clazz.isInterface()) {
        for (DexEncodedMethod method : clazz.directMethods()) {
          checkInterfaceMethod(clazz, method);
        }
        for (DexEncodedMethod method : clazz.virtualMethods()) {
          checkInterfaceMethod(clazz, method);
        }
      }
    }
  }

  // Ensures interface method comply with requirements imposed by Android runtime:
  //  -- in pre-N Android versions interfaces may only have class
  //     initializer and public abstract methods.
  //  -- starting with N interfaces may also have public or private
  //     static methods, as well as public non-abstract (default)
  //     and private instance methods.
  private void checkInterfaceMethod(DexProgramClass holder, DexEncodedMethod method) {
    if (appView.dexItemFactory().isClassConstructor(method.getReference())) {
      return; // Class constructor is always OK.
    }
    if (method.accessFlags.isStatic()) {
      if (!options.canUseDefaultAndStaticInterfaceMethods()
          && !options.testing.allowStaticInterfaceMethodsForPreNApiLevel) {
        throw options.reporter.fatalError(
            new UnsupportedStaticInterfaceMethodDiagnostic(
                holder.getOrigin(), MethodPosition.create(method)));
      }
    } else {
      if (method.isInstanceInitializer()) {
        throw new CompilationError(
            "Interface must not have constructors: " + method.getReference().toSourceString());
      }
      if (!method.accessFlags.isAbstract() && !method.accessFlags.isPrivate() &&
          !options.canUseDefaultAndStaticInterfaceMethods()) {
        throw options.reporter.fatalError(
            new UnsupportedDefaultInterfaceMethodDiagnostic(
                holder.getOrigin(), MethodPosition.create(method)));
      }
    }

    if (method.accessFlags.isPrivate()) {
      if (options.canUsePrivateInterfaceMethods()) {
        return;
      }
      throw options.reporter.fatalError(
          new UnsupportedPrivateInterfaceMethodDiagnostic(
              holder.getOrigin(), MethodPosition.create(method)));
    }

    if (!method.accessFlags.isPublic() && !method.getName().toString().equals("<clinit>")) {
      throw new CompilationError(
          "Interface methods must not be "
              + "protected or package private: "
              + method.getReference().toSourceString());
    }
  }

  private boolean verifyNames() {
    if (appView.dexItemFactory().getSkipNameValidationForTesting()) {
      return true;
    }

    AndroidApiLevel apiLevel = options.getMinApiLevel();
    for (DexField field : mapping.getFields()) {
      assert field.name.isValidSimpleName(apiLevel);
    }
    for (DexMethod method : mapping.getMethods()) {
      assert method.name.isValidSimpleName(apiLevel);
    }
    for (DexType type : mapping.getTypes()) {
      if (type.isClassType()) {
        assert DexString.isValidSimpleName(apiLevel, type.getName());
        assert SyntheticNaming.verifyNotInternalSynthetic(type);
      }
    }

    return true;
  }

  private <T extends IndexedDexItem> void writeFixedSectionItems(
      Collection<T> items, int offset, Consumer<T> writer) {
    assert dest.position() == offset;
    for (T item : items) {
      writer.accept(item);
    }
  }

  private void writeFixedSectionItems(
      DexProgramClass[] items, int offset, Consumer<DexProgramClass> writer) {
    assert dest.position() == offset;
    for (DexProgramClass item : items) {
      writer.accept(item);
    }
  }

  private <T extends DexItem> void writeItems(Collection<T> items, Consumer<Integer> offsetSetter,
      Consumer<T> writer) {
    writeItems(items, offsetSetter, writer, 1);
  }

  private <T> void writeItems(
      Collection<T> items, Consumer<Integer> offsetSetter, Consumer<T> writer, int alignment) {
    if (items.isEmpty()) {
      offsetSetter.accept(0);
    } else {
      offsetSetter.accept(dest.align(alignment));
      items.forEach(writer);
    }
  }

  static class SizeAndCount {

    private int size = 0;
    private int count = 0;

    public int getCount() {
      return count;
    }

    public int getSize() {
      return size;
    }
  }

  private SizeAndCount sizeAndCountOfCodeItems(Iterable<ProgramMethod> methods) {
    SizeAndCount sizeAndCount = new SizeAndCount();
    Set<DexWritableCacheKey> cache = new HashSet<>();
    for (ProgramMethod method : methods) {
      DexWritableCode code = method.getDefinition().getCode().asDexWritableCode();
      if (!options.canUseCanonicalizedCodeObjects()
          || cache.add(code.getCacheLookupKey(method, appView.dexItemFactory()))) {
        sizeAndCount.count++;
        sizeAndCount.size = alignSize(4, sizeAndCount.size) + sizeOfCodeItem(code);
      }
    }
    return sizeAndCount;
  }

  private int sizeOfCodeItem(DexWritableCode code) {
    int result = 16;
    int insnSize = code.codeSizeInBytes();
    result += insnSize * 2;
    result += code.getTries().length * 8;
    if (code.getHandlers().length > 0) {
      result = alignSize(4, result);
      result += LebUtils.sizeAsUleb128(code.getHandlers().length);
      for (TryHandler handler : code.getHandlers()) {
        boolean hasCatchAll = handler.catchAllAddr != TryHandler.NO_HANDLER;
        result += LebUtils
            .sizeAsSleb128(hasCatchAll ? -handler.pairs.length : handler.pairs.length);
        for (TypeAddrPair pair : handler.pairs) {
          result += sizeAsUleb128(mapping.getOffsetFor(pair.getType(graphLens)));
          result += sizeAsUleb128(pair.addr);
        }
        if (hasCatchAll) {
          result += sizeAsUleb128(handler.catchAllAddr);
        }
      }
    }
    return result;
  }

  private void writeStringItem(DexString string) {
    dest.putInt(mixedSectionOffsets.getOffsetFor(string));
  }

  private void writeTypeItem(DexType type) {
    DexString descriptor = getNamingLens().lookupDescriptor(type);
    dest.putInt(mapping.getOffsetFor(descriptor));
  }

  private void writeProtoItem(DexProto proto) {
    DexString shorty = mapping.getShorty(proto);
    dest.putInt(mapping.getOffsetFor(shorty));
    dest.putInt(mapping.getOffsetFor(proto.returnType));
    dest.putInt(mixedSectionOffsets.getOffsetFor(proto.parameters));
  }

  private void writeFieldItem(DexField field) {
    int classIdx = mapping.getOffsetFor(field.holder);
    assert (classIdx & 0xFFFF) == classIdx;
    dest.putShort((short) classIdx);
    int typeIdx = mapping.getOffsetFor(field.type);
    assert (typeIdx & 0xFFFF) == typeIdx;
    dest.putShort((short) typeIdx);
    DexString name = getNamingLens().lookupName(field);
    dest.putInt(mapping.getOffsetFor(name));
  }

  private void writeMethodItem(DexMethod method) {
    int classIdx = mapping.getOffsetFor(method.holder);
    assert (classIdx & 0xFFFF) == classIdx;
    dest.putShort((short) classIdx);
    int protoIdx = mapping.getOffsetFor(method.proto);
    assert (protoIdx & 0xFFFF) == protoIdx;
    dest.putShort((short) protoIdx);
    DexString name = getNamingLens().lookupName(method);
    dest.putInt(mapping.getOffsetFor(name));
  }

  private void writeClassDefItem(DexProgramClass clazz) {
    desugaredLibraryCodeToKeep.recordHierarchyOf(clazz);
    dest.putInt(mapping.getOffsetFor(clazz.type));
    dest.putInt(clazz.accessFlags.getAsDexAccessFlags());
    dest.putInt(
        clazz.superType == null ? Constants.NO_INDEX : mapping.getOffsetFor(clazz.superType));
    dest.putInt(mixedSectionOffsets.getOffsetFor(clazz.interfaces));
    dest.putInt(
        clazz.sourceFile == null ? Constants.NO_INDEX : mapping.getOffsetFor(clazz.sourceFile));
    dest.putInt(mixedSectionOffsets.getOffsetForAnnotationsDirectory(clazz));
    dest.putInt(
        clazz.hasMethodsOrFields() ? mixedSectionOffsets.getOffsetFor(clazz) : Constants.NO_OFFSET);
    dest.putInt(
        mixedSectionOffsets.getOffsetFor(mixedSectionOffsets.getStaticFieldValuesForClass(clazz)));
  }

  private void writeDebugItem(DexDebugInfoForWriting debugInfo) {
    mixedSectionOffsets.setOffsetFor(debugInfo, dest.position());
    dest.putBytes(new DebugBytecodeWriter(debugInfo, mapping, graphLens).generate());
  }

  private int writeCodeItem(ProgramMethod method, DexWritableCode code) {
    int codeOffset = dest.align(4);
    mixedSectionOffsets.setOffsetFor(method.getDefinition(), codeOffset);
    // Fixed size header information.
    dest.putShort((short) code.getRegisterSize(method));
    dest.putShort((short) code.getIncomingRegisterSize(method));
    dest.putShort((short) code.getOutgoingRegisterSize());
    dest.putShort((short) code.getTries().length);
    dest.putInt(mixedSectionOffsets.getOffsetFor(code.getDebugInfoForWriting()));
    // Jump over the size.
    int insnSizeOffset = dest.position();
    dest.forward(4);
    // Write instruction stream.
    dest.putInstructions(appView, code, method, mapping, desugaredLibraryCodeToKeep);
    // Compute size and do the backward/forward dance to write the size at the beginning.
    int insnSize = dest.position() - insnSizeOffset - 4;
    dest.rewind(insnSize + 4);
    dest.putInt(insnSize / 2);
    dest.forward(insnSize);
    if (code.getTries().length > 0) {
      // The tries need to be 4 byte aligned.
      int beginOfTriesOffset = dest.align(4);
      // First write the handlers, so that we know their mixedSectionOffsets.
      dest.forward(code.getTries().length * 8);
      int beginOfHandlersOffset = dest.position();
      dest.putUleb128(code.getHandlers().length);
      short[] offsets = new short[code.getHandlers().length];
      int i = 0;
      for (TryHandler handler : code.getHandlers()) {
        offsets[i++] = (short) (dest.position() - beginOfHandlersOffset);
        boolean hasCatchAll = handler.catchAllAddr != TryHandler.NO_HANDLER;
        dest.putSleb128(hasCatchAll ? -handler.pairs.length : handler.pairs.length);
        for (TypeAddrPair pair : handler.pairs) {
          dest.putUleb128(mapping.getOffsetFor(pair.getType(graphLens)));
          dest.putUleb128(pair.addr);
          desugaredLibraryCodeToKeep.recordClass(pair.getType(graphLens));
        }
        if (hasCatchAll) {
          dest.putUleb128(handler.catchAllAddr);
        }
      }
      int endOfCodeOffset = dest.position();
      // Now write the tries.
      dest.moveTo(beginOfTriesOffset);
      for (Try aTry : code.getTries()) {
        dest.putInt(aTry.startAddress);
        dest.putShort((short) aTry.instructionCount);
        dest.putShort(offsets[aTry.handlerIndex]);
      }
      // And move to the end.
      dest.moveTo(endOfCodeOffset);
    }
    return codeOffset;
  }

  private void writeTypeList(DexTypeList list) {
    assert !list.isEmpty();
    mixedSectionOffsets.setOffsetFor(list, dest.align(4));
    DexType[] values = list.values;
    dest.putInt(values.length);
    for (DexType type : values) {
      dest.putShort((short) mapping.getOffsetFor(type));
    }
  }

  private void writeStringData(DexString string) {
    mixedSectionOffsets.setOffsetFor(string, dest.position());
    dest.putUleb128(string.size);
    dest.putBytes(string.content);
  }

  private void writeAnnotation(DexAnnotation annotation) {
    mixedSectionOffsets.setOffsetFor(annotation, dest.position());
    dest.putByte((byte) annotation.visibility);
    writeEncodedAnnotation(annotation.annotation, dest, mapping);
  }

  private void writeAnnotationSet(DexAnnotationSet set) {
    mixedSectionOffsets.setOffsetFor(set, dest.align(4));
    List<DexAnnotation> annotations = new ArrayList<>(Arrays.asList(set.annotations));
    annotations.sort(
        (a, b) ->
            a.annotation.type.acceptCompareTo(b.annotation.type, mapping.getCompareToVisitor()));
    dest.putInt(annotations.size());
    for (DexAnnotation annotation : annotations) {
      dest.putInt(mixedSectionOffsets.getOffsetFor(annotation));
    }
  }

  private void writeAnnotationSetRefList(ParameterAnnotationsList parameterAnnotationsList) {
    assert !parameterAnnotationsList.isEmpty();
    mixedSectionOffsets.setOffsetFor(parameterAnnotationsList, dest.align(4));
    dest.putInt(parameterAnnotationsList.countNonMissing());
    for (int i = 0; i < parameterAnnotationsList.size(); i++) {
      if (parameterAnnotationsList.isMissing(i)) {
        // b/62300145: Maintain broken ParameterAnnotations attribute by only outputting the
        // non-missing annotation lists.
        continue;
      }
      dest.putInt(mixedSectionOffsets.getOffsetFor(parameterAnnotationsList.get(i)));
    }
  }

  private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> void writeMemberAnnotations(
      List<D> items, ToIntFunction<D> getter) {
    for (D item : items) {
      dest.putInt(item.getReference().getOffset(mapping));
      dest.putInt(getter.applyAsInt(item));
    }
  }

  private void writeAnnotationDirectory(DexAnnotationDirectory annotationDirectory) {
    mixedSectionOffsets.setOffsetForAnnotationsDirectory(annotationDirectory, dest.align(4));
    dest.putInt(mixedSectionOffsets.getOffsetFor(annotationDirectory.getClazzAnnotations()));
    List<DexEncodedMethod> methodAnnotations =
        annotationDirectory.sortMethodAnnotations(mapping.getCompareToVisitor());
    List<DexEncodedMethod> parameterAnnotations =
        annotationDirectory.sortParameterAnnotations(mapping.getCompareToVisitor());
    List<DexEncodedField> fieldAnnotations =
        annotationDirectory.sortFieldAnnotations(mapping.getCompareToVisitor());
    dest.putInt(fieldAnnotations.size());
    dest.putInt(methodAnnotations.size());
    dest.putInt(parameterAnnotations.size());
    writeMemberAnnotations(
        fieldAnnotations, item -> mixedSectionOffsets.getOffsetFor(item.annotations()));
    writeMemberAnnotations(
        methodAnnotations, item -> mixedSectionOffsets.getOffsetFor(item.annotations()));
    writeMemberAnnotations(parameterAnnotations,
        item -> mixedSectionOffsets.getOffsetFor(item.parameterAnnotationsList));
  }

  private void writeEncodedFields(List<DexEncodedField> unsortedFields) {
    List<DexEncodedField> fields = new ArrayList<>(unsortedFields);
    fields.sort(
        (a, b) ->
            a.getReference().acceptCompareTo(b.getReference(), mapping.getCompareToVisitor()));
    int currentOffset = 0;
    for (DexEncodedField field : fields) {
      assert field.validateDexValue(appView.dexItemFactory());
      int nextOffset = mapping.getOffsetFor(field.getReference());
      assert nextOffset - currentOffset >= 0;
      dest.putUleb128(nextOffset - currentOffset);
      currentOffset = nextOffset;
      dest.putUleb128(field.accessFlags.getAsDexAccessFlags());
      desugaredLibraryCodeToKeep.recordField(field.getReference());
    }
  }

  private void writeEncodedMethods(Iterable<DexEncodedMethod> unsortedMethods) {
    List<DexEncodedMethod> methods = IterableUtils.toNewArrayList(unsortedMethods);
    methods.sort(
        (a, b) ->
            a.getReference().acceptCompareTo(b.getReference(), mapping.getCompareToVisitor()));
    int currentOffset = 0;
    for (DexEncodedMethod method : methods) {
      int nextOffset = mapping.getOffsetFor(method.getReference());
      assert nextOffset - currentOffset >= 0;
      dest.putUleb128(nextOffset - currentOffset);
      currentOffset = nextOffset;
      dest.putUleb128(method.accessFlags.getAsDexAccessFlags());
      DexWritableCode code = method.getDexWritableCodeOrNull();
      desugaredLibraryCodeToKeep.recordMethod(method.getReference());
      if (code == null) {
        assert method.shouldNotHaveCode();
        dest.putUleb128(0);
      } else {
        dest.putUleb128(mixedSectionOffsets.getOffsetFor(method, code));
      }
    }
  }

  private void writeClassData(DexProgramClass clazz) {
    assert clazz.hasMethodsOrFields();
    mixedSectionOffsets.setOffsetFor(clazz, dest.position());
    dest.putUleb128(clazz.staticFields().size());
    dest.putUleb128(clazz.instanceFields().size());
    dest.putUleb128(clazz.getMethodCollection().numberOfDirectMethods());
    dest.putUleb128(clazz.getMethodCollection().numberOfVirtualMethods());
    writeEncodedFields(clazz.staticFields());
    writeEncodedFields(clazz.instanceFields());
    writeEncodedMethods(clazz.directMethods());
    writeEncodedMethods(clazz.virtualMethods());
  }

  private void addStaticFieldValues(DexProgramClass clazz) {
    // We have collected the individual components of this array due to the data stored in
    // DexEncodedField#staticValues. However, we have to collect the DexEncodedArray itself
    // here.
    DexEncodedArray staticValues = clazz.computeStaticValuesArray(getNamingLens());
    if (staticValues != null) {
      mixedSectionOffsets.setStaticFieldValuesForClass(clazz, staticValues);
    }
  }

  private void writeMethodHandle(DexMethodHandle methodHandle) {
    checkThatInvokeCustomIsAllowed();
    MethodHandleType methodHandleDexType;
    switch (methodHandle.type) {
      case INVOKE_SUPER:
        methodHandleDexType = MethodHandleType.INVOKE_DIRECT;
        break;
      default:
        methodHandleDexType = methodHandle.type;
        break;
    }
    assert dest.isAligned(4);
    dest.putShort(methodHandleDexType.getValue());
    dest.putShort((short) 0); // unused
    int fieldOrMethodIdx;
    if (methodHandle.isMethodHandle()) {
      fieldOrMethodIdx = mapping.getOffsetFor(methodHandle.asMethod());
    } else {
      assert methodHandle.isFieldHandle();
      fieldOrMethodIdx = mapping.getOffsetFor(methodHandle.asField());
    }
    assert (fieldOrMethodIdx & 0xFFFF) == fieldOrMethodIdx;
    dest.putShort((short) fieldOrMethodIdx);
    dest.putShort((short) 0); // unused
  }

  private void writeCallSite(DexCallSite callSite) {
    checkThatInvokeCustomIsAllowed();
    assert dest.isAligned(4);
    dest.putInt(mixedSectionOffsets.getOffsetFor(callSite.getEncodedArray()));
  }

  private void writeEncodedArray(DexEncodedArray array) {
    mixedSectionOffsets.setOffsetFor(array, dest.position());
    dest.putUleb128(array.values.length);
    for (DexValue value : array.values) {
      value.writeTo(dest, mapping);
    }
  }

  public void writeMap(Layout layout) {
    int startOfMap = dest.align(4);
    layout.setMapOffset(startOfMap);
    dest.forward(4); // Leave space for size;
    List<MapItem> mapItems = layout.generateMapInfo(this);
    int size = 0;
    for (MapItem mapItem : mapItems) {
      size += includeStringData ? mapItem.write(dest) : mapItem.size();
    }
    dest.moveTo(startOfMap);
    dest.putInt(size);
    dest.forward(size * Constants.TYPE_MAP_LIST_ITEM_SIZE);
  }

  private byte[] dexVersionBytes() {
    if (options.testing.dexContainerExperiment) {
      return DexVersion.V41.getBytes();
    }
    // TODO(b/269089718): Remove this testing option and always emit DEX version 040 if DEX contains
    //  identifiers with whitespace.
    if (options.testing.dexVersion40FromApiLevel30
        && options.getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.R)) {
      return DexVersion.V40.getBytes();
    }
    return options.testing.forceDexVersionBytes != null
        ? options.testing.forceDexVersionBytes
        : DexVersion.getDexVersion(options.getMinApiLevel()).getBytes();
  }

  private void writeHeader(Layout layout) {
    dest.moveTo(layout.headerOffset);
    dest.putBytes(Constants.DEX_FILE_MAGIC_PREFIX);
    dest.putBytes(dexVersionBytes());
    dest.putByte(Constants.DEX_FILE_MAGIC_SUFFIX);
    // Leave out checksum and signature for now.
    dest.moveTo(layout.headerOffset + Constants.FILE_SIZE_OFFSET);
    dest.putInt(layout.getEndOfFile() - layout.headerOffset);
    dest.putInt(layout.getHeaderSize());
    dest.putInt(Constants.ENDIAN_CONSTANT);
    dest.putInt(0);
    dest.putInt(0);
    dest.putInt(layout.getMapOffset());
    int numberOfStrings = mapping.getStrings().size();
    dest.putInt(numberOfStrings);
    dest.putInt(numberOfStrings == 0 ? 0 : layout.stringIdsOffset);
    int numberOfTypes = mapping.getTypes().size();
    dest.putInt(numberOfTypes);
    dest.putInt(numberOfTypes == 0 ? 0 : layout.typeIdsOffset);
    int numberOfProtos = mapping.getProtos().size();
    dest.putInt(numberOfProtos);
    dest.putInt(numberOfProtos == 0 ? 0 : layout.protoIdsOffset);
    int numberOfFields = mapping.getFields().size();
    dest.putInt(numberOfFields);
    dest.putInt(numberOfFields == 0 ? 0 : layout.fieldIdsOffset);
    int numberOfMethods = mapping.getMethods().size();
    dest.putInt(numberOfMethods);
    dest.putInt(numberOfMethods == 0 ? 0 : layout.methodIdsOffset);
    int numberOfClasses = mapping.getClasses().length;
    dest.putInt(numberOfClasses);
    dest.putInt(numberOfClasses == 0 ? 0 : layout.classDefsOffset);
    if (layout.isContainerSection()) {
      // Fields data_size and data_off are zero for all sections in a container DEX.
      dest.putInt(0); // data_size
      dest.putInt(0); // data_off
      dest.putInt(0); // container_size will be updated in final pass.
      dest.putInt(layout.headerOffset); // container_off
    } else {
      dest.putInt(layout.getDataSectionSize());
      dest.putInt(layout.dataSectionOffset);
    }
    assert dest.position() == layout.stringIdsOffset;
  }

  private void writeSignature(Layout layout) {
    writeSignature(layout, dest);
  }

  public void writeSignature(Layout layout, DexOutputBuffer dexOutputBuffer) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(
          dexOutputBuffer.asArray(),
          layout.headerOffset + Constants.FILE_SIZE_OFFSET,
          layout.getEndOfFile() - layout.headerOffset - Constants.FILE_SIZE_OFFSET);
      md.digest(dexOutputBuffer.asArray(), layout.headerOffset + Constants.SIGNATURE_OFFSET, 20);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void writeChecksum(Layout layout) {
    writeChecksum(layout, dest);
  }

  public void writeChecksum(Layout layout, DexOutputBuffer dexOutputBuffer) {
    Adler32 adler = new Adler32();
    adler.update(
        dexOutputBuffer.asArray(),
        layout.headerOffset + Constants.SIGNATURE_OFFSET,
        layout.getEndOfFile() - layout.headerOffset - Constants.SIGNATURE_OFFSET);
    dexOutputBuffer.moveTo(layout.headerOffset + Constants.CHECKSUM_OFFSET);
    dexOutputBuffer.putInt((int) adler.getValue());
  }

  private static int alignSize(int bytes, int value) {
    int mask = bytes - 1;
    return (value + mask) & ~mask;
  }

  public static class MapItem {
    final int type;
    final int offset;
    final int length;

    public MapItem(int type, int offset, int size) {
      this.type = type;
      this.offset = offset;
      this.length = size;
    }

    public int getType() {
      return type;
    }

    public int getOffset() {
      return offset;
    }

    public int getLength() {
      return length;
    }

    public int write(DexOutputBuffer dest) {
      if (length == 0) {
        return 0;
      }
      dest.putShort((short) type);
      dest.putShort((short) 0);
      dest.putInt(length);
      dest.putInt(offset);
      return 1;
    }

    public int size() {
      return length == 0 && type != Constants.TYPE_STRING_DATA_ITEM ? 0 : 1;
    }
  }

  public static class Layout {

    private static final int NOT_SET = -1;

    // Fixed size constant pool sections
    final int headerOffset;
    final int stringIdsOffset;
    final int typeIdsOffset;
    final int protoIdsOffset;
    final int fieldIdsOffset;
    final int methodIdsOffset;
    final int classDefsOffset;
    final int callSiteIdsOffset;
    final int methodHandleIdsOffset;
    final int dataSectionOffset;
    final DexVersion.Layout layoutType;

    // Mixed size sections
    private int codesOffset = NOT_SET; // aligned
    private int debugInfosOffset = NOT_SET;

    private int typeListsOffset = NOT_SET; // aligned
    private int stringDataOffsets = NOT_SET;
    private int annotationsOffset = NOT_SET;
    private int annotationSetsOffset = NOT_SET; // aligned
    private int annotationSetRefListsOffset = NOT_SET; // aligned
    private int annotationDirectoriesOffset = NOT_SET; // aligned
    private int classDataOffset = NOT_SET;
    private int encodedArraysOffset = NOT_SET;
    private int mapOffset = NOT_SET;
    private int endOfFile = NOT_SET;
    private int codeCount = NOT_SET;

    private Layout(
        int headerOffset,
        int stringIdsOffset,
        int typeIdsOffset,
        int protoIdsOffset,
        int fieldIdsOffset,
        int methodIdsOffset,
        int classDefsOffset,
        int callSiteIdsOffset,
        int methodHandleIdsOffset,
        int dataSectionOffset,
        DexVersion.Layout layoutType) {
      this.headerOffset = headerOffset;
      this.stringIdsOffset = stringIdsOffset;
      this.typeIdsOffset = typeIdsOffset;
      this.protoIdsOffset = protoIdsOffset;
      this.fieldIdsOffset = fieldIdsOffset;
      this.methodIdsOffset = methodIdsOffset;
      this.classDefsOffset = classDefsOffset;
      this.callSiteIdsOffset = callSiteIdsOffset;
      this.methodHandleIdsOffset = methodHandleIdsOffset;
      this.dataSectionOffset = dataSectionOffset;
      this.layoutType = layoutType;
      assert stringIdsOffset <= typeIdsOffset;
      assert typeIdsOffset <= protoIdsOffset;
      assert protoIdsOffset <= fieldIdsOffset;
      assert fieldIdsOffset <= methodIdsOffset;
      assert methodIdsOffset <= classDefsOffset;
      assert classDefsOffset <= dataSectionOffset;
      assert callSiteIdsOffset <= dataSectionOffset;
      assert methodHandleIdsOffset <= dataSectionOffset;
    }

    static Layout from(ObjectToOffsetMapping mapping) {
      return from(mapping, 0, SINGLE_DEX, true);
    }

    static Layout from(
        ObjectToOffsetMapping mapping,
        int offset,
        DexVersion.Layout layoutType,
        boolean includeStringData) {
      assert offset == 0 || layoutType.isContainer();
      return new Layout(
          offset,
          offset += layoutType.getHeaderSize(),
          offset +=
              includeStringData
                  ? mapping.getStrings().size() * Constants.TYPE_STRING_ID_ITEM_SIZE
                  : 0,
          offset += mapping.getTypes().size() * Constants.TYPE_TYPE_ID_ITEM_SIZE,
          offset += mapping.getProtos().size() * Constants.TYPE_PROTO_ID_ITEM_SIZE,
          offset += mapping.getFields().size() * Constants.TYPE_FIELD_ID_ITEM_SIZE,
          offset += mapping.getMethods().size() * Constants.TYPE_METHOD_ID_ITEM_SIZE,
          offset += mapping.getClasses().length * Constants.TYPE_CLASS_DEF_ITEM_SIZE,
          offset += mapping.getCallSites().size() * Constants.TYPE_CALL_SITE_ID_ITEM_SIZE,
          offset += mapping.getMethodHandles().size() * Constants.TYPE_METHOD_HANDLE_ITEM_SIZE,
          layoutType);
    }

    int getDataSectionSize() {
      int size = getEndOfFile() - dataSectionOffset;
      assert size % 4 == 0;
      return size;
    }

    private boolean isValidOffset(int value, boolean isAligned) {
      return value != NOT_SET && (!isAligned || value % 4 == 0);
    }

    public int getCodesOffset() {
      assert isValidOffset(codesOffset, true);
      return codesOffset;
    }

    public void setCodesOffset(int codesOffset) {
      assert this.codesOffset == NOT_SET;
      this.codesOffset = codesOffset;
    }

    public void setCodeCount(int codeCount) {
      assert this.codeCount == NOT_SET;
      this.codeCount = codeCount;
    }

    public int getCodeCount() {
      return codeCount;
    }

    public int getDebugInfosOffset() {
      assert isValidOffset(debugInfosOffset, false);
      return debugInfosOffset;
    }

    public void setDebugInfosOffset(int debugInfosOffset) {
      assert this.debugInfosOffset == NOT_SET;
      this.debugInfosOffset = debugInfosOffset;
    }

    public int getTypeListsOffset() {
      assert isValidOffset(typeListsOffset, true);
      return typeListsOffset;
    }

    public void setTypeListsOffset(int typeListsOffset) {
      assert this.typeListsOffset == NOT_SET;
      this.typeListsOffset = typeListsOffset;
    }

    public int getStringDataOffsets() {
      assert isValidOffset(stringDataOffsets, false);
      return stringDataOffsets;
    }

    public void setStringDataOffsets(int stringDataOffsets) {
      assert this.stringDataOffsets == NOT_SET;
      this.stringDataOffsets = stringDataOffsets;
    }

    public int getAnnotationsOffset() {
      assert isValidOffset(annotationsOffset, false);
      return annotationsOffset;
    }

    public void setAnnotationsOffset(int annotationsOffset) {
      assert this.annotationsOffset == NOT_SET;
      this.annotationsOffset = annotationsOffset;
    }

    public int getAnnotationSetsOffset() {
      assert isValidOffset(annotationSetsOffset, true);
      return annotationSetsOffset;
    }

    public void alreadySetOffset(int ignored) {
      // Intentionally empty.
    }

    public void setAnnotationSetsOffset(int annotationSetsOffset) {
      assert this.annotationSetsOffset == NOT_SET;
      this.annotationSetsOffset = annotationSetsOffset;
    }

    public int getAnnotationSetRefListsOffset() {
      assert isValidOffset(annotationSetRefListsOffset, true);
      return annotationSetRefListsOffset;
    }

    public void setAnnotationSetRefListsOffset(int annotationSetRefListsOffset) {
      assert this.annotationSetRefListsOffset == NOT_SET;
      this.annotationSetRefListsOffset = annotationSetRefListsOffset;
    }

    public int getAnnotationDirectoriesOffset() {
      assert isValidOffset(annotationDirectoriesOffset, true);
      return annotationDirectoriesOffset;
    }

    public void setAnnotationDirectoriesOffset(int annotationDirectoriesOffset) {
      assert this.annotationDirectoriesOffset == NOT_SET;
      this.annotationDirectoriesOffset = annotationDirectoriesOffset;
    }

    public int getClassDataOffset() {
      assert isValidOffset(classDataOffset, false);
      return classDataOffset;
    }

    public void setClassDataOffset(int classDataOffset) {
      assert this.classDataOffset == NOT_SET;
      this.classDataOffset = classDataOffset;
    }

    public int getEncodedArraysOffset() {
      assert isValidOffset(encodedArraysOffset, false);
      return encodedArraysOffset;
    }

    public void setEncodedArraysOffset(int encodedArraysOffset) {
      assert this.encodedArraysOffset == NOT_SET;
      this.encodedArraysOffset = encodedArraysOffset;
    }

    public int getMapOffset() {
      return mapOffset;
    }

    public void setMapOffset(int mapOffset) {
      this.mapOffset = mapOffset;
    }

    public boolean isContainerSection() {
      return layoutType.isContainer();
    }

    public int getHeaderSize() {
      return layoutType.getHeaderSize();
    }

    public List<MapItem> generateMapInfo(FileWriter fileWriter) {
      return generateMapInfo(
          fileWriter,
          0,
          fileWriter.mixedSectionOffsets.getStringData().size(),
          stringIdsOffset,
          getStringDataOffsets());
    }

    public List<MapItem> generateMapInfo(
        FileWriter fileWriter,
        int headerOffset,
        int stringIdsSize,
        int stringIdsOffset,
        int stringDataOffsets) {
      List<MapItem> mapItems = new ArrayList<>();
      mapItems.add(new MapItem(Constants.TYPE_HEADER_ITEM, headerOffset, 1));
      mapItems.add(
          new MapItem(
              Constants.TYPE_STRING_ID_ITEM,
              stringIdsOffset,
              fileWriter.mapping.getStrings().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_TYPE_ID_ITEM, typeIdsOffset, fileWriter.mapping.getTypes().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_PROTO_ID_ITEM, protoIdsOffset, fileWriter.mapping.getProtos().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_FIELD_ID_ITEM, fieldIdsOffset, fileWriter.mapping.getFields().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_METHOD_ID_ITEM,
              methodIdsOffset,
              fileWriter.mapping.getMethods().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_CLASS_DEF_ITEM,
              classDefsOffset,
              fileWriter.mapping.getClasses().length));
      mapItems.add(
          new MapItem(
              Constants.TYPE_CALL_SITE_ID_ITEM,
              callSiteIdsOffset,
              fileWriter.mapping.getCallSites().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_METHOD_HANDLE_ITEM,
              methodHandleIdsOffset,
              fileWriter.mapping.getMethodHandles().size()));
      mapItems.add(new MapItem(Constants.TYPE_CODE_ITEM, getCodesOffset(), codeCount));
      mapItems.add(
          new MapItem(
              Constants.TYPE_DEBUG_INFO_ITEM,
              getDebugInfosOffset(),
              fileWriter.mixedSectionOffsets.getDebugInfos().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_TYPE_LIST,
              getTypeListsOffset(),
              fileWriter.mixedSectionOffsets.getTypeLists().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_STRING_DATA_ITEM,
              stringDataOffsets,
              stringDataOffsets == 0 ? 0 : stringIdsSize));
      mapItems.add(
          new MapItem(
              Constants.TYPE_ANNOTATION_ITEM,
              getAnnotationsOffset(),
              fileWriter.mixedSectionOffsets.getAnnotations().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_CLASS_DATA_ITEM,
              getClassDataOffset(),
              fileWriter.mixedSectionOffsets.getClassesWithData().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_ENCODED_ARRAY_ITEM,
              getEncodedArraysOffset(),
              fileWriter.mixedSectionOffsets.getEncodedArrays().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_ANNOTATION_SET_ITEM,
              getAnnotationSetsOffset(),
              fileWriter.mixedSectionOffsets.getAnnotationSets().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_ANNOTATION_SET_REF_LIST,
              getAnnotationSetRefListsOffset(),
              fileWriter.mixedSectionOffsets.getAnnotationSetRefLists().size()));
      mapItems.add(
          new MapItem(
              Constants.TYPE_ANNOTATIONS_DIRECTORY_ITEM,
              getAnnotationDirectoriesOffset(),
              fileWriter.mixedSectionOffsets.getAnnotationDirectories().size()));
      mapItems.add(new MapItem(Constants.TYPE_MAP_LIST, getMapOffset(), 1));
      mapItems.sort(Comparator.comparingInt(MapItem::getOffset));
      return mapItems;
    }

    public int getEndOfFile() {
      return endOfFile;
    }

    public void setEndOfFile(int endOfFile) {
      this.endOfFile = endOfFile;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (false) {
        builder.append("headerOffset: ").append(headerOffset).append("\n");
        builder.append("stringIdsOffset: ").append(stringIdsOffset).append("\n");
        builder.append("typeIdsOffset: ").append(typeIdsOffset).append("\n");
        builder.append("protoIdsOffset: ").append(protoIdsOffset).append("\n");
        builder.append("fieldIdsOffset: ").append(fieldIdsOffset).append("\n");
        builder.append("methodIdsOffset: ").append(methodIdsOffset).append("\n");
        builder.append("classDefsOffset: ").append(classDefsOffset).append("\n");
        builder.append("callSiteIdsOffset: ").append(callSiteIdsOffset).append("\n");
        builder.append("methodHandleIdsOffset: ").append(methodHandleIdsOffset).append("\n");
        builder.append("dataSectionOffset: ").append(dataSectionOffset).append("\n");

        // Mixed size sections
        builder.append("codesOffset: ").append(codesOffset).append("\n");
        builder.append("debugInfosOffset: ").append(debugInfosOffset).append("\n");

        builder.append("typeListsOffset: ").append(typeListsOffset).append("\n");
        builder.append("stringDataOffsets: ").append(stringDataOffsets).append("\n");
        builder.append("annotationsOffset: ").append(annotationsOffset).append("\n");
        builder.append("annotationSetsOffset: ").append(annotationSetsOffset).append("\n");
        builder
            .append("annotationSetRefListsOffset: ")
            .append(annotationSetRefListsOffset)
            .append("\n");
        builder
            .append("annotationDirectoriesOffset: ")
            .append(annotationDirectoriesOffset)
            .append("\n");
        builder.append("classDataOffset: ").append(classDataOffset).append("\n");
        builder.append("encodedArraysOffset: ").append(encodedArraysOffset).append("\n");
        builder.append("mapOffset: ").append(mapOffset).append("\n");
        builder.append("endOfFile: ").append(endOfFile).append("\n");
      } else {
        builder.append("Header: ").append(stringIdsOffset - headerOffset).append("\n");
        builder.append("StringIds: ").append(typeIdsOffset - stringIdsOffset).append("\n");
        builder.append("typeIds: ").append(protoIdsOffset - typeIdsOffset).append("\n");
        builder.append("protoIds: ").append(fieldIdsOffset - protoIdsOffset).append("\n");
        builder.append("fieldIds: ").append(methodIdsOffset - fieldIdsOffset).append("\n");
        builder.append("methodIds: ").append(classDefsOffset - methodIdsOffset).append("\n");
        builder.append("classDefs: ").append(callSiteIdsOffset - classDefsOffset).append("\n");
        builder
            .append("callSiteIds: ")
            .append(methodHandleIdsOffset - callSiteIdsOffset)
            .append("\n");
        builder
            .append("methodHandleIds: ")
            .append(dataSectionOffset - methodHandleIdsOffset)
            .append("\n");

        // Mixed size sections
        builder.append("code: ").append(debugInfosOffset - codesOffset).append("\n");
        builder.append("debugInfo: ").append(typeListsOffset - debugInfosOffset).append("\n");

        builder
            .append("typeList: ")
            .append(
                (stringDataOffsets > 0 ? stringDataOffsets : annotationsOffset) - typeListsOffset)
            .append("\n");
        builder
            .append("stringData: ")
            .append(stringDataOffsets > 0 ? annotationsOffset - stringDataOffsets : 0)
            .append("\n");
        builder.append("annotations: ").append(classDataOffset - annotationsOffset).append("\n");
        builder.append("classData: ").append(encodedArraysOffset - classDataOffset).append("\n");
        builder
            .append("encodedArrays: ")
            .append(mapOffset - annotationSetRefListsOffset)
            .append("\n");
        builder
            .append("annotationSets: ")
            .append(annotationSetRefListsOffset - annotationSetsOffset)
            .append("\n");
        builder
            .append("annotationSetRefLists: ")
            .append(annotationDirectoriesOffset - annotationSetRefListsOffset)
            .append("\n");
        builder
            .append("annotationDirectories: ")
            .append(mapOffset - annotationDirectoriesOffset)
            .append("\n");
        builder.append("map: ").append(endOfFile - mapOffset).append("\n");
        builder.append("endOfFile: ").append(endOfFile).append("\n");
      }
      return builder.toString();
    }
  }

  /**
   * Encapsulates information on the offsets of items in the sections of the mixed data part of the
   * DEX file. Initially, items are collected using the {@link MixedSectionCollection} traversal and
   * all offsets are unset. When writing a section, the offsets of the written items are stored.
   * These offsets are then used to resolve cross-references between items from different sections
   * into a file offset.
   */
  static class MixedSectionOffsets extends MixedSectionCollection {

    private static final int NOT_SET = -1;
    private static final int NOT_KNOWN = -2;

    private final Reference2IntMap<DexEncodedMethod> codes = createReference2IntMap();
    private final Object2IntMap<DexDebugInfoForWriting> debugInfos = createObject2IntMap();
    private final Object2IntMap<DexTypeList> typeLists = createObject2IntMap();
    private final Reference2IntMap<DexString> stringData = createReference2IntMap();
    private final Object2IntMap<DexAnnotation> annotations = createObject2IntMap();
    private final Object2IntMap<DexAnnotationSet> annotationSets = createObject2IntMap();
    private final Object2IntMap<ParameterAnnotationsList> annotationSetRefLists
        = createObject2IntMap();
    private final Object2IntMap<DexAnnotationDirectory> annotationDirectories
        = createObject2IntMap();
    private final Reference2IntMap<DexProgramClass> classesWithData = createReference2IntMap();
    private final Object2IntMap<DexEncodedArray> encodedArrays = createObject2IntMap();
    private final Map<DexProgramClass, DexAnnotationDirectory> classToAnnotationDirectory =
        new IdentityHashMap<>();
    private final Map<DexProgramClass, DexEncodedArray> classToStaticFieldValues =
        new IdentityHashMap<>();

    private final InternalOptions options;

    private static <T> Object2IntMap<T> createObject2IntMap() {
      Object2IntMap<T> result = new Object2IntLinkedOpenHashMap<>();
      result.defaultReturnValue(NOT_KNOWN);
      return result;
    }

    private static <T> Reference2IntMap<T> createReference2IntMap() {
      Reference2IntMap<T> result = new Reference2IntLinkedOpenHashMap<>();
      result.defaultReturnValue(NOT_KNOWN);
      return result;
    }

    private MixedSectionOffsets(InternalOptions options) {
      this.options = options;
    }

    private <T> boolean add(Object2IntMap<T> map, T item) {
      if (!map.containsKey(item)) {
        map.put(item, NOT_SET);
        return true;
      }
      return false;
    }

    private <T> boolean add(Reference2IntMap<T> map, T item) {
      if (!map.containsKey(item)) {
        map.put(item, NOT_SET);
        return true;
      }
      return false;
    }

    @Override
    public boolean add(DexProgramClass aClassWithData) {
      return add(classesWithData, aClassWithData);
    }

    @Override
    public boolean add(DexEncodedArray encodedArray) {
      return add(encodedArrays, encodedArray);
    }

    @Override
    public boolean add(DexAnnotationSet annotationSet) {
      if (!options.canHaveDalvikEmptyAnnotationSetBug() && annotationSet.isEmpty()) {
        return false;
      }
      return add(annotationSets, annotationSet);
    }

    @Override
    public void visit(DexEncodedMethod method) {
      method.collectMixedSectionItemsWithCodeMapping(this);
    }

    @Override
    public boolean add(DexEncodedMethod method, DexWritableCode code) {
      return add(codes, method);
    }

    @Override
    public boolean add(DexDebugInfoForWriting debugInfo) {
      return add(debugInfos, debugInfo);
    }

    @Override
    public boolean add(DexTypeList typeList) {
      if (typeList.isEmpty()) {
        return false;
      }
      return add(typeLists, typeList);
    }

    @Override
    public boolean add(ParameterAnnotationsList annotationSetRefList) {
      if (annotationSetRefList.isEmpty()) {
        return false;
      }
      return add(annotationSetRefLists, annotationSetRefList);
    }

    @Override
    public boolean add(DexAnnotation annotation) {
      return add(annotations, annotation);
    }

    @Override
    public void setAnnotationsDirectoryForClass(
        DexProgramClass clazz, DexAnnotationDirectory annotationDirectory) {
      DexAnnotationDirectory previous = classToAnnotationDirectory.put(clazz, annotationDirectory);
      assert previous == null;
      add(annotationDirectories, annotationDirectory);
    }

    @Override
    public void setStaticFieldValuesForClass(
        DexProgramClass clazz, DexEncodedArray staticFieldValues) {
      DexEncodedArray previous = classToStaticFieldValues.put(clazz, staticFieldValues);
      assert previous == null;
      add(staticFieldValues);
    }

    public boolean add(DexString string) {
      return add(stringData, string);
    }

    public Collection<DexEncodedMethod> getCodes() {
      return codes.keySet();
    }

    public Collection<DexDebugInfoForWriting> getDebugInfos() {
      return debugInfos.keySet();
    }

    public Collection<DexTypeList> getTypeLists() {
      return typeLists.keySet();
    }

    public Collection<DexString> getStringData() {
      return stringData.keySet();
    }

    public Collection<DexAnnotation> getAnnotations() {
      return annotations.keySet();
    }

    public Collection<DexAnnotationSet> getAnnotationSets() {
      return annotationSets.keySet();
    }

    public Collection<ParameterAnnotationsList> getAnnotationSetRefLists() {
      return annotationSetRefLists.keySet();
    }

    public Collection<DexProgramClass> getClassesWithData() {
      return classesWithData.keySet();
    }

    public Collection<DexAnnotationDirectory> getAnnotationDirectories() {
      return annotationDirectories.keySet();
    }

    public Collection<DexEncodedArray> getEncodedArrays() {
      return encodedArrays.keySet();
    }

    private <T> int lookup(T item, Object2IntMap<T> table) {
      if (item == null) {
        return Constants.NO_OFFSET;
      }
      int offset = table.getInt(item);
      assert offset != NOT_SET && offset != NOT_KNOWN;
      return offset;
    }

    private <T> int lookup(T item, Reference2IntMap<T> table) {
      if (item == null) {
        return Constants.NO_OFFSET;
      }
      int offset = table.getInt(item);
      assert offset != NOT_SET && offset != NOT_KNOWN;
      return offset;
    }

    public int getOffsetFor(DexString item) {
      return lookup(item, stringData);
    }

    public int getOffsetFor(DexTypeList parameters) {
      if (parameters.isEmpty()) {
        return 0;
      }
      return lookup(parameters, typeLists);
    }

    public int getOffsetFor(DexProgramClass aClassWithData) {
      return lookup(aClassWithData, classesWithData);
    }

    public int getOffsetFor(DexEncodedArray encodedArray) {
      return lookup(encodedArray, encodedArrays);
    }

    public int getOffsetFor(DexDebugInfoForWriting debugInfo) {
      return lookup(debugInfo, debugInfos);
    }

    public int getOffsetForAnnotationsDirectory(DexProgramClass clazz) {
      if (!clazz.hasClassOrMemberAnnotations()) {
        return Constants.NO_OFFSET;
      }
      int offset = annotationDirectories.getInt(getAnnotationDirectoryForClass(clazz));
      assert offset != NOT_KNOWN;
      return offset;
    }

    public int getOffsetFor(DexAnnotation annotation) {
      return lookup(annotation, annotations);
    }

    public int getOffsetFor(DexAnnotationSet annotationSet) {
      if (!options.canHaveDalvikEmptyAnnotationSetBug() && annotationSet.isEmpty()) {
        return 0;
      }
      return lookup(annotationSet, annotationSets);
    }

    public int getOffsetFor(ParameterAnnotationsList annotationSetRefList) {
      if (annotationSetRefList.isEmpty()) {
        return 0;
      }
      return lookup(annotationSetRefList, annotationSetRefLists);
    }

    public int getOffsetFor(DexEncodedMethod method, DexWritableCode code) {
      return lookup(method, codes);
    }

    private <T> void setOffsetFor(T item, int offset, Object2IntMap<T> map) {
      int old = map.put(item, offset);
      assert old <= NOT_SET;
    }

    private <T> void setOffsetFor(T item, int offset, Reference2IntMap<T> map) {
      int old = map.put(item, offset);
      assert old <= NOT_SET;
    }

    void setOffsetFor(DexDebugInfoForWriting debugInfo, int offset) {
      setOffsetFor(debugInfo, offset, debugInfos);
    }

    void setOffsetFor(DexEncodedMethod method, int offset) {
      setOffsetFor(method, offset, codes);
    }

    void setOffsetFor(DexTypeList typeList, int offset) {
      assert offset != 0 && !typeLists.isEmpty();
      setOffsetFor(typeList, offset, typeLists);
    }

    void setOffsetFor(DexString string, int offset) {
      setOffsetFor(string, offset, stringData);
    }

    void setOffsetFor(DexAnnotation annotation, int offset) {
      setOffsetFor(annotation, offset, annotations);
    }

    void setOffsetFor(DexAnnotationSet annotationSet, int offset) {
      assert options.canHaveDalvikEmptyAnnotationSetBug() || !annotationSet.isEmpty();
      setOffsetFor(annotationSet, offset, annotationSets);
    }

    void setOffsetForAnnotationsDirectory(DexAnnotationDirectory annotationDirectory, int offset) {
      setOffsetFor(annotationDirectory, offset, annotationDirectories);
    }

    void setOffsetFor(DexProgramClass aClassWithData, int offset) {
      setOffsetFor(aClassWithData, offset, classesWithData);
    }

    void setOffsetFor(DexEncodedArray encodedArray, int offset) {
      setOffsetFor(encodedArray, offset, encodedArrays);
    }

    void setOffsetFor(ParameterAnnotationsList annotationSetRefList, int offset) {
      assert offset != 0 && !annotationSetRefList.isEmpty();
      setOffsetFor(annotationSetRefList, offset, annotationSetRefLists);
    }

    DexAnnotationDirectory getAnnotationDirectoryForClass(DexProgramClass clazz) {
      return classToAnnotationDirectory.get(clazz);
    }

    DexEncodedArray getStaticFieldValuesForClass(DexProgramClass clazz) {
      return classToStaticFieldValues.get(clazz);
    }
  }

  private class ProgramClassDependencyCollector extends ProgramClassVisitor {

    private final Set<DexProgramClass> includedClasses = Sets.newIdentityHashSet();

    ProgramClassDependencyCollector(AppView<?> appView, DexProgramClass[] includedClasses) {
      super(appView);
      Collections.addAll(this.includedClasses, includedClasses);
    }

    @Override
    public void visit(DexProgramClass clazz) {
      // Only visit classes that are part of the current file.
      if (includedClasses.contains(clazz)) {
        clazz.addDependencies(mixedSectionOffsets);
      }
    }
  }

  private void checkThatInvokeCustomIsAllowed() {
    if (!options.canUseInvokeCustom()) {
      throw options.reporter.fatalError(
          new UnsupportedInvokeCustomDiagnostic(Origin.unknown(), Position.UNKNOWN));
    }
  }
}
