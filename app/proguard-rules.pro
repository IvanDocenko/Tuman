# GeckoView supplies consumer rules for JNI and its public interfaces.
# YAML configuration is parsed by Gecko using this reflection-backed bean.
-keep class org.mozilla.gecko.util.DebugConfig { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
