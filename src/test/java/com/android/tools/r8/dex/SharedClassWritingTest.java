// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.errors.DexOverflowException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexAnnotationSetRefList;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IgnoreContentsOutputSink;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SharedClassWritingTest {

  private final static String PREFIX = "A";
  private final static int NUMBER_OF_FILES = 500;

  DexItemFactory dexItemFactory = new DexItemFactory();

  private DexString[] strings;

  @Before
  public void generateStringArray() {
    strings = new DexString[Constants.MAX_NON_JUMBO_INDEX + 100];
    for (int i = 0; i < strings.length; i++) {
      // Format i as string with common prefix and leading 0's so that they are in the array
      // in lexicographic order.
      String string = PREFIX + StringUtils.zeroPrefix(i, 8);
      strings[i] = dexItemFactory.createString(string);
    }
  }

  private DexEncodedMethod makeMethod(DexType holder, int stringCount, int startOffset) {
    assert stringCount + startOffset < strings.length;
    Instruction[] instructions = new Instruction[stringCount + 1];
    for (int i = 0; i < stringCount; i++) {
      instructions[i] = new ConstString(0, strings[startOffset + i]);
    }
    instructions[stringCount] = new ReturnVoid();
    DexCode code = new DexCode(1, 0, 0, instructions, new Try[0], new TryHandler[0], null,
        strings[startOffset + stringCount - 1]);
    return new DexEncodedMethod(
        dexItemFactory.createMethod(
            holder, dexItemFactory.createProto(dexItemFactory.voidType), "theMethod"),
        MethodAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC, false),
        DexAnnotationSet.empty(),
        DexAnnotationSetRefList.empty(),
        code);
  }

  private DexProgramClass makeClass(String name, int stringCount, int startOffset,
      Collection<DexProgramClass> synthesizedFrom) {
    String desc = DescriptorUtils.javaTypeToDescriptor(name);
    DexType type = dexItemFactory.createType(desc);
    return new DexProgramClass(
        type,
        null,
        new SynthesizedOrigin("test", getClass()),
        ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC),
        dexItemFactory.objectType,
        DexTypeList.empty(),
        null,
        null,
        Collections.emptyList(),
        DexAnnotationSet.empty(),
        DexEncodedField.EMPTY_ARRAY,
        DexEncodedField.EMPTY_ARRAY,
        DexEncodedMethod.EMPTY_ARRAY,
        new DexEncodedMethod[] {makeMethod(type, stringCount, startOffset)},
        synthesizedFrom);
  }

  @Test
  public void manyFilesWithSharedSynthesizedClass()
      throws ExecutionException, IOException, DexOverflowException {

    // Create classes that all reference enough strings to overflow the index, but are all
    // at different offsets in the strings array. This ensures we trigger multiple rounds of
    // rewrites.
    List<DexProgramClass> classes = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_FILES; i++) {
      classes.add(makeClass("Class" + i, Constants.MAX_NON_JUMBO_INDEX - 1, i % 100,
          Collections.emptyList()));
    }

    // Create a shared class that references strings above the maximum.
    DexProgramClass sharedSynthesizedClass = makeClass("SharedSynthesized", 100,
        Constants.MAX_NON_JUMBO_INDEX - 1,
        classes);

    DexApplication.Builder builder = DirectMappedDexApplication
        .builder(dexItemFactory, new Timing("SharedClassWritingTest"));
    builder.addSynthesizedClass(sharedSynthesizedClass, false);
    classes.forEach(builder::addProgramClass);
    DexApplication application = builder.build();

    InternalOptions options = new InternalOptions(dexItemFactory,
        new Reporter(new DefaultDiagnosticsHandler()));
    options.outputMode = OutputMode.FilePerInputClass;
    ApplicationWriter writer = new ApplicationWriter(application,
        options, null, null, NamingLens.getIdentityLens(), null);
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    CollectInfoOutputSink sink = new CollectInfoOutputSink();
    writer.write(sink, executorService);
    List<Set<String>> generatedDescriptors = sink.getDescriptors();
    // Check all files present.
    Assert.assertEquals(NUMBER_OF_FILES, generatedDescriptors.size());
    // And each file contains two classes of which one is the shared one.
    for (Set<String> classDescriptors : generatedDescriptors) {
      Assert.assertEquals(2, classDescriptors.size());
      Assert
          .assertTrue(classDescriptors.contains(sharedSynthesizedClass.type.toDescriptorString()));
    }
  }

  private static class CollectInfoOutputSink extends IgnoreContentsOutputSink {

    private final List<Set<String>> descriptors = new ArrayList<>();

    @Override
    public void writeDexFile(byte[] contents, Set<String> classDescriptors, int fileId) {
      throw new Unreachable();
    }

    @Override
    public synchronized void writeDexFile(byte[] contents, Set<String> classDescriptors,
        String primaryClassName) {
      descriptors.add(classDescriptors);
    }

    public List<Set<String>> getDescriptors() {
      return descriptors;
    }
  }
}
