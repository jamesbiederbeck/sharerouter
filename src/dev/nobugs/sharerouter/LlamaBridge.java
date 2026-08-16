package dev.nobugs.sharerouter;

/**
 * Thin wrapper around llama_jni.so (native llama.cpp built for arm64-v8a via
 * the NDK). One instance owns one loaded model/context/adapter triple.
 */
public class LlamaBridge implements AutoCloseable {

    static {
        System.loadLibrary("llama_jni");
    }

    private long handle;

    /** adapterPath may be null to run the base model with no LoRA applied. */
    public LlamaBridge(String modelPath, String adapterPath, int contextSize) {
        handle = nativeInit(modelPath, adapterPath, contextSize);
        if (handle == 0) {
            throw new IllegalStateException("Failed to load model: " + modelPath);
        }
    }

    public String generate(String prompt, int maxTokens) {
        if (handle == 0) {
            throw new IllegalStateException("LlamaBridge already closed");
        }
        return nativeGenerate(handle, prompt, maxTokens);
    }

    @Override
    public void close() {
        if (handle != 0) {
            nativeFree(handle);
            handle = 0;
        }
    }

    private static native long nativeInit(String modelPath, String adapterPath, int contextSize);
    private static native String nativeGenerate(long handle, String prompt, int maxTokens);
    private static native void nativeFree(long handle);
}
