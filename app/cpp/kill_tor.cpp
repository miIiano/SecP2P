#include <jni.h>

#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <android/log.h>

#include <cstdio>
#include <cstring>

extern "C" {
bool suffixCheck(const char *str, const char *suffix) {
    size_t lenstr = strlen(str);
    size_t lensuffix = strlen(suffix);
    if (lensuffix > lenstr) return 0;
    return strncmp(str + lenstr - lensuffix, suffix, lensuffix) == 0;
}
JNIEXPORT void JNICALL
Java_md_miliano_secp2p_tor_Native_killTor(JNIEnv *env, jclass clazz) {
    DIR *d = opendir("/proc");
    dirent *de;
    while ((de = readdir(d)) != nullptr) {
        int pid = atol(de->d_name);
        if (pid <= 0) continue;
        char nmpath[1024];
        sprintf(nmpath, "/proc/%i/cmdline", pid);
        char name[1024] = {0};
        if (int namefd = open(nmpath, O_RDONLY)) {
            read(namefd, name, sizeof(name) - 1);
            close(namefd);
        }
        if (suffixCheck(name, "libTor.so")) {
            __android_log_print(ANDROID_LOG_INFO, "PROC", "FOUND %i %s\n", pid, name);
            if (kill(pid, SIGKILL) == 0) {
                __android_log_print(ANDROID_LOG_INFO, "PROC", "KILLED %i %s\n", pid, name);
            } else {
                __android_log_print(ANDROID_LOG_INFO, "PROC", "FAILED TO KILL %i %s\n", pid,
                                    name);
            }
        }
    }
    closedir(d);
}
}