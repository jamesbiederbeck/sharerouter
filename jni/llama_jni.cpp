// JNI bridge between com.example.sharerouter.LlamaBridge (Java) and llama.cpp.
// One llama_model/llama_context/llama_adapter_lora triple lives per JNI handle,
// stored as the jlong returned from init() and passed back into generate()/free().

#include <jni.h>
#include <string>
#include <vector>

#include "llama.h"

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
Java_com_example_sharerouter_LlamaBridge_nativeInit(
        JNIEnv *env, jclass, jstring jModelPath, jstring jAdapterPath, jint nCtx) {
    static bool backendInitialized = false;
    if (!backendInitialized) {
        llama_backend_init();
        backendInitialized = true;
    }

    std::string modelPath = jstringToStd(env, jModelPath);

    llama_model_params modelParams = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (model == nullptr) {
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(nCtx);
    llama_context *ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
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
            llama_free(ctx);
            llama_model_free(model);
            delete session;
            return 0;
        }
        session->adapter = adapter;

        llama_adapter_lora *adapters[] = {adapter};
        float scales[] = {1.0f};
        llama_set_adapters_lora(ctx, adapters, 1, scales);
    }

    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_sharerouter_LlamaBridge_nativeGenerate(
        JNIEnv *env, jclass, jlong handle, jstring jPrompt, jint maxTokens) {
    auto *session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) {
        return env->NewStringUTF("");
    }

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    std::string prompt = jstringToStd(env, jPrompt);

    // Tokenize.
    int32_t nPromptTokens = -llama_tokenize(vocab, prompt.c_str(),
            static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    std::vector<llama_token> promptTokens(nPromptTokens);
    if (llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                        promptTokens.data(), nPromptTokens, true, true) < 0) {
        return env->NewStringUTF("");
    }

    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(0));

    std::string result;

    llama_batch batch = llama_batch_get_one(promptTokens.data(),
                                             static_cast<int32_t>(promptTokens.size()));

    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(session->ctx, batch) != 0) {
            break;
        }

        llama_token newToken = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) {
            break;
        }

        char buf[256];
        int32_t len = llama_token_to_piece(vocab, newToken, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
        }

        batch = llama_batch_get_one(&newToken, 1);
    }

    llama_sampler_free(sampler);

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_sharerouter_LlamaBridge_nativeFree(JNIEnv *, jclass, jlong handle) {
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
