// Host verification CLI: compares this implementation with pyopenjtalk.
//
//   ja_g2p_tool <dic_dir> phonemes "こんにちは"
//   ja_g2p_tool <dic_dir> kana "こんにちは"
//   ja_g2p_tool <dic_dir> batch phonemes < lines.txt   # one text per line
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <string>

#include "ja_g2p.h"

int main(int argc, char **argv) {
    if (argc < 4) {
        std::fprintf(stderr,
                     "usage: %s <dic_dir> <phonemes|kana|tokens|batch-phonemes|batch-kana> <text>\n",
                     argv[0]);
        return 2;
    }
    const char *dic = argv[1];
    const std::string mode = argv[2];
    void *engine = ja_g2p_open(dic);
    if (!engine) {
        std::fprintf(stderr, "cannot open dictionary: %s\n", dic);
        return 1;
    }

    auto emit = [&](const std::string &text) {
        char *value = nullptr;
        if (mode.find("kana") != std::string::npos) {
            value = ja_g2p_kana(engine, text.c_str());
        } else if (mode.find("tokens") != std::string::npos) {
            value = ja_g2p_tokens(engine, text.c_str());
        } else {
            value = ja_g2p_phonemes(engine, text.c_str());
        }
        std::printf("%s\n", value ? value : "");
        ja_g2p_free(value);
    };

    if (mode.rfind("batch", 0) == 0) {
        std::string line;
        while (std::getline(std::cin, line)) {
            if (!line.empty() && line.back() == '\r') line.pop_back();
            emit(line);
        }
    } else {
        emit(argv[3]);
    }
    ja_g2p_close(engine);
    return 0;
}
