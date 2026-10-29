// Minimal OpenJTalk G2P frontend, matching pyopenjtalk's `run_frontend`/`g2p`.
//
// Built without the HTS engine: only the text frontend is needed (text2mecab →
// MeCab + NAIST dictionary → NJD sets → JPCommon labels), which is exactly what
// pyopenjtalk's `g2p` uses. No synthesis, no HTS voice.
#ifndef MELOX_JA_G2P_H
#define MELOX_JA_G2P_H

#ifdef __cplusplus
extern "C" {
#endif

/** Opens the dictionary directory; returns NULL when the dictionary is unusable. */
void *ja_g2p_open(const char *dic_dir);

void ja_g2p_close(void *handle);

/**
 * Phoneme sequence for `text`, space separated, matching
 * `pyopenjtalk.g2p(text)` (boundary sil/pau dropped by the caller's slicing).
 * Caller frees with ja_g2p_free. NULL on failure.
 */
char *ja_g2p_phonemes(void *handle, const char *text);

/** Katakana reading, matching `pyopenjtalk.g2p(text, kana=True)`. */
char *ja_g2p_kana(void *handle, const char *text);

/**
 * Frontend tokens as `surface\tpron` lines (UTF-8), matching
 * `pyopenjtalk.run_frontend(text)`; empty surfaces are skipped. Caller frees.
 */
char *ja_g2p_tokens(void *handle, const char *text);

void ja_g2p_free(char *value);

/** Dictionary directory version string, for diagnostics. */
const char *ja_g2p_version(void);

#ifdef __cplusplus
}
#endif

#endif  // MELOX_JA_G2P_H
