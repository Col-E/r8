// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Joiner;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class is used to run tests on devices (or emulators)
 * using ddmlib.
 */
public class DeviceRunner {

  private static final boolean VERBOSE = false;
  private static final long ADB_CONNECTION_TIMEOUT = 5000;
  private static final long ADB_WAIT_STEP = ADB_CONNECTION_TIMEOUT / 10;
  private static final String TEST_SCRIPT_NAME = "test-exit-status.sh";
  private static final File TEST_SCRIPT_FILE =
      new File(System.getProperty("user.dir"), "scripts/" + TEST_SCRIPT_NAME);
  private static final char PATH_SEPARATOR_CHAR = ':';
  private static final String RUNTIME_NAME = "ART";

  private List<String> vmOptions = Collections.emptyList();
  private Map<String, String> systemProperties = Collections.emptyMap();
  private List<File> classpath = Collections.emptyList();
  private List<File> bootClasspath = Collections.emptyList();
  private String mainClass;
  private List<String> programArguments = Collections.emptyList();
  private OutputStream outRedirectStream = new ByteArrayOutputStream();

  private ShellOutputReceiver hostOutput = new ShellOutputReceiver();

  public static class DeviceRunnerConfigurationException extends Exception {

    public DeviceRunnerConfigurationException(String message) {
      super(message);
    }
  }

  private static class ShellOutputReceiver implements IShellOutputReceiver {

    private final PrintStream out;

    public ShellOutputReceiver() {
      this.out = System.out;
    }

    public ShellOutputReceiver(OutputStream out) {
      this.out = new PrintStream(out);
    }

    @Override
    public void addOutput(byte[] data, int offset, int length) {
      out.print(new String(Arrays.copyOfRange(data, offset, offset + length)));
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean isCancelled() {
      return false;
    }
  }

  private static class ShellOutputToStringReceiver implements IShellOutputReceiver {

    StringBuffer outBuffer = new StringBuffer();

    @Override
    public void addOutput(byte[] data, int offset, int length) {
      outBuffer.append(new String(Arrays.copyOfRange(data, offset, offset + length)));
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    public String getOutput() {
      return outBuffer.toString();
    }
  }

  public DeviceRunner() {
    try {
      AndroidDebugBridge.init(/* clientSupport */ false);
    } catch (IllegalStateException ex) {
      // ADB was already initialized, we're fine, so just ignore.
    }
  }

  public DeviceRunner setVmOptions(List<String> vmOptions) {
    this.vmOptions = vmOptions;
    return this;
  }

  public DeviceRunner setSystemProperties(Map<String, String> systemProperties) {
    this.systemProperties = systemProperties;
    return this;
  }

  public DeviceRunner setClasspath(List<File> classpath) {
    this.classpath = classpath;
    return this;
  }

  public DeviceRunner setBootClasspath(List<File> bootClasspath) {
    this.bootClasspath = bootClasspath;
    return this;
  }

  public DeviceRunner setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  public DeviceRunner setProgramArguments(List<String> programArguments) {
    this.programArguments = programArguments;
    return this;
  }

  public DeviceRunner setOutputStream(OutputStream outputStream) {
    outRedirectStream = outputStream;
    return this;
  }

  public ToolHelper.ProcessResult run() throws DeviceRunnerConfigurationException, IOException {

    AndroidDebugBridge adb = initializeAdb();

    IDevice[] connectedDevices = adb.getDevices();
    if (connectedDevices.length == 0) {
      throw new DeviceRunnerConfigurationException("No device found");
    }

    if (connectedDevices.length != 1) {
      throw new DeviceRunnerConfigurationException(
          "Running tests on more than one device is not yet supported. "
              + "Currently connected devices: ["
              + StringUtils.join(Arrays.asList(connectedDevices), ",") + "]");
    }

    int exitStatus = -1;
    String uuid = java.util.UUID.randomUUID().toString();
    IDevice device = connectedDevices[0];
    try {
      checkDeviceRuntime(device);

      log("Running on device: " + device.getName());

      ensureAdbRoot(device);

      // Remove trailing '\n' returned by emulator
      File testsRootDirFile = new File(device.getMountPoint(IDevice.MNT_DATA).replace("\n", ""),
          "r8-tests-" + uuid);
      String testsRootDir = convertToTargetPath(testsRootDirFile);

      String testScriptPathOnTarget = convertToTargetPath(
          new File(testsRootDirFile, TEST_SCRIPT_NAME));

      ClasspathPair destFilePaths = installTestFiles(device, testsRootDirFile,
          testScriptPathOnTarget);

      File rootDir = new File(device.getMountPoint(IDevice.MNT_ROOT).replace("\n", ""));

      // Bug : exit code return by adb shell is wrong (always 0)
      // https://code.google.com/p/android/issues/detail?id=3254
      // Use go team hack to work this around
      // https://code.google.com/p/go/source/browse/misc/arm/a
      executeShellCommand(testScriptPathOnTarget + ' ' + uuid + ' ' + Joiner.on(' ').join(
          buildCommandLine(rootDir, testsRootDir, destFilePaths.bootclasspath,
              destFilePaths.classpath)), device, new ShellOutputReceiver(outRedirectStream),
          /* maxTimeToOutputResponse = */10000);
      exitStatus = getExitStatus(device, testsRootDir);
      log("Exit status: " + exitStatus);

      if (exitStatus != 0) {
        System.err.println("Execution failed on device '" + device.getName() + "'");
      }

    } catch (IOException t) {
      System.err.println("Error with device '" + device.getName() + "': " + t.getMessage());
      t.printStackTrace();
      throw t;
    } finally {
      deleteTestFiles(device, uuid);
    }

    // Convert target line separator to whatever host is for output comparison-based tests
    String outputAsString = outRedirectStream.toString()
        .replace("\n", ToolHelper.LINE_SEPARATOR);

    return new ToolHelper.ProcessResult(exitStatus, outputAsString, "");
  }

  private AndroidDebugBridge initializeAdb() throws DeviceRunnerConfigurationException {
    AndroidDebugBridge adb = AndroidDebugBridge.createBridge(getAdbLocation(), false);

    long start = System.currentTimeMillis();

    log("Initializing adb...");

    while (!isAdbInitialized(adb)) {
      long timeLeft = start + ADB_CONNECTION_TIMEOUT - System.currentTimeMillis();
      if (timeLeft <= 0) {
        break;
      }
      try {
        Thread.sleep(ADB_WAIT_STEP);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }

    if (!isAdbInitialized(adb)) {
      String userDefinedPathToSdk = System.getProperty("ANDROID_SDK_HOME");
      if (userDefinedPathToSdk != null) {
        throw new DeviceRunnerConfigurationException(
            "Adb not found. Check SDK location '"
                + userDefinedPathToSdk
                + "'");
      } else {
        throw new DeviceRunnerConfigurationException(
            "Adb not found. Set either PATH or ANDROID_SDK_HOME environment variable");
      }
    }

    log("Done");
    return adb;
  }

  private static class ClasspathPair {
    public String[] bootclasspath;
    public String[] classpath;

    ClasspathPair(String[] bootclasspath, String[] classpath) {
      this.bootclasspath = bootclasspath;
      this.classpath = classpath;
    }
  }

  /**
   * @return path of classpath and bootclasspath files on device
   */
  private ClasspathPair installTestFiles(IDevice device, File testsRootDirFile,
      String testScriptPathOnTarget) throws IOException {

    String testsRootDir = convertToTargetPath(testsRootDirFile);

    String[] desFilePaths = new String[classpath.size()];
    String[] destFileBootCpPaths = new String[bootClasspath.size()];

    executeShellCommand("mkdir " + testsRootDir, device);
    executeShellCommand(
        "rm " + testsRootDir + FileListingService.FILE_SEPARATOR + "*", device);
    executePushCommand(TEST_SCRIPT_FILE.getAbsolutePath(), testScriptPathOnTarget, device);
    executeShellCommand("chmod 777 " + testScriptPathOnTarget, device);

    int i = 0;
    for (File f : bootClasspath) {
      destFileBootCpPaths[i] = convertToTargetPath(
          new File(testsRootDirFile, "f" + i + "_" + f.getName()));
      executePushCommand(f.getAbsolutePath(), destFileBootCpPaths[i], device);
      i++;
    }
    i = 0;
    for (File f : classpath) {
      desFilePaths[i] = convertToTargetPath(
          new File(testsRootDirFile, "f" + i + "_" + f.getName()));
      executePushCommand(f.getAbsolutePath(), desFilePaths[i], device);
      i++;
    }
    return new ClasspathPair(destFileBootCpPaths, desFilePaths);
  }

  private int getExitStatus(IDevice device, String testsRootDir) throws IOException {
    File exitStatusFile = createTempFile("exitStatus", "");
    executePullCommand(
        testsRootDir + "/exitStatus", exitStatusFile.getAbsolutePath(), device);

    try (BufferedReader br = new BufferedReader(new FileReader(exitStatusFile))) {
      String readLine = br.readLine();
      if (readLine == null) {
        throw new FileNotFoundException("Exit status not found at " + exitStatusFile.getPath());
      }
      return Integer.parseInt(readLine);
    }
  }

  @SuppressWarnings("deprecation")
  private void executeShellCommand(
      String command,
      IDevice device,
      ShellOutputReceiver hostOutput,
      int maxTimeToOutputResponse) throws IOException {
    log("adb -s " + device.getSerialNumber() + " shell " + command);
    try {
      if (maxTimeToOutputResponse != -1) {
        device.executeShellCommand(command, hostOutput, maxTimeToOutputResponse);
      } else {
        device.executeShellCommand(command, hostOutput);
      }
    } catch (IOException
        | ShellCommandUnresponsiveException
        | TimeoutException
        | AdbCommandRejectedException e) {
      throw new IOException(
          "Failed to execute shell command: '" + command + "'", e);
    }
  }

  private void executeShellCommand(String command, IDevice device)
      throws IOException {
    executeShellCommand(command, device, hostOutput, -1);
  }

  private void executePushCommand(
      String srcFile, String destFile, IDevice device) throws IOException {
    log("adb -s " + device.getSerialNumber() + " push " + srcFile + " " + destFile);
    try {
      device.pushFile(srcFile, destFile);
    } catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
      throw new IOException(
          "Unable to push file '" + srcFile + "' on device into '" + destFile + "'", e);
    }
  }

  private void executePullCommand(
      String srcFile, String destFile, IDevice device) throws IOException {
    log("adb -s " + device.getSerialNumber() + " pull " + srcFile + " " + destFile);
    try {
      device.pullFile(srcFile, destFile);
    } catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
      throw new IOException(
          "Unable to pull file '" + srcFile + "' from device into '" + destFile + "'", e);
    }
  }

  private boolean isAdbInitialized(AndroidDebugBridge adb) {
    return adb.isConnected() && adb.hasInitialDeviceList();
  }

  private String getAdbLocation() {
    String adbLocation = "adb";
    String userSpecifiedSdkLocation = System.getenv("ANDROID_SDK_HOME");
    if (userSpecifiedSdkLocation != null) {
      adbLocation =
          userSpecifiedSdkLocation
              + File.separatorChar
              + "platform-tools"
              + File.separatorChar
              + "adb";
    }
    return adbLocation;
  }


  private void ensureAdbRoot(IDevice device) throws IOException {
    boolean isRoot;
    try {
      isRoot = device.isRoot();
    } catch (TimeoutException
        | AdbCommandRejectedException
        | IOException
        | ShellCommandUnresponsiveException e) {
      throw new IOException(
          "Cannot fetch root status for device '"
              + device.getName()
              + "("
              + device.getSerialNumber()
              + ")"
              + "': "
              + e.getMessage(),
          e);
    }

    int numberOfTries = 0;
    while (!isRoot && numberOfTries < 5) {
      try {
        isRoot = device.root();
      } catch (TimeoutException
          | AdbCommandRejectedException
          | IOException
          | ShellCommandUnresponsiveException e1) {
        // root() seems to throw an IOException: EOF, and it tends
        // to make the subsequent call to isRoot() fail with
        // AdbCommandRejectedException: device offline, until adbd is
        // restarted as root.
      } finally {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e1) {
          Thread.currentThread().interrupt();
        }
      }
      numberOfTries++;
    }

    if (!isRoot) {
      throw new IOException(
          "Cannot switch to root on device '"
              + device.getName()
              + "("
              + device.getSerialNumber()
              + ")"
              + "'");
    }
  }

  private void checkDeviceRuntime(IDevice device)
      throws DeviceRunnerConfigurationException, IOException {
    ShellOutputToStringReceiver outputToString = new ShellOutputToStringReceiver();
    try {
      device.executeShellCommand("dalvikvm -showversion", outputToString);
      if (!outputToString.getOutput().contains(RUNTIME_NAME)) {
        throw new DeviceRunnerConfigurationException(
            "The plugged device does not run the required runtime: '" + RUNTIME_NAME + "'");
      }
    } catch (TimeoutException
        | AdbCommandRejectedException
        | ShellCommandUnresponsiveException
        | IOException e) {
      throw new IOException("Could not check device runtime", e);
    }
  }

  private String convertToTargetPath(File file) {
    String path = file.getPath();
    Path root = file.toPath().getRoot();
    if (root != null) {
      path = path.replace(root.toString(), FileListingService.FILE_SEPARATOR);
    }
    return path.replace(File.separator, FileListingService.FILE_SEPARATOR);
  }

  private static File createTempFile(String prefix, String suffix) throws IOException {
    File tmp = File.createTempFile("r8-tests-" + prefix, suffix);
    tmp.deleteOnExit();
    return tmp;
  }

  private void deleteTestFiles(IDevice device, String uuid) throws IOException {
    String deleteCommand = "find data -name '*r8-tests-" + uuid + "*' -exec rm -rf {} +";
    log("adb -s " + device.getSerialNumber() + " shell " + deleteCommand);
    try {
      device.executeShellCommand(deleteCommand, hostOutput);
    } catch (TimeoutException
        | AdbCommandRejectedException
        | ShellCommandUnresponsiveException
        | IOException e) {
      throw new IOException("Error while deleting test file on device", e);
    }
  }

  private List<String> buildCommandLine(File rootDir, String testRootDir,
      String[] bootClasspathFiles, String[] classpathFiles) {
    List<String> result = new ArrayList<>();

    result.add(convertToTargetPath(new File(rootDir, "/bin/dalvikvm")));

    for (String option : vmOptions) {
      result.add(option);
    }
    for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
      StringBuilder builder = new StringBuilder("-D");
      builder.append(entry.getKey());
      builder.append("=");
      builder.append(entry.getValue());
      result.add(builder.toString());
    }

    if (classpathFiles.length > 0) {
      result.add("-cp");
      result.add(Joiner.on(PATH_SEPARATOR_CHAR).join(
          Arrays.asList(classpathFiles).stream()
              .map(filePath -> convertToTargetPath(new File(filePath)))
              .collect(Collectors.toList())));
    }
    if (bootClasspathFiles.length > 0) {
      result.add("-Xbootclasspath:" + Joiner.on(PATH_SEPARATOR_CHAR).join(
          Arrays.asList(bootClasspathFiles).stream()
              .map(filePath -> convertToTargetPath(new File(filePath)))
              .collect(Collectors.toList())));
    }

    if (mainClass != null) {
      result.add(mainClass);
    }
    for (String argument : programArguments) {
      result.add(argument);
    }
    return result;
  }

  private void log(String message) {
    if (VERBOSE) {
      System.out.println(message);
    }
  }
}
