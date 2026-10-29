cmake_minimum_required(VERSION 3.16)
project(melox_ja_g2p CXX C)

# MeCab (vendored) still uses the `register` keyword, removed in C++17.
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# OpenJTalk G2P frontend. Vendored from r9y9/open_jtalk v1.11.1 (Modified BSD,
# see COPYING.open_jtalk / COPYING.mecab). No HTS engine and no HTS voice: only
# text2mecab → MeCab + NAIST dictionary → NJD sets → JPCommon labels.
set(OPEN_JTALK_SRC "${CMAKE_CURRENT_SOURCE_DIR}/third_party/open_jtalk")

# The checked-in config.h selects Windows or POSIX headers without writing to
# the source tree, so Windows and Linux builds cannot race over it.

foreach(dir jpcommon mecab/src mecab2njd njd njd2jpcommon
        njd_set_accent_phrase njd_set_accent_type njd_set_digit
        njd_set_long_vowel njd_set_pronunciation njd_set_unvoiced_vowel text2mecab)
    aux_source_directory("${OPEN_JTALK_SRC}/${dir}" MELOX_SOURCES)
    include_directories("${OPEN_JTALK_SRC}/${dir}")
endforeach()

add_definitions(-DHAVE_CONFIG_H
                -DDIC_VERSION=102
                -DMECAB_DEFAULT_RC="dummy"
                -DMECAB_WITHOUT_SHARE_DIC
                -DPACKAGE="open_jtalk"
                -DVERSION="1.11"
                -DCHARSET_UTF_8
                -DMECAB_CHARSET=utf-8
                -DMECAB_UTF8_USE_ONLY)

add_library(melox_ja_g2p SHARED ja_g2p.cpp ${MELOX_SOURCES})
if(WIN32)
    target_sources(melox_ja_g2p PRIVATE exports.def)
    if(MINGW)
        target_link_options(melox_ja_g2p PRIVATE -static -Wl,--exclude-all-symbols)
    endif()
endif()
target_include_directories(melox_ja_g2p PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")

add_executable(ja_g2p_tool host_main.cpp)
target_link_libraries(ja_g2p_tool melox_ja_g2p)
if(MINGW)
    target_link_options(ja_g2p_tool PRIVATE -static)
endif()
