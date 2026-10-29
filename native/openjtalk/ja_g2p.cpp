#include "ja_g2p.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

// Order matters: the newer headers reference the NJD/JPCommon types.
#include "njd.h"
#include "jpcommon.h"
#include "mecab.h"
#include "text2mecab.h"
#include "mecab2njd.h"
#include "njd2jpcommon.h"

// The NJD set functions are plain C entry points compiled into the same library.
extern "C" {
#include "njd_set_pronunciation.h"
#include "njd_set_digit.h"
#include "njd_set_accent_phrase.h"
#include "njd_set_accent_type.h"
#include "njd_set_unvoiced_vowel.h"
#include "njd_set_long_vowel.h"
}

namespace {

struct Engine {
    Mecab mecab;
    NJD njd;
    JPCommon jp;
};

// Mirrors pyopenjtalk `__init__` + `run_frontend` exactly.
bool run_frontend(Engine *engine, const char *text) {
    char buff[8192];
    if (std::strlen(text) > 4096 || !text2mecab_bounded(buff, sizeof(buff), text)) return false;
    Mecab_analysis(&engine->mecab, buff);
    mecab2njd(&engine->njd, Mecab_get_feature(&engine->mecab), Mecab_get_size(&engine->mecab));
    njd_set_pronunciation(&engine->njd);
    njd_set_digit(&engine->njd);
    njd_set_accent_phrase(&engine->njd);
    njd_set_accent_type(&engine->njd);
    njd_set_unvoiced_vowel(&engine->njd);
    njd_set_long_vowel(&engine->njd);
    return true;
}

void refresh(Engine *engine) {
    NJD_refresh(&engine->njd);
    Mecab_refresh(&engine->mecab);
}

char *dup_string(const std::string &value) {
    char *out = static_cast<char *>(std::malloc(value.size() + 1));
    if (!out) return nullptr;
    std::memcpy(out, value.c_str(), value.size() + 1);
    return out;
}

// pyopenjtalk strips U+2019 (E2 80 99) from the kana reading.
std::string strip_apostrophe(const char *value) {
    std::string out;
    if (!value) return out;
    for (const char *p = value; *p;) {
        if (static_cast<unsigned char>(p[0]) == 0xE2 && static_cast<unsigned char>(p[1]) == 0x80 &&
            static_cast<unsigned char>(p[2]) == 0x99) {
            p += 3;
            continue;
        }
        out.push_back(*p++);
    }
    return out;
}

// pyopenjtalk: label.split('-')[1].split('+')[0]
std::string phoneme_of_label(const char *label) {
    if (!label) return std::string();
    const char *dash = std::strchr(label, '-');
    if (!dash) return std::string();
    const char *start = dash + 1;
    const char *plus = std::strchr(start, '+');
    if (!plus) return std::string(start);
    return std::string(start, static_cast<size_t>(plus - start));
}

}  // namespace

extern "C" void *ja_g2p_open(const char *dic_dir) {
    if (!dic_dir) return nullptr;
    Engine *engine = new (std::nothrow) Engine();
    if (!engine) return nullptr;
    std::memset(&engine->mecab, 0, sizeof(engine->mecab));
    std::memset(&engine->njd, 0, sizeof(engine->njd));
    std::memset(&engine->jp, 0, sizeof(engine->jp));
    Mecab_initialize(&engine->mecab);
    if (!Mecab_load(&engine->mecab, const_cast<char *>(dic_dir))) {
        delete engine;
        return nullptr;
    }
    NJD_initialize(&engine->njd);
    JPCommon_initialize(&engine->jp);
    return engine;
}

extern "C" void ja_g2p_close(void *handle) {
    Engine *engine = static_cast<Engine *>(handle);
    if (!engine) return;
    Mecab_clear(&engine->mecab);
    NJD_clear(&engine->njd);
    JPCommon_clear(&engine->jp);
    delete engine;
}

extern "C" char *ja_g2p_phonemes(void *handle, const char *text) {
    Engine *engine = static_cast<Engine *>(handle);
    if (!engine || !text) return nullptr;
    if (!run_frontend(engine, text)) return nullptr;
    njd2jpcommon(&engine->jp, &engine->njd);
    JPCommon_make_label(&engine->jp);
    const int size = JPCommon_get_label_size(&engine->jp);
    char **labels = JPCommon_get_label_feature(&engine->jp);
    std::string out;
    // pyopenjtalk: labels[1:-1] — the boundary sil/pau labels are dropped.
    for (int i = 1; i < size - 1; ++i) {
        std::string phoneme = phoneme_of_label(labels[i]);
        if (phoneme.empty()) continue;
        if (!out.empty()) out.push_back(' ');
        out += phoneme;
    }
    JPCommon_refresh(&engine->jp);
    refresh(engine);
    return dup_string(out);
}

extern "C" char *ja_g2p_kana(void *handle, const char *text) {
    Engine *engine = static_cast<Engine *>(handle);
    if (!engine || !text) return nullptr;
    if (!run_frontend(engine, text)) return nullptr;
    std::string out;
    for (NJDNode *node = engine->njd.head; node; node = node->next) {
        // pyopenjtalk: symbols report their surface, everything else its pron.
        const char *pos = node->pos;
        const char *value = (pos && std::strcmp(pos, "記号") == 0) ? node->string : node->pron;
        out += strip_apostrophe(value);
    }
    refresh(engine);
    return dup_string(out);
}

extern "C" char *ja_g2p_tokens(void *handle, const char *text) {
    Engine *engine = static_cast<Engine *>(handle);
    if (!engine || !text) return nullptr;
    if (!run_frontend(engine, text)) return nullptr;
    std::string out;
    for (NJDNode *node = engine->njd.head; node; node = node->next) {
        const char *surface = node->string;
        if (!surface || !*surface) continue;
        if (!out.empty()) out.push_back('\n');
        out += surface;
        out.push_back('\t');
        out += node->pron ? node->pron : "";
    }
    refresh(engine);
    return dup_string(out);
}

extern "C" void ja_g2p_free(char *value) { std::free(value); }

extern "C" const char *ja_g2p_version(void) { return "open_jtalk-1.11-dic-utf8-1.11"; }
