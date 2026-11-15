package com.sevenx.ai.runtime;

/**
 * Thin JNI binding for the embedded Node runtime.
 *
 * The native start method is one-shot per Android process. The gateway service
 * therefore runs in a dedicated :gateway process and restarts by replacing that
 * process, never by calling node::Start() twice in the same process.
 */
public final class NativeNode {
    static {
        System.loadLibrary("sevenx-node");
    }

    private NativeNode() {
    }

    public static native int startNode(
        String[] arguments,
        String[] environment,
        String workingDirectory
    );
}
