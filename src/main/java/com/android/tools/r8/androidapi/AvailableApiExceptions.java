// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import java.util.Set;

/**
 * Provides a mapping of API levels to exceptions introduced at that API level.
 *
 * <p>The mapping is only provided up to excluding the API level 21 (Android L) at which point the
 * verification issue for unresolved exception types was fixed.
 *
 * <p>See b/153514654 and b/131349148.
 *
 * <p>See GenerateAvailableApiExceptions.main in tests module for generating the buildMap function.
 */
public class AvailableApiExceptions {

  private final Set<DexType> exceptions;

  public AvailableApiExceptions(InternalOptions options) {
    assert options.minApiLevel < AndroidApiLevel.L.getLevel();
    exceptions = build(options.itemFactory, options.minApiLevel);
  }

  public boolean canCauseVerificationError(DexType type) {
    return !exceptions.contains(type);
  }

  /** The content of this method can be regenerated with GenerateAvailableExceptions.main. */
  public static Set<DexType> build(DexItemFactory factory, int minApiLevel) {
    Set<DexType> types = SetUtils.newIdentityHashSet(333);
    if (minApiLevel >= 1) {
      types.add(factory.createType("Landroid/app/PendingIntent$CanceledException;"));
      types.add(factory.createType("Landroid/content/ActivityNotFoundException;"));
      types.add(factory.createType("Landroid/content/IntentFilter$MalformedMimeTypeException;"));
      types.add(factory.createType("Landroid/content/ReceiverCallNotAllowedException;"));
      types.add(factory.createType("Landroid/content/pm/PackageManager$NameNotFoundException;"));
      types.add(factory.createType("Landroid/content/res/Resources$NotFoundException;"));
      types.add(factory.createType("Landroid/database/CursorIndexOutOfBoundsException;"));
      types.add(factory.createType("Landroid/database/SQLException;"));
      types.add(factory.createType("Landroid/database/StaleDataException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteAbortException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteConstraintException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteDatabaseCorruptException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteDiskIOException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteDoneException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteFullException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteMisuseException;"));
      types.add(factory.createType("Landroid/net/ParseException;"));
      types.add(factory.createType("Landroid/opengl/GLException;"));
      types.add(factory.createType("Landroid/os/BadParcelableException;"));
      types.add(factory.createType("Landroid/os/DeadObjectException;"));
      types.add(factory.createType("Landroid/os/ParcelFormatException;"));
      types.add(factory.createType("Landroid/os/RemoteException;"));
      types.add(factory.createType("Landroid/provider/Settings$SettingNotFoundException;"));
      types.add(factory.createType("Landroid/test/AssertionFailedError;"));
      types.add(factory.createType("Landroid/test/ComparisonFailure;"));
      types.add(factory.createType("Landroid/util/AndroidException;"));
      types.add(factory.createType("Landroid/util/AndroidRuntimeException;"));
      types.add(factory.createType("Landroid/util/TimeFormatException;"));
      types.add(factory.createType("Landroid/view/InflateException;"));
      types.add(factory.createType("Landroid/view/Surface$OutOfResourcesException;"));
      types.add(factory.createType("Landroid/view/SurfaceHolder$BadSurfaceTypeException;"));
      types.add(factory.createType("Landroid/view/WindowManager$BadTokenException;"));
      types.add(factory.createType("Landroid/widget/RemoteViews$ActionException;"));
      types.add(factory.createType("Ljava/io/CharConversionException;"));
      types.add(factory.createType("Ljava/io/EOFException;"));
      types.add(factory.createType("Ljava/io/FileNotFoundException;"));
      types.add(factory.createType("Ljava/io/IOException;"));
      types.add(factory.createType("Ljava/io/InterruptedIOException;"));
      types.add(factory.createType("Ljava/io/InvalidClassException;"));
      types.add(factory.createType("Ljava/io/InvalidObjectException;"));
      types.add(factory.createType("Ljava/io/NotActiveException;"));
      types.add(factory.createType("Ljava/io/NotSerializableException;"));
      types.add(factory.createType("Ljava/io/ObjectStreamException;"));
      types.add(factory.createType("Ljava/io/OptionalDataException;"));
      types.add(factory.createType("Ljava/io/StreamCorruptedException;"));
      types.add(factory.createType("Ljava/io/SyncFailedException;"));
      types.add(factory.createType("Ljava/io/UTFDataFormatException;"));
      types.add(factory.createType("Ljava/io/UnsupportedEncodingException;"));
      types.add(factory.createType("Ljava/io/WriteAbortedException;"));
      types.add(factory.createType("Ljava/lang/AbstractMethodError;"));
      types.add(factory.createType("Ljava/lang/ArithmeticException;"));
      types.add(factory.createType("Ljava/lang/ArrayIndexOutOfBoundsException;"));
      types.add(factory.createType("Ljava/lang/ArrayStoreException;"));
      types.add(factory.createType("Ljava/lang/AssertionError;"));
      types.add(factory.createType("Ljava/lang/ClassCastException;"));
      types.add(factory.createType("Ljava/lang/ClassCircularityError;"));
      types.add(factory.createType("Ljava/lang/ClassFormatError;"));
      types.add(factory.createType("Ljava/lang/ClassNotFoundException;"));
      types.add(factory.createType("Ljava/lang/CloneNotSupportedException;"));
      types.add(factory.createType("Ljava/lang/EnumConstantNotPresentException;"));
      types.add(factory.createType("Ljava/lang/Error;"));
      types.add(factory.createType("Ljava/lang/Exception;"));
      types.add(factory.createType("Ljava/lang/ExceptionInInitializerError;"));
      types.add(factory.createType("Ljava/lang/IllegalAccessError;"));
      types.add(factory.createType("Ljava/lang/IllegalAccessException;"));
      types.add(factory.createType("Ljava/lang/IllegalArgumentException;"));
      types.add(factory.createType("Ljava/lang/IllegalMonitorStateException;"));
      types.add(factory.createType("Ljava/lang/IllegalStateException;"));
      types.add(factory.createType("Ljava/lang/IllegalThreadStateException;"));
      types.add(factory.createType("Ljava/lang/IncompatibleClassChangeError;"));
      types.add(factory.createType("Ljava/lang/IndexOutOfBoundsException;"));
      types.add(factory.createType("Ljava/lang/InstantiationError;"));
      types.add(factory.createType("Ljava/lang/InstantiationException;"));
      types.add(factory.createType("Ljava/lang/InternalError;"));
      types.add(factory.createType("Ljava/lang/InterruptedException;"));
      types.add(factory.createType("Ljava/lang/LinkageError;"));
      types.add(factory.createType("Ljava/lang/NegativeArraySizeException;"));
      types.add(factory.createType("Ljava/lang/NoClassDefFoundError;"));
      types.add(factory.createType("Ljava/lang/NoSuchFieldError;"));
      types.add(factory.createType("Ljava/lang/NoSuchFieldException;"));
      types.add(factory.createType("Ljava/lang/NoSuchMethodError;"));
      types.add(factory.createType("Ljava/lang/NoSuchMethodException;"));
      types.add(factory.createType("Ljava/lang/NullPointerException;"));
      types.add(factory.createType("Ljava/lang/NumberFormatException;"));
      types.add(factory.createType("Ljava/lang/OutOfMemoryError;"));
      types.add(factory.createType("Ljava/lang/RuntimeException;"));
      types.add(factory.createType("Ljava/lang/SecurityException;"));
      types.add(factory.createType("Ljava/lang/StackOverflowError;"));
      types.add(factory.createType("Ljava/lang/StringIndexOutOfBoundsException;"));
      types.add(factory.createType("Ljava/lang/ThreadDeath;"));
      types.add(factory.createType("Ljava/lang/Throwable;"));
      types.add(factory.createType("Ljava/lang/TypeNotPresentException;"));
      types.add(factory.createType("Ljava/lang/UnknownError;"));
      types.add(factory.createType("Ljava/lang/UnsatisfiedLinkError;"));
      types.add(factory.createType("Ljava/lang/UnsupportedClassVersionError;"));
      types.add(factory.createType("Ljava/lang/UnsupportedOperationException;"));
      types.add(factory.createType("Ljava/lang/VerifyError;"));
      types.add(factory.createType("Ljava/lang/VirtualMachineError;"));
      types.add(factory.createType("Ljava/lang/annotation/AnnotationFormatError;"));
      types.add(factory.createType("Ljava/lang/annotation/AnnotationTypeMismatchException;"));
      types.add(factory.createType("Ljava/lang/annotation/IncompleteAnnotationException;"));
      types.add(factory.createType("Ljava/lang/reflect/GenericSignatureFormatError;"));
      types.add(factory.createType("Ljava/lang/reflect/InvocationTargetException;"));
      types.add(factory.createType("Ljava/lang/reflect/MalformedParameterizedTypeException;"));
      types.add(factory.createType("Ljava/lang/reflect/UndeclaredThrowableException;"));
      types.add(factory.createType("Ljava/net/BindException;"));
      types.add(factory.createType("Ljava/net/ConnectException;"));
      types.add(factory.createType("Ljava/net/HttpRetryException;"));
      types.add(factory.createType("Ljava/net/MalformedURLException;"));
      types.add(factory.createType("Ljava/net/NoRouteToHostException;"));
      types.add(factory.createType("Ljava/net/PortUnreachableException;"));
      types.add(factory.createType("Ljava/net/ProtocolException;"));
      types.add(factory.createType("Ljava/net/SocketException;"));
      types.add(factory.createType("Ljava/net/SocketTimeoutException;"));
      types.add(factory.createType("Ljava/net/URISyntaxException;"));
      types.add(factory.createType("Ljava/net/UnknownHostException;"));
      types.add(factory.createType("Ljava/net/UnknownServiceException;"));
      types.add(factory.createType("Ljava/nio/BufferOverflowException;"));
      types.add(factory.createType("Ljava/nio/BufferUnderflowException;"));
      types.add(factory.createType("Ljava/nio/InvalidMarkException;"));
      types.add(factory.createType("Ljava/nio/ReadOnlyBufferException;"));
      types.add(factory.createType("Ljava/nio/channels/AlreadyConnectedException;"));
      types.add(factory.createType("Ljava/nio/channels/AsynchronousCloseException;"));
      types.add(factory.createType("Ljava/nio/channels/CancelledKeyException;"));
      types.add(factory.createType("Ljava/nio/channels/ClosedByInterruptException;"));
      types.add(factory.createType("Ljava/nio/channels/ClosedChannelException;"));
      types.add(factory.createType("Ljava/nio/channels/ClosedSelectorException;"));
      types.add(factory.createType("Ljava/nio/channels/ConnectionPendingException;"));
      types.add(factory.createType("Ljava/nio/channels/FileLockInterruptionException;"));
      types.add(factory.createType("Ljava/nio/channels/IllegalBlockingModeException;"));
      types.add(factory.createType("Ljava/nio/channels/IllegalSelectorException;"));
      types.add(factory.createType("Ljava/nio/channels/NoConnectionPendingException;"));
      types.add(factory.createType("Ljava/nio/channels/NonReadableChannelException;"));
      types.add(factory.createType("Ljava/nio/channels/NonWritableChannelException;"));
      types.add(factory.createType("Ljava/nio/channels/NotYetBoundException;"));
      types.add(factory.createType("Ljava/nio/channels/NotYetConnectedException;"));
      types.add(factory.createType("Ljava/nio/channels/OverlappingFileLockException;"));
      types.add(factory.createType("Ljava/nio/channels/UnresolvedAddressException;"));
      types.add(factory.createType("Ljava/nio/channels/UnsupportedAddressTypeException;"));
      types.add(factory.createType("Ljava/nio/charset/CharacterCodingException;"));
      types.add(factory.createType("Ljava/nio/charset/CoderMalfunctionError;"));
      types.add(factory.createType("Ljava/nio/charset/IllegalCharsetNameException;"));
      types.add(factory.createType("Ljava/nio/charset/MalformedInputException;"));
      types.add(factory.createType("Ljava/nio/charset/UnmappableCharacterException;"));
      types.add(factory.createType("Ljava/nio/charset/UnsupportedCharsetException;"));
      types.add(factory.createType("Ljava/security/AccessControlException;"));
      types.add(factory.createType("Ljava/security/DigestException;"));
      types.add(factory.createType("Ljava/security/GeneralSecurityException;"));
      types.add(factory.createType("Ljava/security/InvalidAlgorithmParameterException;"));
      types.add(factory.createType("Ljava/security/InvalidKeyException;"));
      types.add(factory.createType("Ljava/security/InvalidParameterException;"));
      types.add(factory.createType("Ljava/security/KeyException;"));
      types.add(factory.createType("Ljava/security/KeyManagementException;"));
      types.add(factory.createType("Ljava/security/KeyStoreException;"));
      types.add(factory.createType("Ljava/security/NoSuchAlgorithmException;"));
      types.add(factory.createType("Ljava/security/NoSuchProviderException;"));
      types.add(factory.createType("Ljava/security/PrivilegedActionException;"));
      types.add(factory.createType("Ljava/security/ProviderException;"));
      types.add(factory.createType("Ljava/security/SignatureException;"));
      types.add(factory.createType("Ljava/security/UnrecoverableEntryException;"));
      types.add(factory.createType("Ljava/security/UnrecoverableKeyException;"));
      types.add(factory.createType("Ljava/security/acl/AclNotFoundException;"));
      types.add(factory.createType("Ljava/security/acl/LastOwnerException;"));
      types.add(factory.createType("Ljava/security/acl/NotOwnerException;"));
      types.add(factory.createType("Ljava/security/cert/CRLException;"));
      types.add(factory.createType("Ljava/security/cert/CertPathBuilderException;"));
      types.add(factory.createType("Ljava/security/cert/CertPathValidatorException;"));
      types.add(factory.createType("Ljava/security/cert/CertStoreException;"));
      types.add(factory.createType("Ljava/security/cert/CertificateEncodingException;"));
      types.add(factory.createType("Ljava/security/cert/CertificateException;"));
      types.add(factory.createType("Ljava/security/cert/CertificateExpiredException;"));
      types.add(factory.createType("Ljava/security/cert/CertificateNotYetValidException;"));
      types.add(factory.createType("Ljava/security/cert/CertificateParsingException;"));
      types.add(factory.createType("Ljava/security/spec/InvalidKeySpecException;"));
      types.add(factory.createType("Ljava/security/spec/InvalidParameterSpecException;"));
      types.add(factory.createType("Ljava/sql/BatchUpdateException;"));
      types.add(factory.createType("Ljava/sql/DataTruncation;"));
      types.add(factory.createType("Ljava/sql/SQLException;"));
      types.add(factory.createType("Ljava/sql/SQLWarning;"));
      types.add(factory.createType("Ljava/text/ParseException;"));
      types.add(factory.createType("Ljava/util/ConcurrentModificationException;"));
      types.add(factory.createType("Ljava/util/DuplicateFormatFlagsException;"));
      types.add(factory.createType("Ljava/util/EmptyStackException;"));
      types.add(factory.createType("Ljava/util/FormatFlagsConversionMismatchException;"));
      types.add(factory.createType("Ljava/util/FormatterClosedException;"));
      types.add(factory.createType("Ljava/util/IllegalFormatCodePointException;"));
      types.add(factory.createType("Ljava/util/IllegalFormatConversionException;"));
      types.add(factory.createType("Ljava/util/IllegalFormatException;"));
      types.add(factory.createType("Ljava/util/IllegalFormatFlagsException;"));
      types.add(factory.createType("Ljava/util/IllegalFormatPrecisionException;"));
      types.add(factory.createType("Ljava/util/IllegalFormatWidthException;"));
      types.add(factory.createType("Ljava/util/InputMismatchException;"));
      types.add(factory.createType("Ljava/util/InvalidPropertiesFormatException;"));
      types.add(factory.createType("Ljava/util/MissingFormatArgumentException;"));
      types.add(factory.createType("Ljava/util/MissingFormatWidthException;"));
      types.add(factory.createType("Ljava/util/MissingResourceException;"));
      types.add(factory.createType("Ljava/util/NoSuchElementException;"));
      types.add(factory.createType("Ljava/util/TooManyListenersException;"));
      types.add(factory.createType("Ljava/util/UnknownFormatConversionException;"));
      types.add(factory.createType("Ljava/util/UnknownFormatFlagsException;"));
      types.add(factory.createType("Ljava/util/concurrent/BrokenBarrierException;"));
      types.add(factory.createType("Ljava/util/concurrent/CancellationException;"));
      types.add(factory.createType("Ljava/util/concurrent/ExecutionException;"));
      types.add(factory.createType("Ljava/util/concurrent/RejectedExecutionException;"));
      types.add(factory.createType("Ljava/util/concurrent/TimeoutException;"));
      types.add(factory.createType("Ljava/util/jar/JarException;"));
      types.add(factory.createType("Ljava/util/prefs/BackingStoreException;"));
      types.add(factory.createType("Ljava/util/prefs/InvalidPreferencesFormatException;"));
      types.add(factory.createType("Ljava/util/regex/PatternSyntaxException;"));
      types.add(factory.createType("Ljava/util/zip/DataFormatException;"));
      types.add(factory.createType("Ljava/util/zip/ZipException;"));
      types.add(factory.createType("Ljavax/crypto/BadPaddingException;"));
      types.add(factory.createType("Ljavax/crypto/ExemptionMechanismException;"));
      types.add(factory.createType("Ljavax/crypto/IllegalBlockSizeException;"));
      types.add(factory.createType("Ljavax/crypto/NoSuchPaddingException;"));
      types.add(factory.createType("Ljavax/crypto/ShortBufferException;"));
      types.add(factory.createType("Ljavax/net/ssl/SSLException;"));
      types.add(factory.createType("Ljavax/net/ssl/SSLHandshakeException;"));
      types.add(factory.createType("Ljavax/net/ssl/SSLKeyException;"));
      types.add(factory.createType("Ljavax/net/ssl/SSLPeerUnverifiedException;"));
      types.add(factory.createType("Ljavax/net/ssl/SSLProtocolException;"));
      types.add(factory.createType("Ljavax/security/auth/DestroyFailedException;"));
      types.add(factory.createType("Ljavax/security/auth/callback/UnsupportedCallbackException;"));
      types.add(factory.createType("Ljavax/security/auth/login/LoginException;"));
      types.add(factory.createType("Ljavax/security/cert/CertificateEncodingException;"));
      types.add(factory.createType("Ljavax/security/cert/CertificateException;"));
      types.add(factory.createType("Ljavax/security/cert/CertificateExpiredException;"));
      types.add(factory.createType("Ljavax/security/cert/CertificateNotYetValidException;"));
      types.add(factory.createType("Ljavax/security/cert/CertificateParsingException;"));
      types.add(factory.createType("Ljavax/xml/parsers/FactoryConfigurationError;"));
      types.add(factory.createType("Ljavax/xml/parsers/ParserConfigurationException;"));
      types.add(factory.createType("Ljunit/framework/AssertionFailedError;"));
      types.add(factory.createType("Ljunit/framework/ComparisonFailure;"));
      types.add(factory.createType("Lorg/apache/http/ConnectionClosedException;"));
      types.add(factory.createType("Lorg/apache/http/HttpException;"));
      types.add(factory.createType("Lorg/apache/http/MalformedChunkCodingException;"));
      types.add(factory.createType("Lorg/apache/http/MethodNotSupportedException;"));
      types.add(factory.createType("Lorg/apache/http/NoHttpResponseException;"));
      types.add(factory.createType("Lorg/apache/http/ParseException;"));
      types.add(factory.createType("Lorg/apache/http/ProtocolException;"));
      types.add(factory.createType("Lorg/apache/http/UnsupportedHttpVersionException;"));
      types.add(factory.createType("Lorg/apache/http/auth/AuthenticationException;"));
      types.add(factory.createType("Lorg/apache/http/auth/InvalidCredentialsException;"));
      types.add(factory.createType("Lorg/apache/http/auth/MalformedChallengeException;"));
      types.add(factory.createType("Lorg/apache/http/client/CircularRedirectException;"));
      types.add(factory.createType("Lorg/apache/http/client/ClientProtocolException;"));
      types.add(factory.createType("Lorg/apache/http/client/HttpResponseException;"));
      types.add(factory.createType("Lorg/apache/http/client/NonRepeatableRequestException;"));
      types.add(factory.createType("Lorg/apache/http/client/RedirectException;"));
      types.add(factory.createType("Lorg/apache/http/conn/ConnectTimeoutException;"));
      types.add(factory.createType("Lorg/apache/http/conn/ConnectionPoolTimeoutException;"));
      types.add(factory.createType("Lorg/apache/http/conn/HttpHostConnectException;"));
      types.add(factory.createType("Lorg/apache/http/cookie/MalformedCookieException;"));
      types.add(factory.createType("Lorg/apache/http/impl/auth/NTLMEngineException;"));
      types.add(
          factory.createType("Lorg/apache/http/impl/auth/UnsupportedDigestAlgorithmException;"));
      types.add(factory.createType("Lorg/apache/http/impl/client/TunnelRefusedException;"));
      types.add(factory.createType("Lorg/apache/http/impl/cookie/DateParseException;"));
      types.add(factory.createType("Lorg/json/JSONException;"));
      types.add(factory.createType("Lorg/w3c/dom/DOMException;"));
      types.add(factory.createType("Lorg/xml/sax/SAXException;"));
      types.add(factory.createType("Lorg/xml/sax/SAXNotRecognizedException;"));
      types.add(factory.createType("Lorg/xml/sax/SAXNotSupportedException;"));
      types.add(factory.createType("Lorg/xml/sax/SAXParseException;"));
      types.add(factory.createType("Lorg/xmlpull/v1/XmlPullParserException;"));
    }
    if (minApiLevel >= 4) {
      types.add(factory.createType("Landroid/content/IntentSender$SendIntentException;"));
    }
    if (minApiLevel >= 5) {
      types.add(factory.createType("Landroid/accounts/AccountsException;"));
      types.add(factory.createType("Landroid/accounts/AuthenticatorException;"));
      types.add(factory.createType("Landroid/accounts/NetworkErrorException;"));
      types.add(factory.createType("Landroid/accounts/OperationCanceledException;"));
      types.add(factory.createType("Landroid/content/OperationApplicationException;"));
    }
    if (minApiLevel >= 8) {
      types.add(factory.createType("Ljavax/xml/datatype/DatatypeConfigurationException;"));
      types.add(factory.createType("Ljavax/xml/transform/TransformerConfigurationException;"));
      types.add(factory.createType("Ljavax/xml/transform/TransformerException;"));
      types.add(factory.createType("Ljavax/xml/transform/TransformerFactoryConfigurationError;"));
      types.add(factory.createType("Ljavax/xml/xpath/XPathException;"));
      types.add(factory.createType("Ljavax/xml/xpath/XPathExpressionException;"));
      types.add(factory.createType("Ljavax/xml/xpath/XPathFactoryConfigurationException;"));
      types.add(factory.createType("Ljavax/xml/xpath/XPathFunctionException;"));
      types.add(factory.createType("Lorg/w3c/dom/ls/LSException;"));
    }
    if (minApiLevel >= 9) {
      types.add(factory.createType("Landroid/net/sip/SipException;"));
      types.add(factory.createType("Landroid/nfc/FormatException;"));
      types.add(factory.createType("Ljava/io/IOError;"));
      types.add(factory.createType("Ljava/sql/SQLClientInfoException;"));
      types.add(factory.createType("Ljava/sql/SQLDataException;"));
      types.add(factory.createType("Ljava/sql/SQLFeatureNotSupportedException;"));
      types.add(factory.createType("Ljava/sql/SQLIntegrityConstraintViolationException;"));
      types.add(factory.createType("Ljava/sql/SQLInvalidAuthorizationSpecException;"));
      types.add(factory.createType("Ljava/sql/SQLNonTransientConnectionException;"));
      types.add(factory.createType("Ljava/sql/SQLNonTransientException;"));
      types.add(factory.createType("Ljava/sql/SQLRecoverableException;"));
      types.add(factory.createType("Ljava/sql/SQLSyntaxErrorException;"));
      types.add(factory.createType("Ljava/sql/SQLTimeoutException;"));
      types.add(factory.createType("Ljava/sql/SQLTransactionRollbackException;"));
      types.add(factory.createType("Ljava/sql/SQLTransientConnectionException;"));
      types.add(factory.createType("Ljava/sql/SQLTransientException;"));
      types.add(factory.createType("Ljava/util/ServiceConfigurationError;"));
      types.add(factory.createType("Ljava/util/zip/ZipError;"));
    }
    if (minApiLevel >= 10) {
      types.add(factory.createType("Landroid/nfc/TagLostException;"));
    }
    if (minApiLevel >= 11) {
      types.add(factory.createType("Landroid/app/Fragment$InstantiationException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteAccessPermException;"));
      types.add(
          factory.createType(
              "Landroid/database/sqlite/SQLiteBindOrColumnIndexOutOfRangeException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteBlobTooBigException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteCantOpenDatabaseException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteDatabaseLockedException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteDatatypeMismatchException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteOutOfMemoryException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteReadOnlyDatabaseException;"));
      types.add(factory.createType("Landroid/database/sqlite/SQLiteTableLockedException;"));
      types.add(factory.createType("Landroid/graphics/SurfaceTexture$OutOfResourcesException;"));
      types.add(factory.createType("Landroid/os/NetworkOnMainThreadException;"));
      types.add(factory.createType("Landroid/renderscript/RSDriverException;"));
      types.add(factory.createType("Landroid/renderscript/RSIllegalArgumentException;"));
      types.add(factory.createType("Landroid/renderscript/RSInvalidStateException;"));
      types.add(factory.createType("Landroid/renderscript/RSRuntimeException;"));
      types.add(factory.createType("Landroid/util/Base64DataException;"));
      types.add(factory.createType("Landroid/util/MalformedJsonException;"));
      types.add(factory.createType("Landroid/view/KeyCharacterMap$UnavailableException;"));
    }
    if (minApiLevel >= 14) {
      types.add(factory.createType("Landroid/security/KeyChainException;"));
      types.add(factory.createType("Landroid/util/NoSuchPropertyException;"));
    }
    if (minApiLevel >= 15) {
      types.add(factory.createType("Landroid/os/TransactionTooLargeException;"));
    }
    if (minApiLevel >= 16) {
      types.add(factory.createType("Landroid/media/MediaCodec$CryptoException;"));
      types.add(factory.createType("Landroid/media/MediaCryptoException;"));
      types.add(factory.createType("Landroid/os/OperationCanceledException;"));
    }
    if (minApiLevel >= 17) {
      types.add(factory.createType("Landroid/view/WindowManager$InvalidDisplayException;"));
    }
    if (minApiLevel >= 18) {
      types.add(factory.createType("Landroid/media/DeniedByServerException;"));
      types.add(factory.createType("Landroid/media/MediaDrmException;"));
      types.add(factory.createType("Landroid/media/NotProvisionedException;"));
      types.add(factory.createType("Landroid/media/UnsupportedSchemeException;"));
    }
    if (minApiLevel >= 19) {
      types.add(factory.createType("Landroid/media/ResourceBusyException;"));
      types.add(
          factory.createType("Landroid/os/ParcelFileDescriptor$FileDescriptorDetachedException;"));
      types.add(factory.createType("Ljava/lang/ReflectiveOperationException;"));
      types.add(factory.createType("Ljavax/crypto/AEADBadTagException;"));
    }
    return types;
  }
}
