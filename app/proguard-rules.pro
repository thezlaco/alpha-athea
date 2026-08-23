# JNI resolves native methods by class/method name; keep them stable.
-keepclasseswithmembernames class com.athea.app.engine.PtyBridge {
    native <methods>;
}
