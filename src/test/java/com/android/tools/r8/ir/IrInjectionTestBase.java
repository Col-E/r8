// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir;

import com.android.tools.r8.R8;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.errors.DexOverflowException;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppOutputSink;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.antlr.runtime.RecognitionException;

public class IrInjectionTestBase extends SmaliTestBase {

  protected DexApplication buildApplication(SmaliBuilder builder, InternalOptions options) {
    try {
      return buildApplication(AndroidApp.fromDexProgramData(builder.compile()), options);
    } catch (IOException | RecognitionException | ExecutionException | DexOverflowException e) {
      throw new RuntimeException(e);
    }
  }

  protected DexApplication buildApplication(AndroidApp input, InternalOptions options) {
    try {
      options.itemFactory.resetSortedIndices();
      return new ApplicationReader(input, options, new Timing("IrInjectionTest")).read();
    } catch (IOException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected DexEncodedMethod getMethod(DexApplication application, MethodSignature signature) {
    return getMethod(application,
        signature.clazz, signature.returnType, signature.name, signature.parameterTypes);
  }

  protected DexEncodedMethod getMethod(
      DexApplication application,
      String className,
      String returnType,
      String methodName,
      List<String> parameters) {
    DexInspector inspector = new DexInspector(application);
    return getMethod(inspector, className, returnType, methodName, parameters);
  }

  public class TestApplication {

    public final DexApplication application;
    public final DexEncodedMethod method;
    public final IRCode code;
    public final List<IRCode> additionalCode;
    public final ValueNumberGenerator valueNumberGenerator;
    public final InternalOptions options;

    public TestApplication(
        DexApplication application,
        DexEncodedMethod method,
        IRCode code,
        ValueNumberGenerator valueNumberGenerator,
        InternalOptions options) {
      this(application, method, code, null, valueNumberGenerator, options);
    }

    public TestApplication(
        DexApplication application,
        DexEncodedMethod method,
        IRCode code,
        List<IRCode> additionalCode,
        ValueNumberGenerator valueNumberGenerator,
        InternalOptions options) {
      this.application = application;
      this.method = method;
      this.code = code;
      this.additionalCode = additionalCode;
      this.valueNumberGenerator = valueNumberGenerator;
      this.options = options;
    }

    public int countArgumentInstructions() {
      int count = 0;
      ListIterator<Instruction> iterator = code.blocks.get(0).listIterator();
      while (iterator.next().isArgument()) {
        count++;
      }
      return count;
    }

    public InstructionListIterator listIteratorAt(BasicBlock block, int index) {
      InstructionListIterator iterator = block.listIterator();
      for (int i = 0; i < index; i++) {
        iterator.next();
      }
      return iterator;
    }

    private AndroidApp writeDex(DexApplication application, InternalOptions options)
        throws DexOverflowException {
      try {
        AndroidAppOutputSink compatSink = new AndroidAppOutputSink();
        R8.writeApplication(
            Executors.newSingleThreadExecutor(),
            application,
            compatSink,
            null,
            NamingLens.getIdentityLens(),
            null,
            options,
            null);
        compatSink.close();
        return compatSink.build();
      } catch (ExecutionException | IOException e) {
        throw new RuntimeException(e);
      }
    }

    public String run() throws DexOverflowException, IOException {
      AppInfo appInfo = new AppInfo(application);
      IRConverter converter = new IRConverter(appInfo, options);
      converter.replaceCodeForTesting(method, code);
      AndroidApp app = writeDex(application, options);
      return runOnArtRaw(app, DEFAULT_MAIN_CLASS_NAME).stdout;
    }
  }

}
