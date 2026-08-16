// JNI bridge between dev.nobugs.sharerouter.LlamaBridge (Java) and llama.cpp.
// One llama_model/llama_context/llama_adapter_lora triple lives per JNI handle,
// stored as the jlong returned from init() and passed back into generate()/free().

#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_adapter_lora *adapter = nullptr;
};

std::string jstringToStd(JNIEnv *env, jstring s) {
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(s, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_nobugs_sharerouter_LlamaBridge_nativeInit(
        JNIEnv *env, jclass, jstring jModelPath, jstring jAdapterPath, jint nCtx) {
    static bool backendInitialized = false;
    if (!backendInitialized) {
        llama_backend_init();
        backendInitialized = true;
    }

    std::string modelPath = jstringToStd(env, jModelPath);
    LOGI("nativeInit: loading model=%s n_ctx=%d", modelPath.c_str(), nCtx);

    llama_model_params modelParams = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (model == nullptr) {
        LOGE("nativeInit: llama_model_load_from_file failed for %s", modelPath.c_str());
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(nCtx);
    llama_context *ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
        LOGE("nativeInit: llama_init_from_model failed (n_ctx=%d)", nCtx);
        llama_model_free(model);
        return 0;
    }

    auto *session = new Session();
    session->model = model;
    session->ctx = ctx;

    if (jAdapterPath != nullptr) {
        std::string adapterPath = jstringToStd(env, jAdapterPath);
        llama_adapter_lora *adapter = llama_adapter_lora_init(model, adapterPath.c_str());
        if (adapter == nullptr) {
            LOGE("nativeInit: llama_adapter_lora_init failed for %s", adapterPath.c_str());
            llama_free(ctx);
            llama_model_free(model);
            delete session;
            return 0;
        }
        session->adapter = adapter;

        llama_adapter_lora *adapters[] = {adapter};
        float scales[] = {1.0f};
        llama_set_adapters_lora(ctx, adapters, 1, scales);
        LOGI("nativeInit: adapter loaded and applied: %s", adapterPath.c_str());
    }

    LOGI("nativeInit: session ready");
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_nobugs_sharerouter_LlamaBridge_nativeGenerate(
        JNIEnv *env, jclass, jlong handle, jstring jPrompt, jint maxTokens) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) {
        return env->NewStringUTF("");
    }

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::string prompt = jstringToStd(env, jPrompt);

    // Each PAW invocation is an independent single-shot transform, not a
    // continuing chat — reset the KV cache so token positions restart at 0.
    // Without this, position tracking (via llama_batch_get_one) keeps
    // accumulating across calls until it exceeds n_ctx and llama_decode
    // starts failing a few calls in.
    llama_memory_clear(llama_get_memory(session->ctx), true);

    // Tokenize.
    int32_t nPromptTokens = -llama_tokenize(vocab, prompt.c_str(),
            static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    std::vector<llama_token> promptTokens(nPromptTokens);
    if (llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                        promptTokens.data(), nPromptTokens, true, true) < 0) {
        LOGE("nativeGenerate: tokenize failed, prompt.size()=%zu", prompt.size());
        return env->NewStringUTF("");
    }
    LOGI("nativeGenerate: prompt.size()=%zu nPromptTokens=%d maxTokens=%d",
         prompt.size(), nPromptTokens, maxTokens);

    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(0));

    std::string result;

    llama_batch batch = llama_batch_get_one(promptTokens.data(),
                                             static_cast<int32_t>(promptTokens.size()));

    int generatedTokens = 0;
    for (int i = 0; i < maxTokens; i++) {
        int32_t decodeStatus = llama_decode(session->ctx, batch);
        if (decodeStatus != 0) {
            LOGE("nativeGenerate: llama_decode failed at step %d, status=%d", i, decodeStatus);
            break;
        }

        llama_token newToken = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) {
            LOGI("nativeGenerate: hit EOG at step %d", i);
            break;
        }

        char buf[256];
        int32_t len = llama_token_to_piece(vocab, newToken, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
        }
        generatedTokens++;

        batch = llama_batch_get_one(&newToken, 1);
    }

    llama_sampler_free(sampler);

    LOGI("nativeGenerate: generatedTokens=%d resultLen=%zu result=\"%s\"",
         generatedTokens, result.size(), result.c_str());

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_nobugs_sharerouter_LlamaBridge_nativeFree(JNIEnv *, jclass, jlong handle) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) {
        return;
    }
    if (session->adapter != nullptr) {
        llama_adapter_lora_free(session->adapter);
    }
    if (session->ctx != nullptr) {
        llama_free(session->ctx);
    }
    if (session->model != nullptr) {
        llama_model_free(session->model);
    }
    delete session;
}
