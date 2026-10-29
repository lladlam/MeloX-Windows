/* Portable MeCab feature configuration for the MeloX OpenJTalk frontend. */
#define HAVE_CTYPE_H 1
#define HAVE_FCNTL_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_MEMORY_H 1
#define HAVE_SETJMP_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_GETENV 1
#define HAVE_SETJMP 1
#define HAVE_SQRT 1
#define HAVE_STRSTR 1
#define SIZEOF_CHAR 1
#define SIZEOF_INT 4
#define SIZEOF_LONG_LONG 8
#define SIZEOF_SHORT 2
#define SIZEOF_SIZE_T 8
#define MECAB_CHARSET utf-8

#if defined(_WIN32)
#define HAVE_WINDOWS_H 1
#define SIZEOF_LONG 4
#else
#define HAVE_DIRENT_H 1
#define HAVE_STRINGS_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_SYS_TIMES_H 1
#define HAVE_UNISTD_H 1
#define HAVE_GETPAGESIZE 1
#define HAVE_MMAP 1
#define HAVE_OPENDIR 1
#define SIZEOF_LONG 8
#endif
