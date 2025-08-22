#include "LLMInference.h"
#include <jni.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_loadModel(JNIEnv* env, jobject thiz, jstring modelPath, jobject params) {
    jboolean    isCopy           = true;
    const char* modelPathCstr    = env->GetStringUTFChars(modelPath, &isCopy);
    auto*       llmInference     = new LLMInference();

    jclass inferenceParamsClass = env->FindClass("com/roblobsta/lobstachat/lm/LobstaChatLM$InferenceParams");

    InferenceParams cppParams;
    cppParams.minP = env->GetFloatField(params, env->GetFieldID(inferenceParamsClass, "minP", "F"));
    cppParams.temperature = env->GetFloatField(params, env->GetFieldID(inferenceParamsClass, "temperature", "F"));
    cppParams.storeChats = env->GetBooleanField(params, env->GetFieldID(inferenceParamsClass, "storeChats", "Z"));
    cppParams.contextSize = env->GetLongField(params, env->GetFieldID(inferenceParamsClass, "contextSize", "J"));
    jstring chatTemplateJava = (jstring)env->GetObjectField(params, env->GetFieldID(inferenceParamsClass, "chatTemplate", "Ljava/lang/String;"));
    cppParams.chatTemplate = env->GetStringUTFChars(chatTemplateJava, &isCopy);
    cppParams.nThreads = env->GetIntField(params, env->GetFieldID(inferenceParamsClass, "numThreads", "I"));
    cppParams.useMmap = env->GetBooleanField(params, env->GetFieldID(inferenceParamsClass, "useMmap", "Z"));
    cppParams.useMlock = env->GetBooleanField(params, env->GetFieldID(inferenceParamsClass, "useMlock", "Z"));
    cppParams.topP = env->GetFloatField(params, env->GetFieldID(inferenceParamsClass, "topP", "F"));
    cppParams.topK = env->GetIntField(params, env->GetFieldID(inferenceParamsClass, "topK", "I"));
    cppParams.xtcP = env->GetFloatField(params, env->GetFieldID(inferenceParamsClass, "xtcP", "F"));
    cppParams.xtcT = env->GetFloatField(params, env->GetFieldID(inferenceParamsClass, "xtcT", "F"));


    try {
        llmInference->loadModel(modelPathCstr, cppParams);
    } catch (std::runtime_error& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }

    env->ReleaseStringUTFChars(modelPath, modelPathCstr);
    env->ReleaseStringUTFChars(chatTemplateJava, cppParams.chatTemplate);
    return reinterpret_cast<jlong>(llmInference);
}

extern "C" JNIEXPORT void JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_addChatMessage(JNIEnv* env, jobject thiz, jlong modelPtr, jstring message,
                                                 jstring role) {
    jboolean    isCopy       = true;
    const char* messageCstr  = env->GetStringUTFChars(message, &isCopy);
    const char* roleCstr     = env->GetStringUTFChars(role, &isCopy);
    auto*       llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->addChatMessage(messageCstr, roleCstr);
    env->ReleaseStringUTFChars(message, messageCstr);
    env->ReleaseStringUTFChars(role, roleCstr);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_getResponseGenerationSpeed(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    return llmInference->getResponseGenerationTime();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_getContextSizeUsed(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    return llmInference->getContextSizeUsed();
}

extern "C" JNIEXPORT void JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_close(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    delete llmInference;
}

extern "C" JNIEXPORT void JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_startCompletion(JNIEnv* env, jobject thiz, jlong modelPtr, jstring prompt) {
    jboolean    isCopy       = true;
    const char* promptCstr   = env->GetStringUTFChars(prompt, &isCopy);
    auto*       llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->startCompletion(promptCstr);
    env->ReleaseStringUTFChars(prompt, promptCItr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_completionLoop(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    try {
        std::string response = llmInference->completionLoop();
        if (response.empty()) {
            return env->NewStringUTF("[EOG]");
        }
        return env->NewStringUTF(response.c_str());
    } catch (std::runtime_error& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_roblobsta_lobstachat_lm_LobstaChatLM_stopCompletion(JNIEnv* env, jobject thiz, jlong modelPtr) {
    auto* llmInference = reinterpret_cast<LLMInference*>(modelPtr);
    llmInference->stopCompletion();
}