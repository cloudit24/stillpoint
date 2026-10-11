// JNI around llama.cpp for Stillpoint's assistant. One model at a time; everything stays on the phone.
#include <jni.h>
#include <string>
#include <vector>
#include "llama.h"

namespace {

struct Llm {
    llama_model * model;
    llama_context * ctx;
    const llama_vocab * vocab;
    llama_sampler * smpl;
};

std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

void quiet(ggml_log_level, const char *, void *) {}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_cloudit24_stillpoint_llm_LlmNative_load(JNIEnv * env, jclass, jstring jpath, jint threads, jint nCtx) {
    static bool ready = false;
    if (!ready) { llama_backend_init(); llama_log_set(quiet, nullptr); ready = true; }

    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    const std::string path = jstr(env, jpath);
    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) return 0;

    auto cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nCtx;
    cp.n_batch = 512;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    cp.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); return 0; }

    auto sp = llama_sampler_chain_default_params();
    sp.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.6f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    return (jlong) new Llm{model, ctx, llama_model_get_vocab(model), smpl};
}

JNIEXPORT void JNICALL
Java_com_cloudit24_stillpoint_llm_LlmNative_free(JNIEnv *, jclass, jlong handle) {
    auto * llm = (Llm *) handle;
    if (!llm) return;
    llama_sampler_free(llm->smpl);
    llama_free(llm->ctx);
    llama_model_free(llm->model);
    delete llm;
}

/** Lays the conversation out the way this model was trained to read it (its own chat template). */
JNIEXPORT jstring JNICALL
Java_com_cloudit24_stillpoint_llm_LlmNative_prompt(JNIEnv * env, jclass, jlong handle, jobjectArray roles, jobjectArray texts) {
    auto * llm = (Llm *) handle;
    if (!llm) return env->NewStringUTF("");
    const int n = env->GetArrayLength(roles);
    std::vector<std::string> r(n), t(n);
    std::vector<llama_chat_message> msgs(n);
    for (int i = 0; i < n; i++) {
        r[i] = jstr(env, (jstring) env->GetObjectArrayElement(roles, i));
        t[i] = jstr(env, (jstring) env->GetObjectArrayElement(texts, i));
        msgs[i] = {r[i].c_str(), t[i].c_str()};
    }
    const char * tmpl = llama_model_chat_template(llm->model, nullptr);
    std::vector<char> buf(8192);
    int len = llama_chat_apply_template(tmpl, msgs.data(), n, true, buf.data(), (int32_t) buf.size());
    if (len > (int) buf.size()) {
        buf.resize(len + 1);
        len = llama_chat_apply_template(tmpl, msgs.data(), n, true, buf.data(), (int32_t) buf.size());
    }
    if (len < 0) len = 0;
    std::string out(buf.data(), len);
    return env->NewStringUTF(out.c_str());
}

/**
 * Generates up to [maxTokens], handing each piece of text to sink.onToken(bytes) as raw UTF-8 bytes
 * (a token can split a character). Stops when the model is done or the sink returns false.
 * Returns 0 when done, 1 when the prompt doesn't fit, 2 on a decode error.
 */
JNIEXPORT jint JNICALL
Java_com_cloudit24_stillpoint_llm_LlmNative_generate(JNIEnv * env, jclass, jlong handle, jstring jprompt, jint maxTokens, jobject sink) {
    auto * llm = (Llm *) handle;
    if (!llm) return 2;
    jclass cls = env->GetObjectClass(sink);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "([B)Z");

    const std::string prompt = jstr(env, jprompt);
    const int n_prompt = -llama_tokenize(llm->vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    if (n_prompt <= 0) return 2;
    if (n_prompt + maxTokens > (int) llama_n_ctx(llm->ctx)) return 1;
    std::vector<llama_token> tokens(n_prompt);
    llama_tokenize(llm->vocab, prompt.c_str(), (int32_t) prompt.size(), tokens.data(), n_prompt, true, true);

    llama_memory_clear(llama_get_memory(llm->ctx), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt);
    char piece[512];
    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(llm->ctx, batch) != 0) return 2;
        llama_token tok = llama_sampler_sample(llm->smpl, llm->ctx, -1);
        if (llama_vocab_is_eog(llm->vocab, tok)) break;
        const int len = llama_token_to_piece(llm->vocab, tok, piece, sizeof(piece), 0, true);
        if (len > 0) {
            jbyteArray arr = env->NewByteArray(len);
            env->SetByteArrayRegion(arr, 0, len, (const jbyte *) piece);
            const jboolean more = env->CallBooleanMethod(sink, onToken, arr);
            env->DeleteLocalRef(arr);
            if (env->ExceptionCheck() || !more) break;
        }
        batch = llama_batch_get_one(&tok, 1);
    }
    return 0;
}

}
