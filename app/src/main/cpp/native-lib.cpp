#include <jni.h>
#include <node.h>

#include <atomic>
#include <cstdlib>
#include <string>
#include <unistd.h>
#include <vector>

namespace {
std::atomic_bool g_node_started{false};

bool apply_environment(JNIEnv* env, jobjectArray entries) {
    if (entries == nullptr) return true;

    const jsize count = env->GetArrayLength(entries);
    for (jsize index = 0; index < count; ++index) {
        auto entry = static_cast<jstring>(env->GetObjectArrayElement(entries, index));
        if (entry == nullptr) return false;

        const char* chars = env->GetStringUTFChars(entry, nullptr);
        if (chars == nullptr) {
            env->DeleteLocalRef(entry);
            return false;
        }

        const std::string value(chars);
        env->ReleaseStringUTFChars(entry, chars);
        env->DeleteLocalRef(entry);

        const auto separator = value.find('=');
        if (separator == std::string::npos || separator == 0) return false;

        const std::string key = value.substr(0, separator);
        const std::string content = value.substr(separator + 1);
        if (setenv(key.c_str(), content.c_str(), 1) != 0) return false;
    }
    return true;
}

bool read_arguments(JNIEnv* env, jobjectArray input, std::vector<std::string>& owned) {
    if (input == nullptr) return false;

    const jsize count = env->GetArrayLength(input);
    if (count < 2) return false;

    owned.reserve(static_cast<size_t>(count));
    for (jsize index = 0; index < count; ++index) {
        auto argument = static_cast<jstring>(env->GetObjectArrayElement(input, index));
        if (argument == nullptr) return false;

        const char* chars = env->GetStringUTFChars(argument, nullptr);
        if (chars == nullptr) {
            env->DeleteLocalRef(argument);
            return false;
        }

        owned.emplace_back(chars);
        env->ReleaseStringUTFChars(argument, chars);
        env->DeleteLocalRef(argument);
    }
    return true;
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_sevenx_ai_runtime_NativeNode_startNode(
    JNIEnv* env,
    jclass,
    jobjectArray arguments,
    jobjectArray environment,
    jstring working_directory
) {
    // node::Start() is one-shot. If callers attempt to start it twice in a
    // single process, reject the second call rather than risking undefined state.
    if (g_node_started.exchange(true)) return 70;

    if (!apply_environment(env, environment)) return 71;

    if (working_directory == nullptr) return 72;
    const char* directory = env->GetStringUTFChars(working_directory, nullptr);
    if (directory == nullptr) return 73;
    const int chdir_result = chdir(directory);
    env->ReleaseStringUTFChars(working_directory, directory);
    if (chdir_result != 0) return 74;

    std::vector<std::string> owned_arguments;
    if (!read_arguments(env, arguments, owned_arguments)) return 75;

    std::vector<char*> argv;
    argv.reserve(owned_arguments.size() + 1);
    for (auto& argument : owned_arguments) {
        argv.push_back(argument.data());
    }
    argv.push_back(nullptr);

    return static_cast<jint>(node::Start(
        static_cast<int>(owned_arguments.size()),
        argv.data()
    ));
}
